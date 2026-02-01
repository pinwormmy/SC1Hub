package com.sc1hub.assistant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.dto.AliasDictionaryDTO;
import com.sc1hub.assistant.dto.AliasDictionaryFormDTO;
import com.sc1hub.assistant.mapper.AliasDictionaryMapper;
import com.sc1hub.assistant.search.AssistantQueryParser;
import com.sc1hub.assistant.search.AssistantSearchTermsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@Slf4j
public class AliasDictionaryAdminService {

    private final AliasDictionaryMapper aliasDictionaryMapper;
    private final ObjectMapper objectMapper;
    private final AssistantSearchTermsService searchTermsService;
    private final AssistantQueryParser queryParser;

    public AliasDictionaryAdminService(AliasDictionaryMapper aliasDictionaryMapper,
                                       ObjectMapper objectMapper,
                                       AssistantSearchTermsService searchTermsService,
                                       AssistantQueryParser queryParser) {
        this.aliasDictionaryMapper = aliasDictionaryMapper;
        this.objectMapper = objectMapper;
        this.searchTermsService = searchTermsService;
        this.queryParser = queryParser;
    }

    public List<AliasDictionaryDTO> list(String keyword) {
        if (StringUtils.hasText(keyword)) {
            return aliasDictionaryMapper.search(keyword.trim());
        }
        return aliasDictionaryMapper.selectAll();
    }

    public AliasDictionaryDTO findById(long id) {
        return aliasDictionaryMapper.selectById(id);
    }

    public AliasDictionaryDTO create(AliasDictionaryFormDTO form) {
        AliasDictionaryDTO alias = toAliasDictionary(form);
        int inserted = aliasDictionaryMapper.insert(alias);
        if (inserted <= 0) {
            log.warn("alias_dictionary insert 결과가 비정상입니다. count={}", inserted);
        }
        invalidateAliasCaches();
        return alias;
    }

    public boolean update(AliasDictionaryFormDTO form) {
        AliasDictionaryDTO alias = toAliasDictionary(form);
        int updated = aliasDictionaryMapper.update(alias);
        if (updated > 0) {
            invalidateAliasCaches();
            return true;
        }
        return false;
    }

    public boolean delete(long id) {
        int deleted = aliasDictionaryMapper.delete(id);
        if (deleted > 0) {
            invalidateAliasCaches();
            return true;
        }
        return false;
    }

    public String formatTermsForInput(String raw) {
        List<String> terms = parseTerms(raw);
        if (terms.isEmpty()) {
            return "";
        }
        return String.join("\n", terms);
    }

    public String formatTermsForDisplay(String raw) {
        List<String> terms = parseTerms(raw);
        if (terms.isEmpty()) {
            return "";
        }
        return String.join(", ", terms);
    }

    public String normalizeCanonicalTerms(String raw) {
        List<String> terms = parseTerms(raw);
        if (terms.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(terms);
        } catch (Exception e) {
            log.warn("canonical_terms JSON 변환 실패, 원문 저장", e);
            return String.join(", ", terms);
        }
    }

    private AliasDictionaryDTO toAliasDictionary(AliasDictionaryFormDTO form) {
        AliasDictionaryDTO alias = new AliasDictionaryDTO();
        if (form.getId() != null) {
            alias.setId(form.getId());
        }
        alias.setAlias(normalizeRequired(form.getAlias()));
        alias.setCanonicalTerms(normalizeCanonicalTerms(form.getCanonicalTerms()));
        alias.setMatchupHint(normalizeOptional(form.getMatchupHint()));
        alias.setBoostBoardIds(normalizeOptional(form.getBoostBoardIds()));
        return alias;
    }

    private String normalizeRequired(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private List<String> parseTerms(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (!StringUtils.hasText(trimmed)) {
            return Collections.emptyList();
        }
        List<String> terms = new ArrayList<>();
        if (looksLikeJsonArray(trimmed)) {
            try {
                List<String> parsed = objectMapper.readValue(trimmed, new TypeReference<List<String>>() {});
                for (String term : parsed) {
                    addTerm(terms, term);
                }
                return deduplicate(terms);
            } catch (Exception e) {
                log.debug("alias term JSON 파싱 실패: {}", trimmed, e);
            }
        }
        String[] tokens = trimmed.split("[,\n\r]+");
        for (String token : tokens) {
            addTerm(terms, token);
        }
        if (terms.isEmpty()) {
            addTerm(terms, trimmed);
        }
        return deduplicate(terms);
    }

    private void addTerm(List<String> terms, String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        String normalized = token.trim();
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        terms.add(normalized);
    }

    private List<String> deduplicate(List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String term : terms) {
            if (StringUtils.hasText(term)) {
                set.add(term.trim());
            }
        }
        return new ArrayList<>(set);
    }

    private static boolean looksLikeJsonArray(String value) {
        String trimmed = value.trim();
        return trimmed.startsWith("[") && trimmed.endsWith("]");
    }

    private void invalidateAliasCaches() {
        searchTermsService.invalidateAliasCache();
        queryParser.invalidateAliasCache();
    }
}
