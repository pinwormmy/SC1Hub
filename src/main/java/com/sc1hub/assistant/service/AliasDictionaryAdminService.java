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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class AliasDictionaryAdminService {

    private static final String TIP_BOARD_ID = "tipboard";
    private static final String LEGACY_TARGET_MATCHUP = "matchupboards";
    private static final List<String> MATCHUP_BOARD_IDS = Arrays.asList(
            "pvspboard", "pvstboard", "pvszboard",
            "tvspboard", "tvstboard", "tvszboard",
            "zvspboard", "zvstboard", "zvszboard"
    );
    private static final List<String> SELECTABLE_BOARD_IDS = Arrays.asList(
            "pvspboard", "pvstboard", "pvszboard",
            "tvspboard", "tvstboard", "tvszboard",
            "zvspboard", "zvstboard", "zvszboard",
            TIP_BOARD_ID
    );
    private static final Set<String> SELECTABLE_BOARD_ID_SET = new LinkedHashSet<>(SELECTABLE_BOARD_IDS);
    private static final Map<String, String> BOARD_LABEL_BY_ID = createBoardLabelMap();

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

    public String formatBoardTargetsForDisplay(AliasDictionaryDTO alias) {
        List<String> targets = resolveBoardTargets(alias);
        if (targets.isEmpty()) {
            return "";
        }
        List<String> labels = new ArrayList<>();
        for (String target : targets) {
            String label = toTargetLabel(target);
            if (StringUtils.hasText(label)) {
                labels.add(label);
            }
        }
        return String.join(", ", deduplicate(labels));
    }

    public List<String> resolveBoardTargetsForForm(AliasDictionaryDTO alias) {
        return resolveBoardTargets(alias);
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
        alias.setMatchupHint(null);
        alias.setBoostBoardIds(normalizeBoostBoardIds(resolveBoardTargets(form)));
        return alias;
    }

    private String normalizeRequired(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim();
    }

    private List<String> resolveBoardTargets(AliasDictionaryFormDTO form) {
        if (form == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        addTargetsFromRawTargets(form.getBoardTargets(), targets);
        if (targets.isEmpty()) {
            addTargetsFromBoostBoardIds(form.getBoostBoardIds(), targets);
            if (StringUtils.hasText(form.getMatchupHint())) {
                targets.addAll(MATCHUP_BOARD_IDS);
            }
        }
        return new ArrayList<>(targets);
    }

    private List<String> resolveBoardTargets(AliasDictionaryDTO alias) {
        if (alias == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        addTargetsFromBoostBoardIds(alias.getBoostBoardIds(), targets);
        if (StringUtils.hasText(alias.getMatchupHint())) {
            targets.addAll(MATCHUP_BOARD_IDS);
        }
        return new ArrayList<>(targets);
    }

    private void addTargetsFromRawTargets(List<String> rawTargets, Set<String> targets) {
        if (rawTargets == null || rawTargets.isEmpty() || targets == null) {
            return;
        }
        for (String rawTarget : rawTargets) {
            addResolvedTarget(rawTarget, targets);
        }
    }

    private void addTargetsFromBoostBoardIds(String rawBoostBoardIds, Set<String> targets) {
        if (!StringUtils.hasText(rawBoostBoardIds) || targets == null) {
            return;
        }
        List<String> boardIds = parseTerms(rawBoostBoardIds);
        for (String boardId : boardIds) {
            addResolvedTarget(boardId, targets);
        }
    }

    private String normalizeBoostBoardIds(List<String> targets) {
        if (targets == null || targets.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> boardIds = new LinkedHashSet<>();
        for (String target : targets) {
            String normalizedBoardId = normalizeBoardId(target);
            if (!SELECTABLE_BOARD_ID_SET.contains(normalizedBoardId)) {
                continue;
            }
            boardIds.add(normalizedBoardId);
        }
        if (boardIds.isEmpty()) {
            return null;
        }
        List<String> ordered = new ArrayList<>(boardIds);
        try {
            return objectMapper.writeValueAsString(ordered);
        } catch (Exception e) {
            log.warn("boost_board_ids JSON 변환 실패, 텍스트 저장", e);
            return String.join(", ", ordered);
        }
    }

    private static String normalizeBoardId(String boardId) {
        if (!StringUtils.hasText(boardId)) {
            return "";
        }
        return boardId.trim().toLowerCase(Locale.ROOT);
    }

    private void addResolvedTarget(String rawTarget, Set<String> targets) {
        if (!StringUtils.hasText(rawTarget) || targets == null) {
            return;
        }
        String normalized = normalizeBoardId(rawTarget);
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        if (LEGACY_TARGET_MATCHUP.equals(normalized) || "matchupboard".equals(normalized) || "matchup".equals(normalized)) {
            targets.addAll(MATCHUP_BOARD_IDS);
            return;
        }
        if ("tip".equals(normalized) || "tips".equals(normalized)) {
            targets.add(TIP_BOARD_ID);
            return;
        }
        if (SELECTABLE_BOARD_ID_SET.contains(normalized)) {
            targets.add(normalized);
        }
    }

    private static String toTargetLabel(String target) {
        String normalized = normalizeBoardId(target);
        String label = BOARD_LABEL_BY_ID.get(normalized);
        if (StringUtils.hasText(label)) {
            return label;
        }
        return normalized;
    }

    private static Map<String, String> createBoardLabelMap() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("pvspboard", "프프전");
        labels.put("pvstboard", "프테전");
        labels.put("pvszboard", "프저전");
        labels.put("tvspboard", "테프전");
        labels.put("tvstboard", "테테전");
        labels.put("tvszboard", "테저전");
        labels.put("zvspboard", "저프전");
        labels.put("zvstboard", "저테전");
        labels.put("zvszboard", "저저전");
        labels.put(TIP_BOARD_ID, "꿀팁 게시판");
        return labels;
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
