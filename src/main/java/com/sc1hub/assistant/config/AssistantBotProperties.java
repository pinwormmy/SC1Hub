package com.sc1hub.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import org.springframework.util.StringUtils;

@Data
@ConfigurationProperties(prefix = "sc1hub.assistant.bot")
public class AssistantBotProperties {
    private static final int RANDOM_SLOT_GRANULARITY_MINUTES = 1;

    private boolean enabled = false;
    private String boardTitle = "funboard";
    private String personaName = "프징징봇";
    private String model = "gemini-2.5-flash-lite";
    private int recentPostLimit = 12;
    private int recentCommentLimit = 24;
    private int recentHistoryLimit = 20;
    private int promptExcerptChars = 220;
    private int maxPromptChars = 12000;
    private int maxOutputTokens = 1400;
    private int maxGenerateAttempts = 3;
    private int autoPublishMaxGenerateAttempts = 1;
    private int dailyGenerateCallLimit = 20;
    private double duplicateSimilarityThreshold = 0.72;
    private int selfReviewMinimumScore = 0;
    private String publishGuestPassword = "bot-approved";
    private boolean autoPublishEnabled = false;
    private String autoPublishCron = "0 * * * * *";
    private String autoPublishZone = "Asia/Seoul";
    private boolean autoPublishCatchUpEnabled = false;
    private int autoPublishPostDailyLimit = 3;
    private int autoPublishCommentDailyLimit = 5;
    private int autoPublishCommentCandidatePosts = 10;
    private double autoPublishCommentReplyPriorityProbability = 0.9;
    private List<PersonaProperties> personas = new ArrayList<>();

    public List<Integer> buildDailyAutoPublishSlots(LocalDate date, String slotKey, int dailyLimit) {
        return buildDailyAutoPublishSlots(date, slotKey, dailyLimit, boardTitle, personaName);
    }

    public List<Integer> buildDailyAutoPublishSlots(LocalDate date,
                                                    String slotKey,
                                                    int dailyLimit,
                                                    String boardTitleSeed,
                                                    String personaNameSeed) {
        if (date == null) {
            return Collections.emptyList();
        }

        List<Integer> candidateMinutes = buildCandidateMinutes();
        if (candidateMinutes.isEmpty()) {
            return Collections.emptyList();
        }

        List<Integer> shuffledMinutes = new ArrayList<>(candidateMinutes);
        Collections.shuffle(shuffledMinutes, new Random(buildDailySeed(date, slotKey, boardTitleSeed, personaNameSeed)));

        int safeDailyLimit = Math.max(0, dailyLimit);
        List<Integer> selectedMinutes = new ArrayList<>();
        for (Integer minuteOfDay : shuffledMinutes) {
            if (minuteOfDay == null) {
                continue;
            }
            selectedMinutes.add(minuteOfDay);
            if (selectedMinutes.size() >= safeDailyLimit) {
                break;
            }
        }

        Collections.sort(selectedMinutes);
        return selectedMinutes;
    }

    private List<Integer> buildCandidateMinutes() {
        return buildRange(0, (24 * 60) - 1);
    }

    private List<Integer> buildRange(int startMinute, int endMinute) {
        if (startMinute > endMinute) {
            return Collections.emptyList();
        }

        List<Integer> minutes = new ArrayList<>();
        int firstMinute = startMinute;
        int remainder = firstMinute % RANDOM_SLOT_GRANULARITY_MINUTES;
        if (remainder != 0) {
            firstMinute += RANDOM_SLOT_GRANULARITY_MINUTES - remainder;
        }

        for (int minute = firstMinute; minute <= endMinute; minute += RANDOM_SLOT_GRANULARITY_MINUTES) {
            minutes.add(minute);
        }

        return minutes;
    }

    private long buildDailySeed(LocalDate date, String slotKey, String boardTitleSeed, String personaNameSeed) {
        long seed = date.toEpochDay();
        seed = (seed * 31) + Objects.hashCode(boardTitleSeed);
        seed = (seed * 31) + Objects.hashCode(personaNameSeed);
        seed = (seed * 31) + Objects.hashCode(slotKey);
        seed = (seed * 31) + autoPublishPostDailyLimit;
        seed = (seed * 31) + autoPublishCommentDailyLimit;
        return seed;
    }

    public PersonaProperties resolvePersona(String requestedPersonaName) {
        List<PersonaProperties> configured = getEnabledPersonas();
        if (configured.isEmpty()) {
            return null;
        }
        if (!StringUtils.hasText(requestedPersonaName)) {
            return configured.get(0);
        }
        String normalized = requestedPersonaName.trim().toLowerCase(Locale.ROOT);
        for (PersonaProperties persona : configured) {
            if (persona == null || !StringUtils.hasText(persona.getName())) {
                continue;
            }
            if (normalized.equals(persona.getName().trim().toLowerCase(Locale.ROOT))) {
                return persona;
            }
        }
        return null;
    }

    public List<PersonaProperties> getEnabledPersonas() {
        List<PersonaProperties> enabledPersonas = new ArrayList<>();
        if (personas != null && !personas.isEmpty()) {
            for (PersonaProperties persona : personas) {
                PersonaProperties normalized = normalizePersona(persona);
                if (normalized != null && normalized.isEnabled()) {
                    enabledPersonas.add(normalized);
                }
            }
        }
        if (!enabledPersonas.isEmpty()) {
            return enabledPersonas;
        }

        PersonaProperties legacy = new PersonaProperties();
        legacy.setEnabled(true);
        legacy.setName(personaName);
        legacy.setBoardTitle(boardTitle);
        PersonaProperties normalizedLegacy = normalizePersona(legacy);
        if (normalizedLegacy != null) {
            enabledPersonas.add(normalizedLegacy);
        }
        return enabledPersonas;
    }

    private PersonaProperties normalizePersona(PersonaProperties persona) {
        if (persona == null) {
            return null;
        }
        String resolvedName = StringUtils.hasText(persona.getName()) ? persona.getName().trim() : null;
        if (!StringUtils.hasText(resolvedName)) {
            return null;
        }
        PersonaProperties normalized = new PersonaProperties();
        normalized.setEnabled(persona.isEnabled());
        normalized.setName(resolvedName);
        normalized.setBoardTitle(StringUtils.hasText(persona.getBoardTitle()) ? persona.getBoardTitle().trim() : boardTitle);
        return normalized;
    }

    @Data
    public static class PersonaProperties {
        private boolean enabled = true;
        private String name;
        private String boardTitle;
    }
}
