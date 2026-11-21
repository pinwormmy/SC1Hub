package com.sc1hub.buildtimer.service;

import com.sc1hub.buildtimer.dto.BuildTimerBuildDTO;
import com.sc1hub.buildtimer.dto.BuildTimerFeedbackDTO;
import com.sc1hub.buildtimer.dto.BuildTimerFeedbackRequestDTO;
import com.sc1hub.buildtimer.mapper.BuildTimerFeedbackMapper;
import com.sc1hub.common.util.SafeTextValidator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 빌드 타이머 (beta) 사용자 피드백 검증·저장 및 관리자 조회/상태 관리 서비스.
 */
@Service
public class BuildTimerFeedbackService {

    public static final String STATUS_NEW = "NEW";
    public static final String STATUS_REVIEWED = "REVIEWED";
    public static final String STATUS_DONE = "DONE";
    public static final String COMMON_BUILD_LABEL = "전체 / 공통";

    static final Set<String> VALID_CATEGORIES = Set.of("timing", "build_request", "bug", "ux", "general");
    static final Set<String> VALID_STATUSES = Set.of(STATUS_NEW, STATUS_REVIEWED, STATUS_DONE);

    static final int MIN_MESSAGE_LENGTH = 5;
    static final int MAX_MESSAGE_LENGTH = 1000;
    static final int MAX_CONTACT_LENGTH = 100;
    static final int MAX_USER_AGENT_LENGTH = 255;
    static final int MAX_IP_LENGTH = 45;
    static final int MAX_MEMBER_TEXT_LENGTH = 50;
    static final int DEFAULT_ADMIN_LIMIT = 50;
    static final int MAX_ADMIN_LIMIT = 200;

    private final BuildTimerFeedbackMapper feedbackMapper;
    private final BuildTimerService buildTimerService;

    public BuildTimerFeedbackService(BuildTimerFeedbackMapper feedbackMapper,
                                     BuildTimerService buildTimerService) {
        this.feedbackMapper = feedbackMapper;
        this.buildTimerService = buildTimerService;
    }

    public BuildTimerFeedbackDTO submitFeedback(BuildTimerFeedbackRequestDTO request,
                                                String memberId,
                                                String nickname,
                                                String ip,
                                                String userAgent) {
        if (request == null) {
            throw new IllegalArgumentException("피드백 요청 내용이 비어 있습니다.");
        }

        String category = resolveCategory(request.getCategory());
        String message = sanitizeMessage(request.getMessage());
        int messageLength = message.codePointCount(0, message.length());
        if (messageLength < MIN_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("피드백 내용은 5자 이상 입력해주세요.");
        }
        if (messageLength > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("피드백 내용은 1000자 이하로 입력해주세요.");
        }

        String contact = normalizeContact(request.getContact());
        BuildSelection build = resolveBuildSelection(request.getBuildId());

        BuildTimerFeedbackDTO feedback = new BuildTimerFeedbackDTO();
        feedback.setBuildId(build.buildId());
        feedback.setBuildLabel(build.buildLabel());
        feedback.setCategory(category);
        feedback.setMessage(message);
        feedback.setContact(contact);
        feedback.setMemberId(normalizeMeta(memberId, MAX_MEMBER_TEXT_LENGTH));
        feedback.setNickname(normalizeMeta(nickname, MAX_MEMBER_TEXT_LENGTH));
        feedback.setIp(normalizeIp(ip));
        feedback.setUserAgent(normalizeUserAgent(userAgent));
        feedback.setStatus(STATUS_NEW);

        feedbackMapper.insertFeedback(feedback);
        return feedback;
    }

    public List<BuildTimerFeedbackDTO> getRecentFeedback(String status, int limit) {
        String normalizedStatus = normalizeOptionalStatus(status);
        int boundedLimit = limit <= 0 ? DEFAULT_ADMIN_LIMIT : Math.min(limit, MAX_ADMIN_LIMIT);
        return feedbackMapper.selectRecentFeedback(normalizedStatus, boundedLimit);
    }

    public int countFeedback(String status) {
        String normalizedStatus = normalizeOptionalStatus(status);
        return feedbackMapper.countFeedback(normalizedStatus);
    }

