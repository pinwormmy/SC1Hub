package com.sc1hub.chat.controller;

import com.sc1hub.chat.dto.ChatMessageDTO;
import com.sc1hub.chat.dto.ChatSanctionDTO;
import com.sc1hub.chat.dto.ChatSanctionRequestDTO;
import com.sc1hub.chat.service.ChatModerationService;
import com.sc1hub.chat.service.ChatRoomService;
import com.sc1hub.member.dto.MemberDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/chat")
@Slf4j
public class ChatAdminController {

    private final ChatRoomService chatRoomService;
    private final ChatModerationService moderationService;

    public ChatAdminController(ChatRoomService chatRoomService, ChatModerationService moderationService) {
        this.chatRoomService = chatRoomService;
        this.moderationService = moderationService;
    }

    @DeleteMapping(value = "/messages/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> deleteMessage(@PathVariable("id") long id, HttpSession session) {
        ResponseEntity<Map<String, Object>> denied = requireAdmin(session);
        if (denied != null) {
            return denied;
        }
        boolean found = chatRoomService.deleteMessage(id);
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("found", found);
        return ResponseEntity.ok(body);
    }

    @GetMapping(value = "/sanctions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> sanctions(HttpSession session) {
        ResponseEntity<Map<String, Object>> denied = requireAdmin(session);
        if (denied != null) {
            return denied;
        }
        List<ChatSanctionDTO> sanctions = moderationService.getActiveSanctions();
        return ResponseEntity.ok(sanctions);
    }

    @PostMapping(value = "/sanctions", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> addSanction(@RequestBody(required = false) ChatSanctionRequestDTO request,
                                                           HttpSession session) {
        ResponseEntity<Map<String, Object>> denied = requireAdmin(session);
        if (denied != null) {
            return denied;
        }

        Map<String, Object> body = new HashMap<>();
        String type = request == null ? null : request.getType();
        String nickname = request == null ? null : request.getNickname();
        if (!ChatModerationService.TYPE_MUTE.equals(type) && !ChatModerationService.TYPE_BLOCK_IP.equals(type)) {
            body.put("error", "제재 유형은 MUTE 또는 BLOCK_IP 이어야 합니다.");
            return ResponseEntity.badRequest().body(body);
        }
        if (!StringUtils.hasText(nickname)) {
            body.put("error", "대상 닉네임을 입력해주세요.");
            return ResponseEntity.badRequest().body(body);
        }

        ChatMessageDTO target = chatRoomService.findLatestByNickname(nickname.trim());
        if (target == null) {
            body.put("error", "최근 채팅에서 해당 닉네임을 찾을 수 없습니다.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }
        if (ChatRoomService.ROLE_AI.equals(target.getRole()) || ChatRoomService.ROLE_ADMIN.equals(target.getRole())) {
            body.put("error", "해당 사용자는 제재할 수 없습니다.");
            return ResponseEntity.badRequest().body(body);
        }

        if (ChatModerationService.TYPE_MUTE.equals(type) && !StringUtils.hasText(target.getMemberId())) {
            body.put("error", "게스트는 IP 차단(BLOCK_IP)만 가능합니다.");
            return ResponseEntity.badRequest().body(body);
        }
        if (ChatModerationService.TYPE_BLOCK_IP.equals(type) && !StringUtils.hasText(target.getIp())) {
            body.put("error", "해당 사용자의 IP 정보가 없어 차단할 수 없습니다.");
            return ResponseEntity.badRequest().body(body);
        }

        MemberDTO admin = getMember(session);
        ChatSanctionDTO sanction = moderationService.addSanction(
                type,
                ChatModerationService.TYPE_MUTE.equals(type) ? target.getMemberId() : null,
                ChatModerationService.TYPE_BLOCK_IP.equals(type) ? target.getIp() : null,
                target.getNickname(),
                request.getMinutes(),
                request.getReason(),
                admin.getId());

        body.put("success", true);
        body.put("sanction", sanction);
        return ResponseEntity.ok(body);
    }

    @DeleteMapping(value = "/sanctions/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> revokeSanction(@PathVariable("id") long id, HttpSession session) {
        ResponseEntity<Map<String, Object>> denied = requireAdmin(session);
        if (denied != null) {
            return denied;
        }
        moderationService.revokeSanction(id);
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        return ResponseEntity.ok(body);
    }

    /**
     * AdminInterceptor responds with an HTML script tag, which fetch clients cannot
     * parse — this in-controller check is the one that yields a clean JSON error.
     */
    private ResponseEntity<Map<String, Object>> requireAdmin(HttpSession session) {
        MemberDTO member = getMember(session);
        if (member == null || member.getGrade() != 3) {
            Map<String, Object> body = new HashMap<>();
            body.put("error", "관리자 전용 기능입니다.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }
        return null;
    }

    private static MemberDTO getMember(HttpSession session) {
        return session == null ? null : (MemberDTO) session.getAttribute("member");
    }
}
