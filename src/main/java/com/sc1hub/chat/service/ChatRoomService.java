package com.sc1hub.chat.service;

import com.sc1hub.chat.config.ChatProperties;
import com.sc1hub.chat.dto.ChatMessageDTO;
import com.sc1hub.chat.dto.ChatPollResponseDTO;
import com.sc1hub.chat.mapper.ChatMapper;
import com.sc1hub.member.dto.MemberDTO;
import com.sc1hub.member.mapper.MemberMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class ChatRoomService {

    public static final String ROLE_MEMBER = "MEMBER";
    public static final String ROLE_GUEST = "GUEST";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_AI = "AI";

    private static final String SESSION_GUEST_NICKNAME = "chatGuestNickname";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_DELETION_EVENTS = 50;
    private static final int MAX_FLUSH_BATCH = 100;
    private static final int MAX_FLOOD_ENTRIES = 1000;

    private final ChatMapper chatMapper;
    private final ChatProperties chatProperties;
    private final ChatModerationService moderationService;
    private final MemberMapper memberMapper;

    private final ReentrantLock lock = new ReentrantLock();
    private final ArrayDeque<ChatMessageDTO> buffer = new ArrayDeque<>();
    private final ArrayDeque<long[]> deletionEvents = new ArrayDeque<>();
    private final AtomicLong seq = new AtomicLong(0);
    private final ConcurrentLinkedQueue<ChatMessageDTO> pendingWrites = new ConcurrentLinkedQueue<>();
    private final Map<String, Long> lastPostAt = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public ChatRoomService(ChatMapper chatMapper,
                           ChatProperties chatProperties,
                           ChatModerationService moderationService,
                           MemberMapper memberMapper) {
        this.chatMapper = chatMapper;
        this.chatProperties = chatProperties;
        this.moderationService = moderationService;
        this.memberMapper = memberMapper;
    }

    @PostConstruct
    public void init() {
        try {
            Long maxId = chatMapper.selectMaxId();
            seq.set(maxId == null ? 0L : maxId);

            List<ChatMessageDTO> recent = chatMapper.selectRecentMessages(chatProperties.getHistorySize());
            if (recent != null && !recent.isEmpty()) {
                Collections.reverse(recent);
                lock.lock();
                try {
                    for (ChatMessageDTO message : recent) {
                        message.setRegDate(formatTime(message.getCreatedAt()));
                        buffer.addLast(message);
                    }
                } finally {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            log.error("채팅 기록 초기화 중 오류 발생 (빈 채팅방으로 시작)", e);
        }
    }

    public ChatPollResponseDTO poll(long afterSeq) {
        ChatPollResponseDTO response = new ChatPollResponseDTO();
        lock.lock();
        try {
            List<ChatMessageDTO> messages = new ArrayList<>();
            for (ChatMessageDTO message : buffer) {
                if (message.getId() > afterSeq && !message.isDeleted()) {
                    messages.add(message);
                }
            }
            List<Long> deletedIds = new ArrayList<>();
            for (long[] event : deletionEvents) {
                if (event[0] > afterSeq) {
                    deletedIds.add(event[1]);
                }
            }
            response.setMessages(messages);
            response.setDeletedIds(deletedIds);
            response.setLastSeq(seq.get());
        } finally {
            lock.unlock();
        }
        return response;
    }

    public ChatMessageDTO postUserMessage(MemberDTO member, HttpSession session, String ip, String content) {
        String trimmed = content == null ? "" : content.trim();
        if (!StringUtils.hasText(trimmed)) {
            throw new ChatRejectedException(HttpStatus.BAD_REQUEST, "메시지를 입력해주세요.");
        }
        if (trimmed.length() > chatProperties.getMaxMessageLength()) {
            throw new ChatRejectedException(HttpStatus.BAD_REQUEST,
                    "메시지는 " + chatProperties.getMaxMessageLength() + "자 이내로 입력해주세요.");
        }

        String memberId = member == null ? null : member.getId();
        String restriction = moderationService.checkRestricted(memberId, ip);
        if (restriction != null) {
            throw new ChatRejectedException(HttpStatus.FORBIDDEN, restriction);
        }

        String blockedWord = moderationService.findBlockedWord(trimmed);
        if (blockedWord != null) {
            throw new ChatRejectedException(HttpStatus.BAD_REQUEST, "금지어가 포함되어 있습니다.");
        }

        checkFlood(memberId, ip);

        String nickname = resolveNickname(member, session);
        String role = resolveRole(member);
        return appendMessage(memberId, nickname, role, ip, trimmed);
    }

    public ChatMessageDTO postPublicAiQuestion(String memberId, String nickname, String role, String ip, String question) {
        String content = "/ai " + (question == null ? "" : question.trim());
        if (content.length() > chatProperties.getAiMaxMessageLength()) {
            content = content.substring(0, chatProperties.getAiMaxMessageLength());
        }
        return appendMessage(memberId, nickname, role, ip, content);
    }

    public ChatMessageDTO postAiMessage(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (!StringUtils.hasText(trimmed)) {
            return null;
        }
        if (trimmed.length() > chatProperties.getAiMaxMessageLength()) {
            trimmed = trimmed.substring(0, chatProperties.getAiMaxMessageLength());
        }
        return appendMessage(null, chatProperties.getAiNickname(), ROLE_AI, null, trimmed);
    }

    public String resolveNickname(MemberDTO member, HttpSession session) {
        if (member != null && StringUtils.hasText(member.getNickName())) {
            return member.getNickName();
        }
        if (session == null) {
            return "Guest";
        }
        Object existing = session.getAttribute(SESSION_GUEST_NICKNAME);
        if (existing instanceof String && StringUtils.hasText((String) existing)) {
            return (String) existing;
        }
        String nickname = generateGuestNickname();
        session.setAttribute(SESSION_GUEST_NICKNAME, nickname);
        return nickname;
    }

    public String resolveRole(MemberDTO member) {
        if (member == null) {
            return ROLE_GUEST;
        }
        return member.getGrade() == 3 ? ROLE_ADMIN : ROLE_MEMBER;
    }

    public ChatMessageDTO findLatestByNickname(String nickname) {
        if (!StringUtils.hasText(nickname)) {
            return null;
        }
        lock.lock();
        try {
            Iterator<ChatMessageDTO> iterator = buffer.descendingIterator();
            while (iterator.hasNext()) {
                ChatMessageDTO message = iterator.next();
                if (nickname.equals(message.getNickname())) {
                    return message;
                }
            }
        } finally {
            lock.unlock();
        }
        return null;
    }

    public boolean deleteMessage(long messageId) {
        boolean found = false;
        lock.lock();
        try {
            for (ChatMessageDTO message : buffer) {
                if (message.getId() == messageId) {
                    if (message.isDeleted()) {
                        return true;
                    }
                    message.setDeleted(true);
                    found = true;
                    break;
                }
            }
            if (found) {
                deletionEvents.addLast(new long[]{seq.incrementAndGet(), messageId});
                while (deletionEvents.size() > MAX_DELETION_EVENTS) {
                    deletionEvents.pollFirst();
                }
            }
        } finally {
            lock.unlock();
        }

        try {
            // Also covers messages that already fell out of the in-memory buffer.
            chatMapper.markDeleted(messageId);
        } catch (Exception e) {
            log.error("채팅 메시지 삭제 반영 중 오류 발생. messageId={}", messageId, e);
        }
        return found;
    }

    @Scheduled(fixedDelay = 5000)
    public void flushPendingWrites() {
        flush();
    }

    @PreDestroy
    public void shutdownFlush() {
        flush();
    }

    private void flush() {
        if (pendingWrites.isEmpty()) {
            return;
        }
        List<ChatMessageDTO> batch = new ArrayList<>();
        ChatMessageDTO next;
        while (batch.size() < MAX_FLUSH_BATCH && (next = pendingWrites.poll()) != null) {
            batch.add(next);
        }
        if (batch.isEmpty()) {
            return;
        }
        try {
            chatMapper.insertMessages(batch);
        } catch (Exception e) {
            // Drop the batch rather than re-queueing forever: a poison message would
            // otherwise block every following flush. Chat delivery is unaffected.
            log.error("채팅 메시지 저장 중 오류 발생. {}건 유실", batch.size(), e);
        }
    }

    private ChatMessageDTO appendMessage(String memberId, String nickname, String role, String ip, String content) {
        ChatMessageDTO message = new ChatMessageDTO();
        message.setMemberId(memberId);
        message.setNickname(nickname);
        message.setRole(role);
        message.setContent(content);
        message.setIp(ip);
        message.setCreatedAt(LocalDateTime.now());
        message.setRegDate(formatTime(message.getCreatedAt()));

        lock.lock();
        try {
            message.setId(seq.incrementAndGet());
            buffer.addLast(message);
            while (buffer.size() > chatProperties.getBufferSize()) {
                buffer.pollFirst();
            }
        } finally {
            lock.unlock();
        }

        pendingWrites.add(message);
        return message;
    }

    private void checkFlood(String memberId, String ip) {
        String key = StringUtils.hasText(memberId) ? "member:" + memberId : "ip:" + ip;
        long now = System.currentTimeMillis();
        Long previous = lastPostAt.put(key, now);
        if (previous != null && now - previous < chatProperties.getMinIntervalMillis()) {
            lastPostAt.put(key, previous);
            throw new ChatRejectedException(HttpStatus.TOO_MANY_REQUESTS, "너무 빠르게 입력하고 있습니다. 잠시 후 다시 시도해주세요.");
        }
        if (lastPostAt.size() > MAX_FLOOD_ENTRIES) {
            long cutoff = now - 60_000L;
            lastPostAt.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        }
    }

    private String generateGuestNickname() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = "Guest" + (1000 + random.nextInt(9000));
            try {
                if ("0".equals(memberMapper.isUniqueNickName(candidate))) {
                    return candidate;
                }
            } catch (Exception e) {
                log.warn("게스트 닉네임 중복 확인 실패, 후보를 그대로 사용합니다.", e);
                return candidate;
            }
        }
        return "Guest" + (1000 + random.nextInt(9000));
    }

    private static String formatTime(LocalDateTime dateTime) {
        return dateTime == null ? "" : dateTime.format(TIME_FORMAT);
    }
}
