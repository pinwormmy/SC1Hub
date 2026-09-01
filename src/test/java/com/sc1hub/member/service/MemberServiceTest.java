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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private EmailService emailService;

    @Mock
    private MemberMapper memberMapper;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private MemberServiceImpl memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberServiceImpl(memberMapper, emailService, passwordEncoder);
    }

    @Test
    void findPassword_returnsMessage_whenMemberNotFound() {
        String userId = "user";
        String email = "test@example.com";
        when(memberMapper.findByUserIdAndEmail(userId, email)).thenReturn(null);

        String result = memberService.findPassword(userId, email);

        assertTrue(result.contains("찾을 수 없습니다"));
        verify(memberMapper, never()).updatePassword(any(MemberDTO.class));
        verifyNoInteractions(emailService);
    }

    @Test
    void findPassword_returnsSuccess_andSendsTempPassword() throws Exception {
        String userId = "user";
        String email = "test@example.com";
        MemberDTO member = new MemberDTO();

        when(memberMapper.findByUserIdAndEmail(userId, email)).thenReturn(member);
        doNothing().when(memberMapper).updatePassword(any(MemberDTO.class));
        doNothing().when(emailService).sendNewPasswordMessage(eq(email), anyString());

        String result = memberService.findPassword(userId, email);

        assertEquals("success", result);

        ArgumentCaptor<MemberDTO> memberCaptor = ArgumentCaptor.forClass(MemberDTO.class);
        verify(memberMapper).updatePassword(memberCaptor.capture());
        String storedPassword = memberCaptor.getValue().getPw();
        assertNotNull(storedPassword);
        assertTrue(storedPassword.startsWith("$2"),
                "임시 비밀번호는 평문이 아니라 BCrypt로 저장돼야 한다");

        ArgumentCaptor<String> mailCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendNewPasswordMessage(eq(email), mailCaptor.capture());
        String mailedPassword = mailCaptor.getValue();
        assertEquals(12, mailedPassword.length());
        assertTrue(passwordEncoder.matches(mailedPassword, storedPassword),
                "메일로 보낸 임시 비밀번호가 저장된 해시와 일치해야 한다");
    }

    @Test
    void findPassword_returnsErrorMessage_whenEmailFails() throws Exception {
        String userId = "user";
        String email = "test@example.com";
        MemberDTO member = new MemberDTO();

        when(memberMapper.findByUserIdAndEmail(userId, email)).thenReturn(member);
        doNothing().when(memberMapper).updatePassword(any(MemberDTO.class));
        doThrow(new RuntimeException("mail failed")).when(emailService).sendNewPasswordMessage(eq(email), anyString());

        String result = memberService.findPassword(userId, email);

        assertTrue(result.contains("문제가 발생"));
        verify(memberMapper).updatePassword(any(MemberDTO.class));
        verify(emailService).sendNewPasswordMessage(eq(email), anyString());
    }
}
