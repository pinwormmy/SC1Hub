package com.sc1hub.strategytip.service;

import com.sc1hub.common.dto.PageDTO;
import com.sc1hub.common.util.PageUtils;
import com.sc1hub.member.dto.MemberDTO;
import com.sc1hub.strategytip.dto.StrategyTipCategoryDTO;
import com.sc1hub.strategytip.dto.StrategyTipDTO;
import com.sc1hub.strategytip.mapper.StrategyTipMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class StrategyTipServiceImpl implements StrategyTipService {

    private static final String DEFAULT_SEARCH_TYPE = "content";
    private static final int DISPLAY_TIP_LIMIT = 20;
    private static final int PAGESET_LIMIT = 10;
    private static final int MAX_CONTENT_LENGTH = 160;
    private static final String ADMIN_ID = "admin";
    private static final ZoneId RECOMMENDATION_ZONE = ZoneId.of("Asia/Seoul");

    private final StrategyTipMapper strategyTipMapper;
    private final Clock clock;

    @Autowired
    public StrategyTipServiceImpl(StrategyTipMapper strategyTipMapper) {
        this(strategyTipMapper, Clock.system(RECOMMENDATION_ZONE));
    }

    StrategyTipServiceImpl(StrategyTipMapper strategyTipMapper, Clock clock) {
        this.strategyTipMapper = strategyTipMapper;
        this.clock = clock;
    }

    @Override
    public List<StrategyTipCategoryDTO> getCategories() {
        return strategyTipMapper.selectCategories();
    }

    @Override
    public List<StrategyTipDTO> getTips(PageDTO page, String category) {
        PageDTO normalizedPage = PageUtils.normalize(page, DEFAULT_SEARCH_TYPE);
        return strategyTipMapper.selectTips(normalizedPage, normalizeCategory(category));
    }

    @Override
    public PageDTO pageSetting(PageDTO page, String category) {
        PageDTO normalizedPage = PageUtils.normalize(page, DEFAULT_SEARCH_TYPE);
        int totalCount = strategyTipMapper.countTips(normalizedPage, normalizeCategory(category));
        return PageUtils.calculate(normalizedPage, totalCount, DISPLAY_TIP_LIMIT, PAGESET_LIMIT);
    }

    @Override
    public void addTip(StrategyTipDTO tip, MemberDTO member) {
        if (tip == null) {
            throw new IllegalArgumentException("한줄 공략 내용을 입력해주세요.");
        }
        tip.setCategory(requireValue(tip.getCategory(), "분류를 선택해주세요."));
        tip.setContent(validateContent(tip.getContent()));

        if (member == null) {
            tip.setWriter(requireValue(tip.getWriter(), "닉네임을 입력해주세요."));
            tip.setGuestPassword(requireValue(tip.getGuestPassword(), "비밀번호를 입력해주세요."));
            tip.setMemberId(null);
        } else {
            tip.setWriter(member.getNickName());
            tip.setMemberId(member.getId());
            tip.setGuestPassword(null);
        }
        strategyTipMapper.insertTip(tip);
    }

    @Override
    @Transactional
    public void deleteTip(int tipNum, String guestPassword, MemberDTO member) {
        StrategyTipDTO tip = strategyTipMapper.selectTip(tipNum);
        if (tip == null) {
            throw new IllegalArgumentException("존재하지 않는 한줄 공략입니다.");
        }
        if (!canDelete(tip, guestPassword, member)) {
            throw new IllegalArgumentException("삭제 권한을 확인해주세요.");
        }
        strategyTipMapper.deleteTip(tipNum);
    }

    @Override
    @Transactional
    public int recommend(int tipNum, MemberDTO member, String sessionId) {
        StrategyTipDTO existingTip = strategyTipMapper.selectTip(tipNum);
        if (existingTip == null) {
            throw new IllegalArgumentException("존재하지 않는 한줄 공략입니다.");
        }

        String userHash = hashRecommendationUser(member, sessionId);
        LocalDate today = LocalDate.now(clock);
        int inserted = strategyTipMapper.insertDailyRecommendation(tipNum, today, userHash);
        if (inserted == 0) {
            throw new IllegalArgumentException("이 한줄 공략은 오늘 이미 추천했습니다.");
        }

        int updated = strategyTipMapper.incrementRecommendCount(tipNum);
        if (updated == 0) {
            throw new IllegalArgumentException("존재하지 않는 한줄 공략입니다.");
        }
        StrategyTipDTO tip = strategyTipMapper.selectTip(tipNum);
        return tip == null ? 0 : tip.getRecommendCount();
    }

    private String hashRecommendationUser(MemberDTO member, String sessionId) {
        String memberId = member == null ? null : trimToNull(member.getId());
        String identity = memberId == null
                ? "session|" + requireValue(sessionId, "추천 사용자 정보를 확인할 수 없습니다.")
                : "member|" + memberId;

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hash.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return hash.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private boolean canDelete(StrategyTipDTO tip, String guestPassword, MemberDTO member) {
        if (member != null && ADMIN_ID.equals(member.getId())) {
            return true;
        }
        if (member != null && tip.getMemberId() != null) {
            return Objects.equals(tip.getMemberId(), member.getId());
        }
        return tip.getMemberId() == null && Objects.equals(tip.getGuestPassword(), trimToNull(guestPassword));
    }

    private String validateContent(String value) {
        String content = requireValue(value, "한줄 공략 내용을 입력해주세요.");
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("한줄 공략은 " + MAX_CONTENT_LENGTH + "자까지 입력할 수 있습니다.");
        }
        return content;
    }

    private String requireValue(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private String normalizeCategory(String category) {
        return trimToNull(category);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
