package com.sc1hub.member.service;

import com.sc1hub.member.dto.MemberDTO;
import com.sc1hub.member.mapper.MemberMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceImplTest {

    private static final String MEMBER_ID = "tester";
    private static final String RAW_PASSWORD = "correct-password";

    @Mock
    private MemberMapper memberMapper;

    @Mock
    private EmailService emailService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private MemberServiceImpl memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberServiceImpl(memberMapper, emailService, passwordEncoder);
    }

    private MemberDTO storedMember(String storedPw) {
        MemberDTO stored = new MemberDTO();
        stored.setId(MEMBER_ID);
        stored.setPw(storedPw);
        return stored;
    }

    private MemberDTO loginAttempt(String rawPw) {
        MemberDTO attempt = new MemberDTO();
        attempt.setId(MEMBER_ID);
        attempt.setPw(rawPw);
        return attempt;
    }

    @Test
    void bcryptMemberLogsInAndPasswordNeverLeavesTheService() throws Exception {
        when(memberMapper.getMemberInfo(MEMBER_ID))
                .thenReturn(storedMember(passwordEncoder.encode(RAW_PASSWORD)));

        MemberDTO result = memberService.checkLoginData(loginAttempt(RAW_PASSWORD));

        assertNotNull(result);
        assertNull(result.getPw(), "세션에 올라가는 DTO는 해시조차 담지 않아야 한다");
        verify(memberMapper, never()).updatePassword(any());
    }

    @Test
    void legacyPlaintextMemberLogsInAndIsUpgradedToBcryptInPlace() throws Exception {
        when(memberMapper.getMemberInfo(MEMBER_ID)).thenReturn(storedMember(RAW_PASSWORD));

        MemberDTO result = memberService.checkLoginData(loginAttempt(RAW_PASSWORD));

        assertNotNull(result);
        ArgumentCaptor<MemberDTO> captor = ArgumentCaptor.forClass(MemberDTO.class);
        verify(memberMapper).updatePassword(captor.capture());
        String upgraded = captor.getValue().getPw();
        assertTrue(upgraded.startsWith("$2"), "승격된 저장값은 BCrypt여야 한다");
        assertTrue(passwordEncoder.matches(RAW_PASSWORD, upgraded));
        assertNull(result.getPw());
    }

    @Test
    void wrongPasswordFailsForBothStorageFormats() throws Exception {
        when(memberMapper.getMemberInfo(MEMBER_ID)).thenReturn(storedMember(RAW_PASSWORD));
        assertNull(memberService.checkLoginData(loginAttempt("wrong-password")));
        verify(memberMapper, never()).updatePassword(any());

        when(memberMapper.getMemberInfo(MEMBER_ID))
                .thenReturn(storedMember(passwordEncoder.encode(RAW_PASSWORD)));
        assertNull(memberService.checkLoginData(loginAttempt("wrong-password")));
    }

    @Test
    void blankCredentialsFailWithoutTouchingTheDatabase() throws Exception {
        assertNull(memberService.checkLoginData(null));
        assertNull(memberService.checkLoginData(loginAttempt(" ")));

        MemberDTO noId = new MemberDTO();
        noId.setPw(RAW_PASSWORD);
        assertNull(memberService.checkLoginData(noId));

        verify(memberMapper, never()).getMemberInfo(any());
    }

    @Test
    void signUpStoresBcryptButKeepsRawPasswordOnTheDtoForAutoLogin() throws Exception {
        MemberDTO signUp = loginAttempt(RAW_PASSWORD);

        memberService.submitSignUp(signUp);

        ArgumentCaptor<MemberDTO> captor = ArgumentCaptor.forClass(MemberDTO.class);
        verify(memberMapper).submitSignUp(captor.capture());
        assertTrue(passwordEncoder.matches(RAW_PASSWORD, captor.getValue().getPw()));
        assertEquals(RAW_PASSWORD, signUp.getPw(), "가입 직후 자동 로그인을 위해 원문이 복원돼야 한다");
    }

    @Test
    void modifyMyInfoStoresBcryptButKeepsRawPasswordForSessionRefresh() throws Exception {
        MemberDTO modify = loginAttempt(RAW_PASSWORD);

        memberService.submitModifyMyInfo(modify);

        ArgumentCaptor<MemberDTO> captor = ArgumentCaptor.forClass(MemberDTO.class);
        verify(memberMapper).submitModifyMyInfo(captor.capture());
        assertTrue(passwordEncoder.matches(RAW_PASSWORD, captor.getValue().getPw()));
        assertEquals(RAW_PASSWORD, modify.getPw());
    }

    @Test
    void newPasswordsOutsideThePolicyAreRejected() {
        MemberDTO shortPw = loginAttempt("1234567");
        assertThrows(IllegalArgumentException.class, () -> memberService.submitSignUp(shortPw));

        MemberDTO longPw = loginAttempt("a".repeat(65));
        assertThrows(IllegalArgumentException.class, () -> memberService.submitModifyMyInfo(longPw));

        MemberDTO blankPw = loginAttempt("   ");
        assertThrows(IllegalArgumentException.class, () -> memberService.submitSignUp(blankPw));
    }

    @Test
    void temporaryPasswordIsStoredHashedButEmailedInPlaintextOnce() {
        MemberDTO member = storedMember("old-password");
        when(memberMapper.findByUserIdAndEmail(MEMBER_ID, "user@example.com")).thenReturn(member);

        String result = memberService.findPassword(MEMBER_ID, "user@example.com");

        assertEquals("success", result);
        ArgumentCaptor<MemberDTO> captor = ArgumentCaptor.forClass(MemberDTO.class);
        verify(memberMapper).updatePassword(captor.capture());
        String storedPw = captor.getValue().getPw();
        assertTrue(storedPw.startsWith("$2"), "임시 비밀번호도 해시로 저장돼야 한다");
    }
}
