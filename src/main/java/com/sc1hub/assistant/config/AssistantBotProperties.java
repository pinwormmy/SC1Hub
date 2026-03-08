package com.sc1hub.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

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
    private double duplicateSimilarityThreshold = 0.72;
    private int selfReviewMinimumScore = 0;
    private String publishGuestPassword = "bot-approved";
    private boolean autoPublishEnabled = false;
    private String autoPublishCron = "0 * * * * *";
    private String autoPublishZone = "Asia/Seoul";
    private int autoPublishPostDailyLimit = 5;
    private int autoPublishCommentDailyLimit = 10;
    private int autoPublishCommentCandidatePosts = 6;

    public List<Integer> buildDailyAutoPublishSlots(LocalDate date, String slotKey, int dailyLimit) {
        if (date == null) {
            return Collections.emptyList();
        }

        List<Integer> candidateMinutes = buildCandidateMinutes();
        if (candidateMinutes.isEmpty()) {
            return Collections.emptyList();
        }

        List<Integer> shuffledMinutes = new ArrayList<>(candidateMinutes);
        Collections.shuffle(shuffledMinutes, new Random(buildDailySeed(date, slotKey)));

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

    private long buildDailySeed(LocalDate date, String slotKey) {
        long seed = date.toEpochDay();
        seed = (seed * 31) + Objects.hashCode(boardTitle);
        seed = (seed * 31) + Objects.hashCode(personaName);
        seed = (seed * 31) + Objects.hashCode(slotKey);
        seed = (seed * 31) + autoPublishPostDailyLimit;
        seed = (seed * 31) + autoPublishCommentDailyLimit;
        return seed;
    }
}
