package com.sc1hub.member.service;

import com.sc1hub.common.dto.PageDTO;
import com.sc1hub.common.util.PageUtils;
import com.sc1hub.member.dto.MemberDTO;
import com.sc1hub.member.dto.VisitorsDTO;
import com.sc1hub.member.mapper.MemberMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class MemberServiceImpl implements MemberService {

    private static final String DEFAULT_MEMBER_SEARCH_TYPE = "id";
    private static final int MEMBER_DISPLAY_POST_LIMIT = 10;
    private static final int DEFAULT_PAGESET_LIMIT = 10;
    private static final int TEMP_PASSWORD_LENGTH = 12;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 64;
    // 기존 회원 행은 평문 pw를 담고 있다. 로그인 성공 시 BCrypt로 제자리 승격되며,
    // 이 접두사로 저장 형식을 판별한다.
    private static final String BCRYPT_PREFIX = "$2";

    private final MemberMapper memberMapper;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    public MemberServiceImpl(MemberMapper memberMapper, EmailService emailService,
                             PasswordEncoder passwordEncoder) {
        this.memberMapper = memberMapper;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String isUniqueId(String id) throws Exception {
        return memberMapper.isUniqueId(id);
    }

    @Override
    public String isUniqueNickName(String nickName) {
        return memberMapper.isUniqueNickName(nickName);
    }

    @Override
    public List<VisitorsDTO> getRecentVisitors() {
        return memberMapper.getRecentVisitors();
    }

    @Override
    public void submitSignUp(MemberDTO memberDTO) throws Exception {
        validateNewPassword(memberDTO.getPw());
        // 호출자는 같은 DTO로 곧바로 로그인을 시도하므로 원본은 건드리지 않는다.
        memberMapper.submitSignUp(copyWithHashedPassword(memberDTO));
    }

    @Override
    public MemberDTO checkLoginData(MemberDTO memberDTO) throws Exception {
        if (memberDTO == null || !StringUtils.hasText(memberDTO.getId())
                || !StringUtils.hasText(memberDTO.getPw())) {
            return null;
        }
        MemberDTO stored = memberMapper.getMemberInfo(memberDTO.getId());
        if (stored == null || !StringUtils.hasText(stored.getPw())) {
            return null;
        }

        String rawPassword = memberDTO.getPw();
        String storedPassword = stored.getPw();
        boolean authenticated;
        if (storedPassword.startsWith(BCRYPT_PREFIX)) {
            authenticated = passwordEncoder.matches(rawPassword, storedPassword);
        } else {
            authenticated = MessageDigest.isEqual(
                    storedPassword.getBytes(StandardCharsets.UTF_8),
                    rawPassword.getBytes(StandardCharsets.UTF_8));
            if (authenticated) {
                MemberDTO upgrade = new MemberDTO();
                upgrade.setId(stored.getId());
                upgrade.setPw(passwordEncoder.encode(rawPassword));
                memberMapper.updatePassword(upgrade);
                log.info("레거시 평문 비밀번호를 BCrypt로 승격했습니다. memberId={}", stored.getId());
            }
        }
        if (!authenticated) {
            return null;
        }
        // 세션에 올라가는 객체이므로 해시조차 밖으로 내보내지 않는다.
        stored.setPw(null);
        return stored;
    }

    @Override
    public void submitModifyMyInfo(MemberDTO member) throws Exception {
        validateNewPassword(member.getPw());
        // 호출자는 같은 DTO로 재로그인해 세션을 갱신하므로 원본은 건드리지 않는다.
        memberMapper.submitModifyMyInfo(copyWithHashedPassword(member));
    }

    @Override
    public String isUniqueEmail(String email) {
        return memberMapper.isUniqueEmail(email);
    }

    @Override
    public PageDTO pageSetting(PageDTO page) {
        page = PageUtils.normalize(page, DEFAULT_MEMBER_SEARCH_TYPE);
        return PageUtils.calculate(page, getTotalMemberCount(page), MEMBER_DISPLAY_POST_LIMIT, DEFAULT_PAGESET_LIMIT);
    }

    @Override
    public MemberDTO getMemberInfo(String id) {
        return memberMapper.getMemberInfo(id);
    }

    @Override
    public List<MemberDTO> getMemberList(PageDTO page) {
        return memberMapper.getMemberList(page);
    }

    @Override
    public int getTotalMemberCount(PageDTO page) {
        return memberMapper.getTotalMemberCount(page);
    }

    @Override
    public void submitModifyMemberByAdmin(MemberDTO memberDTO) {
        memberMapper.submitModifyMemberByAdmin(memberDTO);
    }

    public boolean findCredentials(String email) {
        MemberDTO member = memberMapper.findByEmail(email);
        if (member == null) {
            return false;
        }

        // 임시 비밀번호 생성
        issueTemporaryPassword(member);

        // 이메일로 아이디 및 임시 비밀번호 보내기
        try {
            emailService.sendSimpleMessage(email);
        } catch (Exception e) {
            log.error("이메일 전송오류", e);
        }

        return true;
    }

    @Override
    public String findIdByNameAndEmail(String userName, String email) {
        try {
            return memberMapper.getIdByNameAndEmail(userName, email);
        } catch (Exception e) {
            log.error("이름과 이메일로 아이디를 찾는 중 오류가 발생했습니다.", e);
            return null;
        }
    }

    @Override
    public String findPassword(String userId, String email) {
        MemberDTO member = memberMapper.findByUserIdAndEmail(userId, email);
        if (member == null) {
            return "입력하신 ID와 이메일로 등록된 회원을 찾을 수 없습니다.";
        }

        // 임시 비밀번호 생성
        String tempPassword = issueTemporaryPassword(member);

        // 이메일로 임시 비밀번호 전송
        try {
            emailService.sendNewPasswordMessage(email, tempPassword);
            return "success";
        } catch (Exception e) {
            log.error("임시 비밀번호 이메일 전송 중 오류 발생", e);
            return "비밀번호 찾기 중 문제가 발생했습니다. 다시 시도해 주세요.";
        }
    }

    @Override
    public void deleteMember(String id) {
        memberMapper.deleteMember(id);
    }

    private String issueTemporaryPassword(MemberDTO member) {
        String tempPassword = UUID.randomUUID().toString().replace("-", "").substring(0, TEMP_PASSWORD_LENGTH);
        MemberDTO update = new MemberDTO();
        update.setId(member.getId());
        update.setPw(passwordEncoder.encode(tempPassword));
        memberMapper.updatePassword(update);
        return tempPassword;
    }

    private MemberDTO copyWithHashedPassword(MemberDTO source) {
        MemberDTO copy = new MemberDTO();
        copy.setId(source.getId());
        copy.setPw(passwordEncoder.encode(source.getPw()));
        copy.setNickName(source.getNickName());
        copy.setRealName(source.getRealName());
        copy.setEmail(source.getEmail());
        copy.setPhone(source.getPhone());
        copy.setGrade(source.getGrade());
        copy.setRegDate(source.getRegDate());
        return copy;
    }

    private void validateNewPassword(String rawPassword) {
        if (!StringUtils.hasText(rawPassword)
                || rawPassword.length() < MIN_PASSWORD_LENGTH
                || rawPassword.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "비밀번호는 " + MIN_PASSWORD_LENGTH + "~" + MAX_PASSWORD_LENGTH + "자여야 합니다.");
        }
    }

}
