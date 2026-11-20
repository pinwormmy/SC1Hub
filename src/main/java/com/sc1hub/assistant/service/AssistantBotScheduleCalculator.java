package com.sc1hub.assistant.service;

import com.sc1hub.assistant.config.AssistantBotProperties;
import com.sc1hub.assistant.config.AssistantBotProperties.PersonaProperties;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * AI 봇의 일일 무작위 발행 슬롯, 재시도/캐치업 여부, 대기 상태 문자열을 계산한다.
 */
public class AssistantBotScheduleCalculator {

    static final String CHAT_RETRY_COOLDOWN = "chat_retry_cooldown";
    static final String CHAT_RETRY_LIMIT_REACHED = "chat_retry_limit_reached";

    private static final String MODE_POST = "post";
    private static final String MODE_COMMENT = "comment";
    private static final String MODE_CHAT = "chat";

    private final AssistantBotProperties botProperties;

    public AssistantBotScheduleCalculator(AssistantBotProperties botProperties) {
        this.botProperties = botProperties;
    }

    static ZoneId resolveZoneId(AssistantBotProperties botProperties) {
        String configured = botProperties == null ? null : botProperties.getAutoPublishZone();
        if (StringUtils.hasText(configured)) {
            try {
                return ZoneId.of(configured.trim());
            } catch (Exception ignored) {
                // fallback to Asia/Seoul
            }
        }
        return ZoneId.of("Asia/Seoul");
    }

    List<Integer> resolveChatAutoPublishSlots(PersonaProperties persona, LocalDate date, int dailyLimit) {
        if (persona == null || date == null || dailyLimit <= 0) {
            return Collections.emptyList();
        }
        return botProperties.buildDailyAutoPublishSlots(
                date, MODE_CHAT, dailyLimit, persona.getBoardTitle(), persona.getName());
    }

    String buildChatWaitingDetail(PersonaProperties persona,
                                  LocalDate date,
                                  LocalTime now,
                                  int publishedChatToday,
                                  int attemptedChatToday,
                                  int attemptedChatInRetryWindow,
                                  int chatDailyLimit) {
        String retryWaitingDetail = resolveChatRetryWaitingDetail(
                persona, publishedChatToday, attemptedChatToday, attemptedChatInRetryWindow, chatDailyLimit);
        if (StringUtils.hasText(retryWaitingDetail)) {
            return retryWaitingDetail;
        }
        Integer nextSlot = resolveNextUpcomingChatSlot(persona, date, now, chatDailyLimit);
        if (nextSlot == null) {
            return publishedChatToday >= chatDailyLimit ? "daily_limits_reached" : "no_remaining_slot_today";
        }
        return "waiting_random_slot:chat=" + formatMinuteOfDay(nextSlot);
    }

    String resolveChatRetryWaitingDetail(PersonaProperties persona,
                                         int publishedChatToday,
                                         int attemptedChatToday,
                                         int attemptedChatInRetryWindow,
                                         int chatDailyLimit) {
        if (publishedChatToday >= chatDailyLimit) {
            return "daily_limits_reached";
        }
        int maxAttemptsPerDay = resolveChatMaxAttemptsPerDay(persona);
        if (attemptedChatToday >= maxAttemptsPerDay) {
            return CHAT_RETRY_LIMIT_REACHED;
        }
        if (attemptedChatToday > publishedChatToday && attemptedChatInRetryWindow > 0) {
            return CHAT_RETRY_COOLDOWN;
        }
        return null;
    }

    int resolveChatDailyLimit(PersonaProperties persona) {
        if (persona != null && persona.getAutoPublishChatDailyLimit() != null) {
            return Math.max(0, persona.getAutoPublishChatDailyLimit());
        }
        return Math.max(0, botProperties.getAutoPublishChatDailyLimit());
    }

    int resolveChatMaxAttemptsPerDay(PersonaProperties persona) {
        if (persona != null && persona.getAutoPublishChatMaxAttemptsPerDay() != null
                && persona.getAutoPublishChatMaxAttemptsPerDay() > 0) {
            return persona.getAutoPublishChatMaxAttemptsPerDay();
        }
        return Math.max(1, botProperties.getAutoPublishChatMaxAttemptsPerDay());
    }

    Integer resolveNextUpcomingChatSlot(PersonaProperties persona,
                                        LocalDate date,
                                        LocalTime now,
                                        int chatDailyLimit) {
        int nowMinuteOfDay = toMinuteOfDay(now);
        for (Integer slot : resolveChatAutoPublishSlots(persona, date, chatDailyLimit)) {
            if (slot != null && slot > nowMinuteOfDay) {
                return slot;
            }
        }
        return null;
    }

