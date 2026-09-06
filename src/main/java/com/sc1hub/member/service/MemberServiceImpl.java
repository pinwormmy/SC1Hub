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

@Service
@Slf4j
public class MemberServiceImpl implements MemberService {

    private static final String DEFAULT_MEMBER_SEARCH_TYPE = "id";
    private static final int MEMBER_DISPLAY_POST_LIMIT = 10;
    private static final int DEFAULT_PAGESET_LIMIT = 10;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 64;
    // BCrypt는 72바이트를 넘는 입력의 뒷부분을 무시한다(CVE-2025-22228). 새/변경 비밀번호를
    // UTF-8 72바이트 이내로 제한해 서로 다른 긴 비밀번호가 같은 것으로 처리되는 문제를 차단한다.
    private static final int MAX_PASSWORD_BYTES = 72;
    private static final int MAX_NICKNAME_LENGTH = 50;
    private static final int MAX_REALNAME_LENGTH = 30;
    private static final int MAX_EMAIL_LENGTH = 100;
    private static final int MAX_PHONE_LENGTH = 50;
    private static final int MIN_GRADE = 1;
    private static final int MAX_GRADE = 3;
    // 기존 회원 행은 평문 pw를 담고 있다. 로그인 성공 시 BCrypt로 제자리 승격되며,
    // 이 접두사로 저장 형식을 판별한다.
    private static final String BCRYPT_PREFIX = "$2";

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
    private final WriterNicknameGuard writerNicknameGuard;

    public MemberServiceImpl(MemberMapper memberMapper, PasswordEncoder passwordEncoder,
                             WriterNicknameGuard writerNicknameGuard) {
        this.memberMapper = memberMapper;
        this.passwordEncoder = passwordEncoder;
        this.writerNicknameGuard = writerNicknameGuard;
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
        validateProfileFields(memberDTO, true);
        ensureNicknameAvailable(null, memberDTO.getNickName());
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
        validateProfileFields(member, true);
        ensureNicknameAvailable(member.getId(), member.getNickName());
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
        validateProfileFields(memberDTO, true);
        validateGrade(memberDTO.getGrade());
        ensureNicknameAvailable(memberDTO.getId(), memberDTO.getNickName());
        memberMapper.submitModifyMemberByAdmin(memberDTO);
    }

    /**
     * 별명은 현재 회원 중 유일해야 하고, 다른 계정이 과거에 회원 게시글 작성에 쓴 별명은 새로 취득할 수
     * 없다. 게시글 관리 권한이 작성자 별명 문자열로 판정되는 동안, 해제된 별명을 통한 과거 글 관리 권한
     * 취득을 막는다. 같은 회원이 별명을 그대로 두는 수정은 검사하지 않는다.
     */
    private void ensureNicknameAvailable(String memberId, String nickName) {
        if (!StringUtils.hasText(nickName)) {
            return;
        }
        String requested = nickName.trim();
        if (memberId != null) {
            MemberDTO current = memberMapper.getMemberInfo(memberId);
            if (current != null && requested.equals(current.getNickName())) {
                return;
            }
        }
        String duplicateCount = memberMapper.isUniqueNickName(requested);
        if (duplicateCount != null && !"0".equals(duplicateCount.trim())) {
            throw new IllegalArgumentException("이미 사용 중인 별명입니다.");
        }
        if (writerNicknameGuard.hasAuthoredPosts(requested)) {
            throw new IllegalArgumentException("이전에 게시글 작성에 사용된 별명은 다시 등록할 수 없습니다.");
        }
    }

    @Override
    public void deleteMember(String id) {
        memberMapper.deleteMember(id);
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
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            // 한글 등 멀티바이트 문자가 많으면 글자 수는 64자 이내여도 72바이트를 넘을 수 있다.
            throw new IllegalArgumentException("비밀번호가 너무 깁니다. 더 짧게 설정해주세요.");
        }
    }

    /**
     * 회원정보 필드의 길이와 마크업 삽입을 서버측에서 검증한다. 출력단 이스케이프와 함께 저장형 XSS를
     * 이중으로 막는다({@code <}, {@code >}, 제어문자 금지).
     */
    private void validateProfileFields(MemberDTO member, boolean requireNickname) {
        if (member == null) {
            throw new IllegalArgumentException("회원 정보를 확인해주세요.");
        }
        String nickName = member.getNickName();
        if (requireNickname && !StringUtils.hasText(nickName)) {
            throw new IllegalArgumentException("별명을 입력해주세요.");
        }
        validateSafeText(nickName, "별명", MAX_NICKNAME_LENGTH);
        validateSafeText(member.getRealName(), "이름", MAX_REALNAME_LENGTH);
        validateSafeText(member.getEmail(), "이메일", MAX_EMAIL_LENGTH);
        validateSafeText(member.getPhone(), "연락처", MAX_PHONE_LENGTH);
    }

    private void validateSafeText(String value, String fieldLabel, int maxLength) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldLabel + "은(는) " + maxLength + "자 이내여야 합니다.");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '<' || c == '>') {
                throw new IllegalArgumentException(fieldLabel + "에 사용할 수 없는 문자가 포함되어 있습니다.");
            }
            if (Character.isISOControl(c) && c != '\t') {
                throw new IllegalArgumentException(fieldLabel + "에 사용할 수 없는 문자가 포함되어 있습니다.");
            }
        }
    }

    private void validateGrade(int grade) {
        if (grade < MIN_GRADE || grade > MAX_GRADE) {
            throw new IllegalArgumentException("회원 등급은 " + MIN_GRADE + "~" + MAX_GRADE + " 사이여야 합니다.");
        }
    }

}
