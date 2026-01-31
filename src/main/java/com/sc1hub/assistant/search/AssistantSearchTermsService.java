package com.sc1hub.assistant.search;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.dto.AliasDictionaryDTO;
import com.sc1hub.assistant.mapper.AliasDictionaryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AssistantSearchTermsService {

    private static final Pattern TOKEN_SPLITTER = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final int MIN_TOKEN_LENGTH = 2;
    private static final int MAX_TERMS_LENGTH = 4000;
    private static final long ALIAS_CACHE_MILLIS = 60_000L;

    private final AliasDictionaryMapper aliasDictionaryMapper;
    private final ObjectMapper objectMapper;

    private volatile List<AliasDictionaryDTO> cachedAliases = Collections.emptyList();
    private volatile long cachedAtMillis = 0L;

    public AssistantSearchTermsService(AliasDictionaryMapper aliasDictionaryMapper, ObjectMapper objectMapper) {
        this.aliasDictionaryMapper = aliasDictionaryMapper;
        this.objectMapper = objectMapper;
    }

    public String buildSearchTerms(String title, String content) {
        String safeTitle = title == null ? "" : title;
        String safeContent = content == null ? "" : stripHtmlToText(content);
        String combined = (safeTitle + " " + safeContent).trim();

        LinkedHashSet<String> terms = new LinkedHashSet<>();
        addTokens(combined, terms);

        String matchText = normalizeForMatch(combined);
        String compactMatchText = matchText.replaceAll("\\s+", "");

        List<AliasDictionaryDTO> aliases = loadAliases();
        if (aliases != null) {
            for (AliasDictionaryDTO alias : aliases) {
                if (alias == null || !StringUtils.hasText(alias.getAlias())) {
                    continue;
                }
                String normalizedAlias = normalizeForMatch(alias.getAlias());
                if (!StringUtils.hasText(normalizedAlias)) {
                    continue;
                }
                boolean matched;
                if (normalizedAlias.contains(" ")) {
                    matched = matchText.contains(normalizedAlias);
                } else {
                    matched = compactMatchText.contains(normalizedAlias.replaceAll("\\s+", ""));
                }
                if (!matched) {
                    continue;
                }
                addParsedTerms(alias.getCanonicalTerms(), terms);
                addParsedTerms(alias.getMatchupHint(), terms);
            }
        }

        if (terms.isEmpty()) {
            return "";
        }

        String joined = String.join(" ", terms);
        if (joined.length() <= MAX_TERMS_LENGTH) {
            return joined;
        }
        return joined.substring(0, MAX_TERMS_LENGTH);
    }

    private List<AliasDictionaryDTO> loadAliases() {
        long now = System.currentTimeMillis();
        if (now - cachedAtMillis < ALIAS_CACHE_MILLIS && cachedAliases != null && !cachedAliases.isEmpty()) {
            return cachedAliases;
        }
        synchronized (this) {
            if (now - cachedAtMillis < ALIAS_CACHE_MILLIS && cachedAliases != null && !cachedAliases.isEmpty()) {
                return cachedAliases;
            }
            try {
                List<AliasDictionaryDTO> aliases = aliasDictionaryMapper.selectAll();
                if (aliases == null || aliases.isEmpty()) {
                    cachedAliases = Collections.emptyList();
                } else {
                    cachedAliases = aliases;
                }
            } catch (Exception e) {
                log.warn("alias_dictionary 로드 실패", e);
                return cachedAliases == null ? Collections.emptyList() : cachedAliases;
            } finally {
                cachedAtMillis = now;
            }
            return cachedAliases;
        }
    }

    private void addTokens(String text, Set<String> terms) {
        if (!StringUtils.hasText(text) || terms == null) {
            return;
        }
        String normalized = TOKEN_SPLITTER.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        String[] tokens = normalized.split("\\s+");
        for (String token : tokens) {
            addNormalizedTerm(token, terms);
        }
    }

    private void addParsedTerms(String raw, Set<String> terms) {
        if (!StringUtils.hasText(raw) || terms == null) {
            return;
        }
        List<String> parsed = parseTerms(raw);
        for (String term : parsed) {
            addNormalizedTerm(term, terms);
            addTokens(term, terms);
        }
    }

    private void addNormalizedTerm(String term, Set<String> terms) {
        if (!StringUtils.hasText(term) || terms == null) {
            return;
        }
        String normalized = term.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < MIN_TOKEN_LENGTH) {
            return;
        }
        terms.add(normalized);
    }

    private List<String> parseTerms(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (!StringUtils.hasText(trimmed)) {
            return Collections.emptyList();
        }
        if (looksLikeJsonArray(trimmed)) {
            try {
                return objectMapper.readValue(trimmed, new TypeReference<List<String>>() {});
            } catch (Exception ignored) {
                // Fallback to text parsing
            }
        }
        String[] tokens = trimmed.split("[,\\n\\r]+");
        List<String> results = new ArrayList<>();
        for (String token : tokens) {
            if (StringUtils.hasText(token)) {
                results.add(token.trim());
            }
        }
        return results.isEmpty() ? Collections.singletonList(trimmed) : results;
    }

    private static boolean looksLikeJsonArray(String value) {
        String trimmed = value.trim();
        return trimmed.startsWith("[") && trimmed.endsWith("]");
    }

    private static String normalizeForMatch(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String stripped = stripHtmlToText(text);
        return stripped.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String stripHtmlToText(String html) {
        if (html == null) {
            return "";
        }
        String text = html.replaceAll("(?s)<[^>]*>", " ");
        text = text.replace("&nbsp;", " ");
        text = text.replace("&amp;", "&");
        text = text.replace("&lt;", "<");
        text = text.replace("&gt;", ">");
        text = text.replace("&quot;", "\"");
        text = text.replace("&#39;", "'");
        return text.replaceAll("\\s+", " ").trim();
    }
}