    List<AutoPublishCandidate> resolveDueAutoPublishCandidates(PersonaProperties persona,
                                                             LocalDate date,
                                                             LocalTime now,
                                                             int handledPostToday,
                                                             int handledCommentToday,
                                                             int handledPostThisMinute,
                                                             int handledCommentThisMinute,
                                                             int handledPostRecoveryCooldown,
                                                             boolean supportsAutoComment) {
        List<AutoPublishCandidate> candidates = new ArrayList<>();
        int nowMinuteOfDay = toMinuteOfDay(now);

        int postDailyLimit = Math.max(0, botProperties.getAutoPublishPostDailyLimit());
        List<Integer> postSlots = resolveAutoPublishSlots(persona, date, MODE_POST, postDailyLimit, supportsAutoComment);
        if (isPublishMinute(postSlots, nowMinuteOfDay, handledPostToday, handledPostThisMinute, postDailyLimit)
                || shouldRecoverMissedPostAfterLastSlot(postSlots, nowMinuteOfDay, handledPostToday,
                handledPostThisMinute, handledPostRecoveryCooldown, postDailyLimit)) {
            candidates.add(new AutoPublishCandidate(MODE_POST, nowMinuteOfDay));
        }

        int commentDailyLimit = Math.max(0, botProperties.getAutoPublishCommentDailyLimit());
        List<Integer> commentSlots = resolveAutoPublishSlots(
                persona, date, MODE_COMMENT, commentDailyLimit, supportsAutoComment);
        if (isPublishMinute(commentSlots, nowMinuteOfDay, handledCommentToday, handledCommentThisMinute)) {
            candidates.add(new AutoPublishCandidate(MODE_COMMENT, nowMinuteOfDay));
        }

        candidates.sort(Comparator.comparingInt(candidate -> candidate.minuteOfDay));
        return candidates;
    }

    String buildWaitingDetail(PersonaProperties persona,
                              LocalDate date,
                              LocalTime now,
                              int handledPostToday,
                              int handledCommentToday,
                              boolean supportsAutoComment) {
        Integer nextPostSlot = resolveNextUpcomingRandomAutoPublishSlot(
                persona, date, MODE_POST, now, supportsAutoComment);
        Integer nextCommentSlot = resolveNextUpcomingRandomAutoPublishSlot(
                persona, date, MODE_COMMENT, now, supportsAutoComment);
        if (nextPostSlot == null && nextCommentSlot == null) {
            return (handledPostToday >= botProperties.getAutoPublishPostDailyLimit()
                    && handledCommentToday >= botProperties.getAutoPublishCommentDailyLimit())
                    ? "daily_limits_reached"
                    : "no_remaining_slot_today";
        }

        List<String> parts = new ArrayList<>();
        if (nextPostSlot != null && toMinuteOfDay(now) < nextPostSlot) {
            parts.add("post=" + formatMinuteOfDay(nextPostSlot));
        }
        if (nextCommentSlot != null && toMinuteOfDay(now) < nextCommentSlot) {
            parts.add("comment=" + formatMinuteOfDay(nextCommentSlot));
        }
        if (parts.isEmpty()) {
            return "waiting_next_due_slot";
        }
        return "waiting_random_slot:" + String.join(",", parts);
    }

    Integer resolveNextUpcomingRandomAutoPublishSlot(PersonaProperties persona,
                                                     LocalDate date,
                                                     String mode,
                                                     LocalTime now,
                                                     boolean supportsAutoComment) {
        List<Integer> slots = resolveAutoPublishSlots(
                persona,
                date,
                mode,
                MODE_POST.equals(mode)
                        ? botProperties.getAutoPublishPostDailyLimit()
                        : botProperties.getAutoPublishCommentDailyLimit(),
                supportsAutoComment
        );
        int nowMinuteOfDay = toMinuteOfDay(now);
        for (Integer slot : slots) {
            if (slot != null && slot > nowMinuteOfDay) {
                return slot;
            }
        }
        return null;
    }