    public boolean updateStatus(long id, String status) {
        if (id <= 0) {
            throw new IllegalArgumentException("올바르지 않은 피드백 번호입니다.");
        }
        String normalizedStatus = requireValidStatus(status);
        return feedbackMapper.updateStatus(id, normalizedStatus) > 0;
    }

    public boolean deleteFeedback(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("올바르지 않은 피드백 번호입니다.");
        }
        return feedbackMapper.deleteFeedback(id) > 0;
    }

    static String resolveCategory(String rawCategory) {
        if (!StringUtils.hasText(rawCategory)) {
            return "general";
        }
        String normalized = rawCategory.trim().toLowerCase(Locale.ROOT);
        if (!VALID_CATEGORIES.contains(normalized)) {
            throw new IllegalArgumentException("올바른 피드백 분류를 선택해주세요.");
        }
        return normalized;
    }

    static String normalizeOptionalStatus(String rawStatus) {
        if (!StringUtils.hasText(rawStatus)) {
            return null;
        }
        return requireValidStatus(rawStatus);
    }

    public static String requireValidStatus(String rawStatus) {
        if (!StringUtils.hasText(rawStatus)) {
            throw new IllegalArgumentException("올바른 상태 값을 입력해주세요.");
        }
        String normalized = rawStatus.trim().toUpperCase(Locale.ROOT);
        if (!VALID_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("올바른 상태 값(NEW, REVIEWED, DONE)을 입력해주세요.");
        }
        return normalized;
    }

    private BuildSelection resolveBuildSelection(String rawBuildId) {
        if (!StringUtils.hasText(rawBuildId)) {
            return new BuildSelection(null, COMMON_BUILD_LABEL);
        }
        String trimmed = rawBuildId.trim();
        for (BuildTimerBuildDTO build : buildTimerService.getBuilds()) {
            if (trimmed.equals(build.getId())) {
                return new BuildSelection(build.getId(), build.getMatchup() + " · " + build.getName());
            }
        }
        return new BuildSelection(null, COMMON_BUILD_LABEL);
    }

    private static String sanitizeMessage(String raw) {
        if (raw == null) {
            return "";
        }
        String normalizedLineEndings = raw.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder cleaned = new StringBuilder(normalizedLineEndings.length());
        for (int i = 0; i < normalizedLineEndings.length(); i++) {
            char c = normalizedLineEndings.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\t') {
                continue;
            }
            cleaned.append(c);
        }
        return cleaned.toString().trim();
    }

    private static String normalizeContact(String rawContact) {
        if (!StringUtils.hasText(rawContact)) {
            return null;
        }
        String trimmed = rawContact.trim();
        if (SafeTextValidator.containsUnsafeTextChar(trimmed)) {
            throw new IllegalArgumentException("닉네임/연락처에 사용할 수 없는 문자가 포함되어 있습니다.");
        }
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_CONTACT_LENGTH) {
            throw new IllegalArgumentException("닉네임/연락처는 100자 이하로 입력해주세요.");
        }
        return trimmed;
    }

    private static String normalizeIp(String rawIp) {
        if (!StringUtils.hasText(rawIp)) {
            return null;
        }
        String ip = rawIp;
        int comma = ip.indexOf(',');
        if (comma >= 0) {
            ip = ip.substring(0, comma);
        }
        ip = ip.trim();
        if (ip.isEmpty()) {
            return null;
        }
        return ip.length() <= MAX_IP_LENGTH ? ip : ip.substring(0, MAX_IP_LENGTH);
    }

    private static String normalizeUserAgent(String rawUserAgent) {
        if (!StringUtils.hasText(rawUserAgent)) {
            return null;
        }
        StringBuilder cleaned = new StringBuilder(rawUserAgent.length());
        for (int i = 0; i < rawUserAgent.length(); i++) {
            char c = rawUserAgent.charAt(i);
            if (!Character.isISOControl(c)) {
                cleaned.append(c);
            }
        }
        String trimmed = cleaned.toString().trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= MAX_USER_AGENT_LENGTH ? trimmed : trimmed.substring(0, MAX_USER_AGENT_LENGTH);
    }

    private static String normalizeMeta(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private record BuildSelection(String buildId, String buildLabel) {
    }
}
