package com.sc1hub.member.controller;

import com.sc1hub.chat.dto.ChatSanctionDTO;
import com.sc1hub.board.service.GuestPasswordMigration;
import com.sc1hub.chat.service.ChatModerationService;
import com.sc1hub.common.security.OffenderTracker;
import com.sc1hub.common.security.SecuritySwitches;
import com.sc1hub.member.dto.MemberDTO;
import com.sc1hub.member.mapper.MemberMapper;
import com.sc1hub.member.service.LegacyPasswordMigration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityAdminControllerTest {

    @Mock
    private MemberMapper memberMapper;
    @Mock
    private LegacyPasswordMigration legacyPasswordMigration;
    @Mock
    private ChatModerationService moderationService;
    @Mock
    private OffenderTracker offenderTracker;
    @Mock
    private GuestPasswordMigration guestPasswordMigration;

    private final SecuritySwitches switches = new SecuritySwitches(true, false);
    private SecurityAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new SecurityAdminController(memberMapper, legacyPasswordMigration, switches,
                moderationService, offenderTracker, guestPasswordMigration);
    }

    private Map<String, Object> body(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @Test
    void writeSwitchesFlipAtRuntimeWithoutARedeploy() {
        when(moderationService.getActiveSanctions()).thenReturn(List.of());

        Map<String, Object> status = controller.setPublicWrites(body("enabled", false), null);

        assertFalse(switches.isPublicWritesEnabled());
        assertEquals(false, status.get("publicWritesEnabled"));
        assertEquals(true, status.get("publicWritesDefault"));

        controller.setGuestWrites(body("enabled", "true"), null);
        assertTrue(switches.isGuestWritesEnabled());
    }

    @Test
    void blockIpRefusesPrivateAndMalformedAddresses() {
        ResponseEntity<Map<String, Object>> privateIp =
                controller.addSanction(body("type", "BLOCK_IP", "target", "10.0.0.1"), null);
        ResponseEntity<Map<String, Object>> malformed =
                controller.addSanction(body("type", "BLOCK_IP", "target", "not-an-ip"), null);

        assertEquals(HttpStatus.BAD_REQUEST, privateIp.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, malformed.getStatusCode());
        verify(moderationService, never()).addSanction(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void muteRefusesUnknownMembersAndAdministrators() {
        when(memberMapper.getMemberInfo("ghost")).thenReturn(null);
        MemberDTO admin = new MemberDTO();
        admin.setId("admin");
        admin.setGrade(3);
        when(memberMapper.getMemberInfo("admin")).thenReturn(admin);

        assertEquals(HttpStatus.NOT_FOUND,
                controller.addSanction(body("type", "MUTE", "target", "ghost"), null).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                controller.addSanction(body("type", "MUTE", "target", "admin"), null).getStatusCode());
        verify(moderationService, never()).addSanction(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void validBlockIpIsStoredWithTheGivenDurationAndReason() {
        ChatSanctionDTO stored = new ChatSanctionDTO();
        stored.setId(7);
        when(moderationService.addSanction(eq("BLOCK_IP"), isNull(), eq("203.0.113.9"), eq("203.0.113.9"),
                eq(60), eq("도배"), eq("admin"))).thenReturn(stored);

        ResponseEntity<Map<String, Object>> response = controller.addSanction(
                body("type", "BLOCK_IP", "target", "203.0.113.9", "minutes", 60, "reason", "도배"), null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(7L, response.getBody().get("id"));
    }
}