    int toMinuteOfDay(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    boolean isPublishMinute(List<Integer> slots,
                            int nowMinuteOfDay,
                            int handledToday,
                            int handledThisMinute) {
        if (slots == null || slots.isEmpty() || handledThisMinute > 0) {
            return false;
        }
        int dueSlotCount = countSlotsAtOrBeforeMinute(slots, nowMinuteOfDay);
        if (botProperties.isAutoPublishCatchUpEnabled()) {
            return dueSlotCount > 0 && handledToday < dueSlotCount;
        }
        return hasSlotAtMinute(slots, nowMinuteOfDay) && handledToday < dueSlotCount;
    }

    boolean isPublishMinute(List<Integer> slots,
                            int nowMinuteOfDay,
                            int handledToday,
                            int handledThisMinute,
                            int dailyLimit) {
        if (slots == null || slots.isEmpty() || dailyLimit <= 0 || handledThisMinute > 0) {
            return false;
        }
        int dueSlotCount = countSlotsAtOrBeforeMinute(slots, nowMinuteOfDay);
        if (botProperties.isAutoPublishCatchUpEnabled()) {
            return hasSlotAtMinute(slots, nowMinuteOfDay)
                    && handledToday < Math.min(Math.max(0, dailyLimit), dueSlotCount);
        }
        return hasSlotAtMinute(slots, nowMinuteOfDay) && handledToday < dueSlotCount;
    }

    List<Integer> resolveAutoPublishSlots(PersonaProperties persona,
                                          LocalDate date,
                                          String mode,
                                          int dailyLimit,
                                          boolean supportsAutoComment) {
        if (persona == null || date == null || !StringUtils.hasText(mode) || dailyLimit <= 0) {
            return Collections.emptyList();
        }
        if (MODE_COMMENT.equals(mode) && !supportsAutoComment) {
            return Collections.emptyList();
        }

        List<Integer> primarySlots = botProperties.buildDailyAutoPublishSlots(
                date,
                mode,
                dailyLimit,
                persona.getBoardTitle(),
                persona.getName()
        );
        if (!MODE_POST.equals(mode) || !botProperties.isAutoPublishCatchUpEnabled()) {
            return primarySlots;
        }

        int retrySlots = Math.max(0, botProperties.getAutoPublishCatchUpPostRetrySlots());
        if (retrySlots <= 0 || primarySlots.isEmpty()) {
            return primarySlots;
        }

        int lastPrimarySlot = primarySlots.get(primarySlots.size() - 1);
        if (lastPrimarySlot >= (24 * 60) - 1) {
            return primarySlots;
        }

        List<Integer> catchUpSlots = botProperties.buildDailyAutoPublishSlotsInRange(
                date,
                mode + "_catchup",
                retrySlots,
                persona.getBoardTitle(),
                persona.getName(),
                lastPrimarySlot + 1,
                (24 * 60) - 1
        );
        if (catchUpSlots.isEmpty()) {
            return primarySlots;
        }

        List<Integer> combinedSlots = new ArrayList<>(primarySlots);
        combinedSlots.addAll(catchUpSlots);
        Collections.sort(combinedSlots);
        return combinedSlots;
    }

    List<String> formatMinuteSlots(List<Integer> slots) {
        if (slots == null || slots.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> formatted = new ArrayList<>();
        for (Integer slot : slots) {
            if (slot == null) {
                continue;
            }
            formatted.add(formatMinuteOfDay(slot));
        }
        return formatted;
    }

    String formatMinuteOfDay(int minuteOfDay) {
        return String.format("%02d:%02d", minuteOfDay / 60, minuteOfDay % 60);
    }

    private int countSlotsAtOrBeforeMinute(List<Integer> slots, int minuteOfDay) {
        int count = 0;
        for (Integer slot : slots) {
            if (slot != null && slot <= minuteOfDay) {
                count++;
            }
        }
        return count;
    }

    private boolean hasSlotAtMinute(List<Integer> slots, int minuteOfDay) {
        for (Integer slot : slots) {
            if (slot != null && slot == minuteOfDay) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldRecoverMissedPostAfterLastSlot(List<Integer> slots,
                                                         int nowMinuteOfDay,
                                                         int handledToday,
                                                         int handledThisMinute,
                                                         int handledRecoveryCooldown,
                                                         int dailyLimit) {
        if (!botProperties.isAutoPublishCatchUpEnabled()
                || slots == null
                || slots.isEmpty()
                || dailyLimit <= 0
                || handledThisMinute > 0
                || handledToday >= dailyLimit
                || handledRecoveryCooldown > 0) {
            return false;
        }
        Integer lastSlot = slots.get(slots.size() - 1);
        return lastSlot != null && nowMinuteOfDay > lastSlot;
    }

    static final class AutoPublishCandidate {
        final String mode;
        final int minuteOfDay;

        AutoPublishCandidate(String mode, int minuteOfDay) {
            this.mode = mode;
            this.minuteOfDay = minuteOfDay;
        }
    }
}
