package com.sc1hub.assistant.service;

import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.assistant.dto.AssistantSearchLogDTO;
import com.sc1hub.assistant.mapper.AssistantSearchLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AssistantSearchLogService {

    private static final List<String> DEFAULT_BLOCKED_WORDS = Arrays.asList(
            "시발", "씨발", "ㅅㅂ", "병신", "ㅂㅅ",
            "좆", "좇", "fuck", "shit", "bitch", "asshole"
    );

    private final AssistantSearchLogMapper searchLogMapper;
    private final AssistantProperties assistantProperties;
    private final Clock clock;

    @Autowired
    public AssistantSearchLogService(AssistantSearchLogMapper searchLogMapper,
                                     AssistantProperties assistantProperties) {
        this(searchLogMapper, assistantProperties, Clock.systemDefaultZone());
    }

    AssistantSearchLogService(AssistantSearchLogMapper searchLogMapper,
                              AssistantProperties assistantProperties,
                              Clock clock) {
        this.searchLogMapper = searchLogMapper;
        this.assistantProperties = assistantProperties;
        this.clock = clock;
    }

    public void recordSearch(String message) {
        if (!StringUtils.hasText(message)) {
            return;
        }
        String trimmed = message.trim();
        if (!StringUtils.hasText(trimmed) || isBlocked(trimmed)) {
            return;
        }
        try {
            cleanupOldLogs();
            searchLogMapper.insertLog(trimmed);
        } catch (Exception e) {
            log.warn("AI 검색어 저장 실패", e);
        }
    }

    public List<AssistantSearchLogDTO> getLatestSearches() {
        int limit = Math.max(1, assistantProperties.getSearchLogLimit());
        try {
            cleanupOldLogs();
            List<AssistantSearchLogDTO> logs = searchLogMapper.selectLatest(limit);
            if (logs == null || logs.isEmpty()) {
                return Collections.emptyList();
            }
            return logs.stream()
                    .filter(log -> log != null && StringUtils.hasText(log.getMessage()))
                    .filter(log -> !isBlocked(log.getMessage()))
                    .limit(limit)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("AI 최신 검색어 조회 실패", e);
            return Collections.emptyList();
        }
    }

    private void cleanupOldLogs() {
        int retentionDays = assistantProperties.getSearchLogRetentionDays();
        if (retentionDays <= 0) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(retentionDays);
        searchLogMapper.deleteOlderThan(cutoff);
    }

    private boolean isBlocked(String message) {
        String normalized = normalize(message);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        for (String blocked : resolveBlockedWords()) {
            String token = normalize(blocked);
            if (!StringUtils.hasText(token)) {
                continue;
            }
            if (normalized.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private List<String> resolveBlockedWords() {
        List<String> configured = assistantProperties.getBlockedWords();
        if (configured != null && !configured.isEmpty()) {
            return configured;
        }
        return DEFAULT_BLOCKED_WORDS;
    }

    private static String normalize(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
