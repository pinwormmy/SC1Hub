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
    private WriterNicknameGuard writerNicknameGuard;

    @Mock
    private MemberRecommendationCleanup recommendationCleanup;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private MemberServiceImpl memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberServiceImpl(memberMapper, passwordEncoder, writerNicknameGuard,
                recommendationCleanup);
    }

    @Test
    void deleteMemberRemovesRecommendationRowsBeforeTheMemberRow() {
        memberService.deleteMember(MEMBER_ID);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(recommendationCleanup, memberMapper);
        order.verify(recommendationCleanup).removeRecommendationsOf(MEMBER_ID);
        order.verify(memberMapper).deleteMember(MEMBER_ID);
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
        signUp.setNickName("tester");

        memberService.submitSignUp(signUp);

        ArgumentCaptor<MemberDTO> captor = ArgumentCaptor.forClass(MemberDTO.class);
        verify(memberMapper).submitSignUp(captor.capture());
        assertTrue(passwordEncoder.matches(RAW_PASSWORD, captor.getValue().getPw()));
        assertEquals(RAW_PASSWORD, signUp.getPw(), "가입 직후 자동 로그인을 위해 원문이 복원돼야 한다");
    }

    @Test
    void modifyMyInfoStoresBcryptButKeepsRawPasswordForSessionRefresh() throws Exception {
        MemberDTO modify = loginAttempt(RAW_PASSWORD);
        modify.setNickName("tester");

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
    void signUpRejectsNicknameContainingMarkup() throws Exception {
        MemberDTO signUp = loginAttempt(RAW_PASSWORD);
        signUp.setNickName("<img src=x onerror=alert(1)>");

        assertThrows(IllegalArgumentException.class, () -> memberService.submitSignUp(signUp));
        verify(memberMapper, never()).submitSignUp(any());
    }

    @Test
    void modifyMyInfoRejectsEmailContainingAngleBrackets() throws Exception {
        MemberDTO modify = loginAttempt(RAW_PASSWORD);
        modify.setNickName("tester");
        modify.setEmail("a@b.com<script>");

        assertThrows(IllegalArgumentException.class, () -> memberService.submitModifyMyInfo(modify));
        verify(memberMapper, never()).submitModifyMyInfo(any());
    }

    @Test
    void newPasswordExceedingSeventyTwoUtf8BytesIsRejected() throws Exception {
        // 한글 25자는 64자 이내지만 UTF-8 로 75바이트라 BCrypt 72바이트 절단 구간에 들어간다.
        MemberDTO signUp = loginAttempt("가".repeat(25));
        signUp.setNickName("tester");

        assertThrows(IllegalArgumentException.class, () -> memberService.submitSignUp(signUp));
        verify(memberMapper, never()).submitSignUp(any());
    }

    @Test
    void signUpRejectsNicknameUsedOnExistingMemberPosts() throws Exception {
        MemberDTO signUp = loginAttempt(RAW_PASSWORD);
        signUp.setNickName("released-author");
        when(memberMapper.isUniqueNickName("released-author")).thenReturn("0");
        when(writerNicknameGuard.hasAuthoredPosts("released-author")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> memberService.submitSignUp(signUp));
        verify(memberMapper, never()).submitSignUp(any());
    }

    @Test
    void signUpRejectsNicknameAlreadyHeldByAnotherMember() throws Exception {
        MemberDTO signUp = loginAttempt(RAW_PASSWORD);
        signUp.setNickName("taken");
        when(memberMapper.isUniqueNickName("taken")).thenReturn("1");

        assertThrows(IllegalArgumentException.class, () -> memberService.submitSignUp(signUp));
        verify(memberMapper, never()).submitSignUp(any());
        verify(writerNicknameGuard, never()).hasAuthoredPosts(any());
    }

    @Test
    void modifyMyInfoSkipsReservationCheckWhenNicknameIsUnchanged() throws Exception {
        MemberDTO current = storedMember(passwordEncoder.encode(RAW_PASSWORD));
        current.setNickName("tester");
        when(memberMapper.getMemberInfo(MEMBER_ID)).thenReturn(current);
        MemberDTO modify = loginAttempt(RAW_PASSWORD);
        modify.setNickName("tester");

        memberService.submitModifyMyInfo(modify);

        verify(memberMapper).submitModifyMyInfo(any());
        verify(memberMapper, never()).isUniqueNickName(any());
        verify(writerNicknameGuard, never()).hasAuthoredPosts(any());
    }

    @Test
    void signUpRejectsIdsOutsideTheAllowedFormat() throws Exception {
        for (String badId : new String[]{"XALGTEST1", "ab", "1abc", "test-user", "a".repeat(21), "테스터"}) {
            MemberDTO signUp = new MemberDTO();
            signUp.setId(badId);
            signUp.setPw(RAW_PASSWORD);
            signUp.setNickName("tester");
            assertThrows(IllegalArgumentException.class, () -> memberService.submitSignUp(signUp), badId);
        }
        verify(memberMapper, never()).submitSignUp(any());
    }

    @Test
    void adminEditRejectsOutOfRangeGrade() {
        MemberDTO edit = new MemberDTO();
        edit.setId(MEMBER_ID);
        edit.setNickName("tester");
        edit.setGrade(9);

        assertThrows(IllegalArgumentException.class, () -> memberService.submitModifyMemberByAdmin(edit));
        verify(memberMapper, never()).submitModifyMemberByAdmin(any());
    }
}
