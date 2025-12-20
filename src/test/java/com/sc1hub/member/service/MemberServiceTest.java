package com.sc1hub.member.service;

import com.sc1hub.member.dto.MemberDTO;
import com.sc1hub.member.mapper.MemberMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks
    private MemberServiceImpl memberService;

    @Test
    void findCredentials_returnsFalse_whenMemberNotFound() {
        String email = "test@example.com";
        when(memberMapper.findByEmail(email)).thenReturn(null);

        boolean result = memberService.findCredentials(email);

        assertFalse(result);
        verify(memberMapper, never()).updatePassword(any(MemberDTO.class));
        verifyNoInteractions(emailService);
    }

    @Test
    void findCredentials_updatesPassword_andSendsMail() throws Exception {
        String email = "test@example.com";
        MemberDTO member = new MemberDTO();

        when(memberMapper.findByEmail(email)).thenReturn(member);
        doNothing().when(memberMapper).updatePassword(any(MemberDTO.class));
        when(emailService.sendSimpleMessage(email)).thenReturn("ignored");

        boolean result = memberService.findCredentials(email);

        assertTrue(result);

        ArgumentCaptor<MemberDTO> memberCaptor = ArgumentCaptor.forClass(MemberDTO.class);
        verify(memberMapper).updatePassword(memberCaptor.capture());
        assertNotNull(memberCaptor.getValue().getPw());
        assertEquals(8, memberCaptor.getValue().getPw().length());

        verify(emailService).sendSimpleMessage(email);
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
        String tempPassword = memberCaptor.getValue().getPw();
        assertNotNull(tempPassword);
        assertEquals(8, tempPassword.length());

        verify(emailService).sendNewPasswordMessage(email, tempPassword);
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
