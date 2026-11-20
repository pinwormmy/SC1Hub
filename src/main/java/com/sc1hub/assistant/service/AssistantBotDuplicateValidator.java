package com.sc1hub.assistant.service;

import com.sc1hub.assistant.config.AssistantBotProperties;
import com.sc1hub.assistant.dto.AssistantBotHistoryDTO;
import com.sc1hub.assistant.support.AssistantTextUtils;
import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.dto.CommentDTO;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * AI 봇 생성 후보의 최근 글/댓글/이력 대비 유사도, 제목 오프닝 중복, 핵심 단어 겹침을 검증한다.
 */
public class AssistantBotDuplicateValidator {

    private static final String MODE_POST = "post";
    private static final String MODE_COMMENT = "comment";
    private static final Pattern NON_TEXT_PATTERN = Pattern.compile("[^가-힣a-z0-9]");
    private static final Pattern TITLE_COMMENT_COUNT_PATTERN = Pattern.compile("\\s*\\(\\s*\\d+\\s*\\)\\s*$");

    private final AssistantBotProperties botProperties;

    public AssistantBotDuplicateValidator(AssistantBotProperties botProperties) {
        this.botProperties = botProperties;
    }

    DuplicateCheck findDuplicateIssue(String mode,
                                       String title,
                                       String body,
                                       List<BoardDTO> recentPosts,
                                       List<CommentDTO> recentComments,
                                       List<AssistantBotHistoryDTO> recentHistory) {
        double threshold = clampThreshold(botProperties.getDuplicateSimilarityThreshold());
        String normalizedTitle = normalizeText(title);
        String normalizedBody = normalizeText(body);

        for (AssistantBotHistoryDTO history : safeList(recentHistory)) {
            if (history == null) {
                continue;
            }
            String historyMode = normalizeMode(history.getGenerationMode());
            if (MODE_POST.equals(mode) && MODE_POST.equals(historyMode)) {
                if (hasRepeatedTitleOpening(title, history.getDraftTitle())) {
                    return DuplicateCheck.of("최근 봇 게시글 제목 첫머리 패턴이 겹칩니다.");
                }
                if (isTooSimilar(normalizedTitle, normalizeText(history.getDraftTitle()), threshold)) {
                    return DuplicateCheck.of("최근 봇 게시글 제목과 너무 비슷합니다.");
                }
                if (isTooSimilar(normalizedBody, normalizeText(history.getDraftBody()), threshold)) {
                    return DuplicateCheck.of("최근 봇 게시글 본문과 너무 비슷합니다.");
                }
            }
            if (MODE_COMMENT.equals(mode) && MODE_COMMENT.equals(historyMode)) {
                if (isTooSimilar(normalizedBody, normalizeText(history.getDraftBody()), threshold)) {
                    return DuplicateCheck.of("최근 봇 댓글과 너무 비슷합니다.");
                }
            }
        }

        if (MODE_POST.equals(mode)) {
            for (BoardDTO post : safeList(recentPosts)) {
                if (post == null) {
                    continue;
                }
                if (hasRepeatedTitleOpening(title, post.getTitle())) {
                    return DuplicateCheck.of("최근 게시글 제목 첫머리 패턴이 겹칩니다.");
                }
                if (isTooSimilar(normalizedTitle, normalizeText(post.getTitle()), threshold)) {
                    return DuplicateCheck.of("최근 게시글 제목과 너무 비슷합니다.");
                }
                if (isTooSimilar(normalizedBody,
                        normalizeText(AssistantTextUtils.stripHtmlToText(post.getContent())),
                        Math.min(0.82, threshold + 0.08))) {
                    return DuplicateCheck.of("최근 게시글 본문 전개와 너무 비슷합니다.");
                }
            }
        } else {
            for (CommentDTO comment : safeList(recentComments)) {
                if (comment == null) {
                    continue;
                }
                if (isTooSimilar(normalizedBody,
                        normalizeText(AssistantTextUtils.stripHtmlToText(comment.getContent())),
                        threshold)) {
                    return DuplicateCheck.of("최근 댓글과 너무 비슷합니다.");
                }
            }
        }
        return DuplicateCheck.notDuplicate();
    }

