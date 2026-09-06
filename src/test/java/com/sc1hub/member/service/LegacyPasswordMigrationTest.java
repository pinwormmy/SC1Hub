package com.sc1hub.member.service;

import com.sc1hub.member.dto.MemberDTO;
import com.sc1hub.member.mapper.MemberMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyPasswordMigrationTest {

    @Mock
    private MemberMapper memberMapper;

    // 테스트 속도를 위해 낮은 강도를 쓴다. 검증 의미는 동일하다.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private MemberDTO member(String id, String pw) {
        MemberDTO member = new MemberDTO();
        member.setId(id);
        member.setPw(pw);
        return member;
    }

    @Test
    void hashesPlaintextRowsInPlaceAndLeavesBcryptAndOverlongRowsAlone() {
        String alreadyHashed = passwordEncoder.encode("irrelevant");
        when(memberMapper.selectLegacyPasswordMembers()).thenReturn(List.of(
                member("plain", "legacy-secret"),
                member("hashed", alreadyHashed),
                member("overlong", "가".repeat(25))));
        when(memberMapper.updatePasswordIfUnchanged(eq("plain"), eq("legacy-secret"), anyString())).thenReturn(1);

        LegacyPasswordMigration.Summary summary =
                new LegacyPasswordMigration(memberMapper, passwordEncoder, true).run();

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(memberMapper).updatePasswordIfUnchanged(eq("plain"), eq("legacy-secret"), hash.capture());
        assertTrue(hash.getValue().startsWith("$2"));
        assertTrue(passwordEncoder.matches("legacy-secret", hash.getValue()), "평문으로 계속 로그인할 수 있어야 한다");
        verify(memberMapper, never()).updatePasswordIfUnchanged(eq("hashed"), any(), any());
        verify(memberMapper, never()).updatePasswordIfUnchanged(eq("overlong"), any(), any());
        assertEquals(3, summary.candidates());
        assertEquals(1, summary.upgraded());
        assertEquals(2, summary.skipped());
        assertEquals(0, summary.failed());
        assertNull(summary.error());
    }

    @Test
    void concurrentChangeIsSkippedNotOverwritten() {
        when(memberMapper.selectLegacyPasswordMembers()).thenReturn(List.of(member("plain", "legacy-secret")));
        // 읽은 뒤 로그인 승격으로 값이 바뀌어 낙관적 UPDATE 가 0행을 갱신한 경우
        when(memberMapper.updatePasswordIfUnchanged(eq("plain"), eq("legacy-secret"), anyString())).thenReturn(0);

        LegacyPasswordMigration.Summary summary =
                new LegacyPasswordMigration(memberMapper, passwordEncoder, true).run();

        assertEquals(0, summary.upgraded());
        assertEquals(1, summary.skipped());
        verify(memberMapper, never()).updatePassword(any());
    }

    @Test
    void databaseFailureNeverPropagatesOutOfStartup() {
        when(memberMapper.selectLegacyPasswordMembers()).thenThrow(new IllegalStateException("synthetic db failure"));
        LegacyPasswordMigration migration = new LegacyPasswordMigration(memberMapper, passwordEncoder, true);

        migration.migrateOnStartup();

        LegacyPasswordMigration.Summary summary = migration.getLastRun();
        assertNotNull(summary);
        assertEquals("IllegalStateException", summary.error());
        assertEquals(0, summary.upgraded());
    }

    @Test
    void startupRunHonoursTheDisableSwitch() {
        LegacyPasswordMigration migration = new LegacyPasswordMigration(memberMapper, passwordEncoder, false);

        migration.migrateOnStartup();

        verify(memberMapper, never()).selectLegacyPasswordMembers();
        assertNull(migration.getLastRun());
    }
}
