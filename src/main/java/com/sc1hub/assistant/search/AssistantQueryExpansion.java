package com.sc1hub.assistant.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.dto.AliasDictionaryDTO;
import com.sc1hub.assistant.support.AssistantTextUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AssistantQueryExpansion {

    private static final int MAX_EXPANDED_TERMS = 15;

    private final ObjectMapper objectMapper;

    public AssistantQueryExpansion(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<String> expand(List<String> keywords,
                               List<AliasDictionaryDTO> matchedAliases,
                               String matchupTag,
                               String matchupKoreanTag) {
        LinkedHashSet<String> expanded = new LinkedHashSet<>();
        if (keywords != null) {
            for (String keyword : keywords) {
                addNormalized(expanded, keyword);
            }
        }

        if (matchedAliases != null) {
            for (AliasDictionaryDTO alias : matchedAliases) {
                if (alias == null) {
                    continue;
                }
                addParsedTerms(expanded, alias.getCanonicalTerms());
            }
        }

        addNormalized(expanded, matchupTag);
        addNormalized(expanded, matchupKoreanTag);

        List<String> result = new ArrayList<>();
        for (String term : expanded) {
            if (result.size() >= MAX_EXPANDED_TERMS) {
                break;
            }
            result.add(term);
        }
        return result;
    }

    private void addParsedTerms(Set<String> target, String raw) {
        if (!StringUtils.hasText(raw) || target == null) {
            return;
        }
        List<String> terms = parseTerms(raw);
        for (String term : terms) {
            addNormalized(target, term);
            addSplitTokens(target, term);
        }
    }

    private void addSplitTokens(Set<String> target, String raw) {
        if (!StringUtils.hasText(raw) || target == null) {
            return;
        }
        String normalized = raw.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        for (String token : normalized.split("\\s+")) {
            addNormalized(target, token);
        }
    }

    private void addNormalized(Set<String> target, String raw) {
        if (!StringUtils.hasText(raw) || target == null) {
            return;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < 2) {
            return;
        }
        target.add(normalized);
    }

    private List<String> parseTerms(String raw) {
        return AssistantTextUtils.parseTerms(raw, objectMapper);
    }
}