    boolean isTooSimilar(String left, String right, double threshold) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        if (left.equals(right)) {
            return true;
        }
        if (left.length() >= 8 && right.contains(left)) {
            return true;
        }
        if (right.length() >= 8 && left.contains(right)) {
            return true;
        }
        return trigramSimilarity(left, right) >= threshold;
    }

    boolean hasRepeatedTitleOpening(String leftTitle, String rightTitle) {
        String left = normalizeTitleForOpeningCompare(leftTitle);
        String right = normalizeTitleForOpeningCompare(rightTitle);
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }

        String leftTokenPrefix = buildLeadingTokenKey(left, 2);
        String rightTokenPrefix = buildLeadingTokenKey(right, 2);
        if (StringUtils.hasText(leftTokenPrefix)
                && Objects.equals(leftTokenPrefix, rightTokenPrefix)
                && normalizeText(leftTokenPrefix).length() >= 4) {
            return true;
        }

        return sharedPrefixLength(normalizeText(left), normalizeText(right)) >= 6;
    }

    double clampThreshold(double threshold) {
        return Math.max(0.45, Math.min(threshold, 0.95));
    }

    String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String lowered = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        return NON_TEXT_PATTERN.matcher(lowered).replaceAll("");
    }

    String normalizeTitleForOpeningCompare(String title) {
        if (!StringUtils.hasText(title)) {
            return "";
        }
        String trimmed = TITLE_COMMENT_COUNT_PATTERN.matcher(title.trim()).replaceAll("");
        return trimmed.replaceAll("\\s+", " ").trim();
    }

    String extractTitleOpeningHint(String title) {
        String normalized = normalizeTitleForOpeningCompare(title);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String[] tokens = normalized.split(" ");
        if (tokens.length >= 3) {
            return String.join(" ", tokens[0], tokens[1], tokens[2]);
        }
        if (tokens.length == 2) {
            return String.join(" ", tokens[0], tokens[1]);
        }
        if (normalized.length() <= 12) {
            return normalized;
        }
        return normalized.substring(0, 12).trim();
    }

    Set<String> collectFrequentRecentTitleKeywords(List<BoardDTO> recentPosts, int limit) {
        if (recentPosts == null || recentPosts.isEmpty() || limit <= 0) {
            return Collections.emptySet();
        }
        Map<String, Integer> counts = new HashMap<>();
        List<String> firstSeenOrder = new ArrayList<>();
        for (BoardDTO post : recentPosts) {
            if (post == null) {
                continue;
            }
            for (String keyword : extractTitleKeywords(post.getTitle())) {
                counts.put(keyword, counts.getOrDefault(keyword, 0) + 1);
                if (counts.get(keyword) == 1) {
                    firstSeenOrder.add(keyword);
                }
            }
        }

        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (String keyword : firstSeenOrder) {
            if (selected.size() >= limit) {
                break;
            }
            if (counts.getOrDefault(keyword, 0) >= 2) {
                selected.add(keyword);
            }
        }
        if (!selected.isEmpty()) {
            return selected;
        }

        for (String keyword : firstSeenOrder) {
            if (selected.size() >= limit) {
                break;
            }
            selected.add(keyword);
        }
        return selected;
    }

    Set<String> extractTitleKeywords(String title) {
        String normalized = normalizeTitleForOpeningCompare(title);
        if (!StringUtils.hasText(normalized)) {
            return Collections.emptySet();
        }
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        for (String token : normalized.split("\\s+")) {
            String cleaned = normalizeText(token);
            if (!StringUtils.hasText(cleaned) || cleaned.length() < 2) {
                continue;
            }
            keywords.add(cleaned);
        }
        return keywords;
    }

    int countOverlap(Set<String> left, Set<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        int overlap = 0;
        for (String item : left) {
            if (right.contains(item)) {
                overlap++;
            }
        }
        return overlap;
    }

    private double trigramSimilarity(String left, String right) {
        Set<String> leftSet = buildNgrams(left, 3);
        Set<String> rightSet = buildNgrams(right, 3);
        if (leftSet.isEmpty() || rightSet.isEmpty()) {
            return 0.0;
        }
        int intersection = 0;
        for (String item : leftSet) {
            if (rightSet.contains(item)) {
                intersection++;
            }
        }
        int union = leftSet.size() + rightSet.size() - intersection;
        if (union <= 0) {
            return 0.0;
        }
        return (double) intersection / union;
    }

    private Set<String> buildNgrams(String text, int size) {
        Set<String> grams = new LinkedHashSet<>();
        if (!StringUtils.hasText(text)) {
            return grams;
        }
        if (text.length() <= size) {
            grams.add(text);
            return grams;
        }
        for (int i = 0; i <= text.length() - size; i++) {
            grams.add(text.substring(i, i + size));
        }
        return grams;
    }

    private String buildLeadingTokenKey(String text, int tokenCount) {
        if (!StringUtils.hasText(text) || tokenCount <= 0) {
            return "";
        }
        String[] tokens = text.trim().split("\\s+");
        if (tokens.length < tokenCount) {
            return "";
        }
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < tokenCount; i++) {
            if (StringUtils.hasText(tokens[i])) {
                selected.add(tokens[i]);
            }
        }
        return String.join(" ", selected);
    }

    private int sharedPrefixLength(String left, String right) {
        int max = Math.min(left.length(), right.length());
        int matched = 0;
        while (matched < max && left.charAt(matched) == right.charAt(matched)) {
            matched++;
        }
        return matched;
    }

    private static String normalizeMode(String mode) {
        if (!StringUtils.hasText(mode)) {
            return null;
        }
        return mode.trim().toLowerCase(Locale.ROOT);
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    static final class DuplicateCheck {
        final boolean duplicate;
        final String feedback;

        private DuplicateCheck(boolean duplicate, String feedback) {
            this.duplicate = duplicate;
            this.feedback = feedback;
        }

        static DuplicateCheck of(String feedback) {
            return new DuplicateCheck(true, feedback);
        }

        static DuplicateCheck notDuplicate() {
            return new DuplicateCheck(false, null);
        }

        boolean isDuplicate() {
            return duplicate;
        }

        String getFeedback() {
            return feedback;
        }
    }
}
