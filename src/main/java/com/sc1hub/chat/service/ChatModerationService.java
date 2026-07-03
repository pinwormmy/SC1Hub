package com.sc1hub.chat.service;

import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.chat.config.ChatProperties;
import com.sc1hub.chat.dto.ChatSanctionDTO;
import com.sc1hub.chat.mapper.ChatMapper;
import com.sc1hub.common.util.BlockedWordMatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ChatModerationService {

    public static final String TYPE_MUTE = "MUTE";
    public static final String TYPE_BLOCK_IP = "BLOCK_IP";

    private static final LocalDateTime PERMANENT = LocalDateTime.of(9999, 1, 1, 0, 0);
    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ChatMapper chatMapper;
    private final ChatProperties chatProperties;
    private final AssistantProperties assistantProperties;

    private final Map<String, LocalDateTime> mutedMembers = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> blockedIps = new ConcurrentHashMap<>();

    public ChatModerationService(ChatMapper chatMapper,
                                 ChatProperties chatProperties,
                                 AssistantProperties assistantProperties) {
        this.chatMapper = chatMapper;
        this.chatProperties = chatProperties;
        this.assistantProperties = assistantProperties;
    }

    @PostConstruct
    public void reloadSanctions() {
        try {
            List<ChatSanctionDTO> sanctions = chatMapper.selectActiveSanctions();
            mutedMembers.clear();
            blockedIps.clear();
            if (sanctions == null) {
                return;
            }
            for (ChatSanctionDTO sanction : sanctions) {
                LocalDateTime expiry = sanction.getExpiresAt() == null ? PERMANENT : sanction.getExpiresAt();
                if (TYPE_MUTE.equals(sanction.getSanctionType()) && StringUtils.hasText(sanction.getMemberId())) {
                    mutedMembers.merge(sanction.getMemberId(), expiry, (a, b) -> a.isAfter(b) ? a : b);
                } else if (TYPE_BLOCK_IP.equals(sanction.getSanctionType()) && StringUtils.hasText(sanction.getIp())) {
                    blockedIps.merge(sanction.getIp(), expiry, (a, b) -> a.isAfter(b) ? a : b);
                }
            }
        } catch (Exception e) {
            log.error("채팅 제재 목록 로딩 중 오류 발생", e);
        }
    }

    public String findBlockedWord(String text) {
        List<String> words = new ArrayList<>();
        if (chatProperties.getBlockedWords() != null) {
            words.addAll(chatProperties.getBlockedWords());
        }
        if (assistantProperties.getBlockedWords() != null) {
            words.addAll(assistantProperties.getBlockedWords());
        }
        return BlockedWordMatcher.findBlockedWord(words, text);
    }

    /**
     * @return null if allowed, otherwise a user-facing restriction message.
     */
    public String checkRestricted(String memberId, String ip) {
        LocalDateTime expiry = null;
        if (StringUtils.hasText(memberId)) {
            expiry = activeExpiry(mutedMembers, memberId);
        }
        if (expiry == null && StringUtils.hasText(ip)) {
            expiry = activeExpiry(blockedIps, ip);
        }
        if (expiry == null) {
            return null;
        }
        if (PERMANENT.equals(expiry)) {
            return "채팅 이용이 제한되었습니다.";
        }
        return "채팅 이용이 제한되었습니다. (해제: " + expiry.format(EXPIRY_FORMAT) + ")";
    }

    public ChatSanctionDTO addSanction(String type, String memberId, String ip, String nickname,
                                       Integer minutes, String reason, String createdBy) {
        ChatSanctionDTO sanction = new ChatSanctionDTO();
        sanction.setSanctionType(type);
        sanction.setMemberId(memberId);
        sanction.setIp(ip);
        sanction.setNickname(nickname);
        sanction.setReason(reason);
        sanction.setCreatedBy(createdBy);
        if (minutes != null && minutes > 0) {
            sanction.setExpiresAt(LocalDateTime.now().plusMinutes(minutes));
        }
        chatMapper.insertSanction(sanction);
        reloadSanctions();
        return sanction;
    }

    public List<ChatSanctionDTO> getActiveSanctions() {
        List<ChatSanctionDTO> sanctions = chatMapper.selectActiveSanctions();
        return sanctions == null ? new ArrayList<>() : sanctions;
    }

    public void revokeSanction(long id) {
        chatMapper.revokeSanction(id);
        reloadSanctions();
    }

    private LocalDateTime activeExpiry(Map<String, LocalDateTime> cache, String key) {
        LocalDateTime expiry = cache.get(key);
        if (expiry == null) {
            return null;
        }
        if (!PERMANENT.equals(expiry) && !expiry.isAfter(LocalDateTime.now())) {
            cache.remove(key);
            return null;
        }
        return expiry;
    }
}
