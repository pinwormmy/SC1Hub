package com.sc1hub.assistant.search;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.dto.AliasDictionaryDTO;
import com.sc1hub.assistant.mapper.AliasDictionaryMapper;
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
import java.util.regex.Pattern;

@Service
@Slf4j
public class AssistantQueryParser {

    private static final String BOARD_PVP = "pvspboard";
    private static final String BOARD_PVT = "pvstboard";
    private static final String BOARD_PVZ = "pvszboard";
    private static final String BOARD_TVP = "tvspboard";
    private static final String BOARD_TVT = "tvstboard";
    private static final String BOARD_TVZ = "tvszboard";
    private static final String BOARD_ZVP = "zvspboard";
    private static final String BOARD_ZVT = "zvstboard";
    private static final String BOARD_ZVZ = "zvszboard";

    private static final double MATCHUP_WEIGHT = 1.6;
    private static final double ALIAS_BOARD_WEIGHT = 1.4;
    private static final long ALIAS_CACHE_MILLIS = 60_000L;

    private static final Set<String> STOPWORDS = new LinkedHashSet<>(Arrays.asList(
            "추천", "질문", "방법", "어떻게", "알려줘", "알려주세요", "알려", "해줘", "해주세요", "좀",
            "빌드", "빌드오더", "운영", "공략", "강의",
            "recommend", "recommendation", "how", "why", "what", "where", "please", "help"
    ));
    private static final List<String> KOREAN_PARTICLE_SUFFIXES = Arrays.asList(
            "으로부터", "로부터", "에게서", "한테서",
            "으로써", "로써", "으로서", "로서",
            "에서", "에게", "께서", "께", "한테",
            "부터", "까지", "으로", "로",
            "의", "은", "는", "이", "가", "을", "를", "과", "와", "도", "만", "에"
    );

    private static final Pattern TOKEN_SPLITTER = Pattern.compile("[^\\p{L}\\p{N}]+");

    private final AliasDictionaryMapper aliasDictionaryMapper;
    private final ObjectMapper objectMapper;
    private final AssistantQueryExpansion queryExpansion;

    private volatile List<AliasDictionaryDTO> cachedAliases = Collections.emptyList();
    private volatile long cachedAtMillis = 0L;

    public AssistantQueryParser(AliasDictionaryMapper aliasDictionaryMapper,
                                ObjectMapper objectMapper,
                                AssistantQueryExpansion queryExpansion) {
        this.aliasDictionaryMapper = aliasDictionaryMapper;
        this.objectMapper = objectMapper;
        this.queryExpansion = queryExpansion;
    }

    public void invalidateAliasCache() {
        cachedAliases = Collections.emptyList();
        cachedAtMillis = 0L;
    }

    public AssistantQueryParseResult parse(String message) {
        AssistantQueryParseResult result = new AssistantQueryParseResult();

        String normalizedMessage = message == null ? "" : message.trim();
        List<String> keywords = extractKeywords(normalizedMessage);
        result.setKeywords(keywords);

        MatchupInfo matchupInfo = detectMatchup(normalizedMessage);
        result.setMatchup(matchupInfo.matchupTag);
        result.setPlayerRace(matchupInfo.playerRace);
        result.setOpponentRace(matchupInfo.opponentRace);

        String intent = resolveIntent(normalizedMessage);
        result.setIntent(intent);

        List<AliasDictionaryDTO> matchedAliases = matchAliases(normalizedMessage, keywords);
        result.setAliasMatched(!matchedAliases.isEmpty());
        Map<String, Double> boardWeights = new LinkedHashMap<>();

        double confidence = matchupInfo.confidence;
        for (AliasDictionaryDTO alias : matchedAliases) {
            if (alias == null) {
                continue;
            }
            if (StringUtils.hasText(alias.getMatchupHint()) && matchupInfo.matchupTag == null) {
                MatchupInfo hinted = detectMatchup(alias.getMatchupHint());
                if (StringUtils.hasText(hinted.matchupTag)) {
                    matchupInfo = hinted;
                    result.setMatchup(hinted.matchupTag);
                    result.setPlayerRace(hinted.playerRace);
                    result.setOpponentRace(hinted.opponentRace);
                    confidence = Math.max(confidence, hinted.confidence * 0.8);
                }
            }
            for (String boardId : parseTerms(alias.getBoostBoardIds())) {
                String normalized = normalizeBoardId(boardId);
                if (!StringUtils.hasText(normalized)) {
                    continue;
                }
                boardWeights.put(normalized, Math.max(boardWeights.getOrDefault(normalized, 1.0), ALIAS_BOARD_WEIGHT));
            }
        }

        if (StringUtils.hasText(matchupInfo.boardTitle)) {
            boardWeights.put(matchupInfo.boardTitle, Math.max(boardWeights.getOrDefault(matchupInfo.boardTitle, 1.0), MATCHUP_WEIGHT));
        }

        result.setBoardWeights(boardWeights);

        List<String> expandedTerms = queryExpansion.expand(keywords, matchedAliases, matchupInfo.matchupTag, matchupInfo.matchupKoreanTag);
        result.setExpandedTerms(expandedTerms);

        if (confidence <= 0.0) {
            confidence = inferConfidence(normalizedMessage, matchupInfo, matchedAliases);
        }
        result.setConfidence(confidence);

        return result;
    }

    private String resolveIntent(String message) {
        String normalized = safeLower(message);
        if (containsAny(normalized, "빌드", "빌드오더", "build", "buildorder", "build order", "오더")) {
            return "build";
        }
        if (containsAny(normalized, "추천", "어떻게", "방법", "how", "help", "guide")) {
            return "guide";
        }
        if (containsAny(normalized, "업그레이드", "스탯", "스펙", "능력치", "stats", "upgrade")) {
            return "facts";
        }
        return "general";
    }

    private MatchupInfo detectMatchup(String message) {
        String normalized = safeLower(message);
        String compact = normalized.replaceAll("\\s+", "");

        MatchupInfo mapping = findMatchupFromTokens(compact);
        if (mapping != null) {
            mapping.confidence = 0.9;
            return mapping;
        }

        MatchupInfo ordered = findMatchupFromVsTokens(normalized);
        if (ordered != null) {
            ordered.confidence = 0.8;
            return ordered;
        }

        MatchupInfo roleBased = findMatchupFromRoleTokens(normalized);
        if (roleBased != null) {
            roleBased.confidence = 0.85;
            return roleBased;
        }

        MatchupInfo opponentBased = findMatchupFromOpponentSuffixTokens(normalized);
        if (opponentBased != null) {
            opponentBased.confidence = 0.75;
            return opponentBased;
        }

        Set<String> races = detectRaceTokens(normalized, compact);
        if (races.size() >= 2) {
            List<String> list = new ArrayList<>(races);
            String player = list.get(0);
            String opponent = list.get(1);
            MatchupInfo info = matchupFromRaces(player, opponent);
            if (info != null) {
                info.confidence = 0.6;
                return info;
            }
        }

        if (races.size() == 1) {
            MatchupInfo info = new MatchupInfo();
            info.playerRace = races.iterator().next();
            info.confidence = 0.4;
            return info;
        }

        return new MatchupInfo();
    }

    private MatchupInfo findMatchupFromRoleTokens(String normalized) {
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String[] tokens = TOKEN_SPLITTER.matcher(normalized).replaceAll(" ").trim().split("\\s+");
        if (tokens.length < 2) {
            return null;
        }
        for (int i = 0; i < tokens.length - 1; i++) {
            String playerToken = tokens[i];
            String opponentToken = tokens[i + 1];

            String playerRace = normalizeRoleRaceToken(playerToken);
            if (!StringUtils.hasText(playerRace)) {
                continue;
            }
            String opponentRace = normalizeRaceToken(opponentToken);
            if (!StringUtils.hasText(opponentRace)) {
                continue;
            }

            MatchupInfo info = matchupFromRaces(playerRace, opponentRace);
            if (info != null) {
                return info;
            }
        }
        return null;
    }

    private MatchupInfo findMatchupFromOpponentSuffixTokens(String normalized) {
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String[] tokens = TOKEN_SPLITTER.matcher(normalized).replaceAll(" ").trim().split("\\s+");
        if (tokens.length < 2) {
            return null;
        }

        String opponentRace = "";
        for (String token : tokens) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            if (!token.endsWith("전")) {
                continue;
            }
            String race = normalizeRaceToken(token);
            if (StringUtils.hasText(race)) {
                opponentRace = race;
                break;
            }
        }
        if (!StringUtils.hasText(opponentRace)) {
            return null;
        }

        for (String token : tokens) {
            String playerRace = normalizeRaceToken(token);
            if (!StringUtils.hasText(playerRace)) {
                continue;
            }
            if (playerRace.equals(opponentRace)) {
                continue;
            }
            return matchupFromRaces(playerRace, opponentRace);
        }
        return null;
    }

    private String normalizeRoleRaceToken(String token) {
        if (!StringUtils.hasText(token)) {
            return "";
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        String stripped = stripTrailingRoleParticle(normalized);
        if (!StringUtils.hasText(stripped)) {
            return "";
        }
        if (stripped.equals(normalized)) {
            return "";
        }
        return normalizeRaceToken(stripped);
    }

    private static String stripTrailingRoleParticle(String token) {
        if (!StringUtils.hasText(token)) {
            return "";
        }
        if (token.endsWith("으로") && token.length() > 2) {
            return token.substring(0, token.length() - 2);
        }
        if (token.endsWith("로") && token.length() > 1) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }

    private MatchupInfo findMatchupFromTokens(String compact) {
        if (!StringUtils.hasText(compact)) {
            return null;
        }
        if (containsAny(compact, "pvz", "pvsz", "프저", "프저전")) {
            return matchupFromRaces("P", "Z");
        }
        if (containsAny(compact, "pvt", "pvst", "프테", "프테전")) {
            return matchupFromRaces("P", "T");
        }
        if (containsAny(compact, "pvp", "프프", "프프전")) {
            return matchupFromRaces("P", "P");
        }
        if (containsAny(compact, "tvz", "tvsz", "테저", "테저전")) {
            return matchupFromRaces("T", "Z");
        }
        if (containsAny(compact, "tvt", "테테", "테테전")) {
            return matchupFromRaces("T", "T");
        }
        if (containsAny(compact, "tvp", "tvsp", "테프", "테프전")) {
            return matchupFromRaces("T", "P");
        }
        if (containsAny(compact, "zvp", "zvsp", "저프", "저프전")) {
            return matchupFromRaces("Z", "P");
        }
        if (containsAny(compact, "zvt", "zvst", "저테", "저테전")) {
            return matchupFromRaces("Z", "T");
        }
        if (containsAny(compact, "zvz", "저저", "저저전")) {
            return matchupFromRaces("Z", "Z");
        }
        return null;
    }

    private MatchupInfo findMatchupFromVsTokens(String normalized) {
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String[] tokens = TOKEN_SPLITTER.matcher(normalized).replaceAll(" ").trim().split("\\s+");
        for (int i = 0; i < tokens.length - 2; i++) {
            String leftRace = normalizeRaceToken(tokens[i]);
            String middle = tokens[i + 1];
            String rightRace = normalizeRaceToken(tokens[i + 2]);
            if (!StringUtils.hasText(leftRace) || !StringUtils.hasText(rightRace)) {
                continue;
            }
            if (isVsToken(middle)) {
                return matchupFromRaces(leftRace, rightRace);
            }
        }
        return null;
    }

    private MatchupInfo matchupFromRaces(String playerRace, String opponentRace) {
        if (!StringUtils.hasText(playerRace) || !StringUtils.hasText(opponentRace)) {
            return null;
        }
        String matchup = playerRace + "v" + opponentRace;
        MatchupInfo info = new MatchupInfo();
        info.playerRace = playerRace;
        info.opponentRace = opponentRace;
        info.matchupTag = matchup;
        info.matchupKoreanTag = toKoreanMatchup(playerRace, opponentRace);
        info.boardTitle = boardForMatchup(playerRace, opponentRace);
        return info;
    }

    private static String boardForMatchup(String player, String opponent) {
        if ("P".equals(player) && "Z".equals(opponent)) {
            return BOARD_PVZ;
        }
        if ("P".equals(player) && "T".equals(opponent)) {
            return BOARD_PVT;
        }
        if ("P".equals(player) && "P".equals(opponent)) {
            return BOARD_PVP;
        }
        if ("T".equals(player) && "Z".equals(opponent)) {
            return BOARD_TVZ;
        }
        if ("T".equals(player) && "P".equals(opponent)) {
            return BOARD_TVP;
        }
        if ("T".equals(player) && "T".equals(opponent)) {
            return BOARD_TVT;
        }
        if ("Z".equals(player) && "P".equals(opponent)) {
            return BOARD_ZVP;
        }
        if ("Z".equals(player) && "T".equals(opponent)) {
            return BOARD_ZVT;
        }
        if ("Z".equals(player) && "Z".equals(opponent)) {
            return BOARD_ZVZ;
        }
        return null;
    }

    private static String toKoreanMatchup(String player, String opponent) {
        String left = toKoreanRace(player);
        String right = toKoreanRace(opponent);
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return null;
        }
        return left + right;
    }

    private static String toKoreanRace(String race) {
        if ("P".equals(race)) {
            return "프";
        }
        if ("T".equals(race)) {
            return "테";
        }
        if ("Z".equals(race)) {
            return "저";
        }
        return null;
    }

    private Set<String> detectRaceTokens(String normalized, String compact) {
        Set<String> races = new LinkedHashSet<>();
        if (containsAny(normalized, "프로토스", "프토", "플토", "protoss", "토스")) {
            races.add("P");
        }
        if (containsAny(normalized, "테란", "terran")) {
            races.add("T");
        }
        if (containsAny(normalized, "저그", "zerg")) {
            races.add("Z");
        }
        if (containsAny(compact, "프vs", "프대", "프상대")) {
            races.add("P");
        }
        if (containsAny(compact, "테vs", "테대", "테상대")) {
            races.add("T");
        }
        if (containsAny(compact, "저vs", "저대", "저상대")) {
            races.add("Z");
        }
        return races;
    }

    private String normalizeRaceToken(String token) {
        if (!StringUtils.hasText(token)) {
            return "";
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        normalized = stripTrailingMatchupSuffix(normalized);
        if (Arrays.asList("프로토스", "프토", "플토", "protoss", "토스", "프").contains(normalized)) {
            return "P";
        }
        if (Arrays.asList("테란", "terran", "테").contains(normalized)) {
            return "T";
        }
        if (Arrays.asList("저그", "zerg", "저").contains(normalized)) {
            return "Z";
        }
        return "";
    }

    private static String stripTrailingMatchupSuffix(String token) {
        if (!StringUtils.hasText(token)) {
            return "";
        }
        if (token.endsWith("전") && token.length() > 1) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }

    private static boolean isVsToken(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        String normalized = token.toLowerCase(Locale.ROOT);
        return Arrays.asList("vs", "대", "상대", "상대로").contains(normalized);
    }

    private double inferConfidence(String message, MatchupInfo matchupInfo, List<AliasDictionaryDTO> aliases) {
        if (StringUtils.hasText(matchupInfo.matchupTag)) {
            return Math.max(0.6, matchupInfo.confidence);
        }
        if (aliases != null && !aliases.isEmpty()) {
            return 0.55;
        }
        if (StringUtils.hasText(message)) {
            return 0.3;
        }
        return 0.2;
    }

    private List<AliasDictionaryDTO> matchAliases(String message, List<String> keywords) {
        String normalized = safeLower(message);
        String compact = normalized.replaceAll("\\s+", "");
        List<AliasDictionaryDTO> result = new ArrayList<>();
        List<AliasDictionaryDTO> aliases = loadAliases();
        for (AliasDictionaryDTO alias : aliases) {
            if (alias == null || !StringUtils.hasText(alias.getAlias())) {
                continue;
            }
            String aliasText = safeLower(alias.getAlias());
            if (!StringUtils.hasText(aliasText)) {
                continue;
            }
            boolean matched;
            if (aliasText.contains(" ")) {
                matched = normalized.contains(aliasText);
            } else {
                matched = compact.contains(aliasText.replaceAll("\\s+", ""));
            }
            if (!matched && keywords != null && !keywords.isEmpty()) {
                for (String keyword : keywords) {
                    if (!StringUtils.hasText(keyword)) {
                        continue;
                    }
                    if (keyword.equals(aliasText)) {
                        matched = true;
                        break;
                    }
                }
            }
            if (matched) {
                result.add(alias);
            }
        }
        return result;
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

    private List<String> parseTerms(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (!StringUtils.hasText(trimmed)) {
            return Collections.emptyList();
        }
        if (looksLikeJsonArray(trimmed)) {
            try {
                return objectMapper.readValue(trimmed, new TypeReference<List<String>>() {});
            } catch (Exception e) {
                log.debug("alias term JSON 파싱 실패: {}", trimmed, e);
            }
        }
        List<String> results = new ArrayList<>();
        String[] tokens = trimmed.split("[,\\n\\r]+");
        for (String token : tokens) {
            if (StringUtils.hasText(token)) {
                results.add(token.trim());
            }
        }
        if (results.isEmpty()) {
            results.add(trimmed);
        }
        return results;
    }

    private static boolean looksLikeJsonArray(String value) {
        String trimmed = value.trim();
        return trimmed.startsWith("[") && trimmed.endsWith("]");
    }

    private static String normalizeBoardId(String boardId) {
        return boardId == null ? "" : boardId.trim().toLowerCase(Locale.ROOT);
    }

    private static List<String> extractKeywords(String message) {
        if (!StringUtils.hasText(message)) {
            return Collections.emptyList();
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        normalized = TOKEN_SPLITTER.matcher(normalized).replaceAll(" ").trim();
        if (!StringUtils.hasText(normalized)) {
            return Collections.emptyList();
        }
        String[] tokens = normalized.split("\\s+");
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String token : tokens) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            String normalizedToken = normalizeKeywordToken(token);
            if (!StringUtils.hasText(normalizedToken)) {
                continue;
            }
            if (normalizedToken.length() < 2) {
                continue;
            }
            if (STOPWORDS.contains(normalizedToken)) {
                continue;
            }
            unique.add(normalizedToken);
        }
        List<String> keywords = new ArrayList<>(unique);
        keywords.sort((a, b) -> Integer.compare(b.length(), a.length()));
        if (keywords.size() > 6) {
            return keywords.subList(0, 6);
        }
        return keywords;
    }

    private static String normalizeKeywordToken(String token) {
        if (!StringUtils.hasText(token)) {
            return "";
        }
        String normalized = token.toLowerCase(Locale.ROOT).trim();
        if (!hasKorean(normalized)) {
            return normalized;
        }
        return stripKoreanParticles(normalized);
    }

    private static boolean hasKorean(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= 0xAC00 && ch <= 0xD7A3) {
                return true;
            }
        }
        return false;
    }

    private static String stripKoreanParticles(String token) {
        String result = token;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String suffix : KOREAN_PARTICLE_SUFFIXES) {
                if (!StringUtils.hasText(suffix)) {
                    continue;
                }
                if (result.length() <= suffix.length() + 1) {
                    continue;
                }
                if (result.endsWith(suffix)) {
                    result = result.substring(0, result.length() - suffix.length());
                    changed = true;
                    break;
                }
            }
        }
        return result;
    }

    private static boolean containsAny(String text, String... tokens) {
        if (!StringUtils.hasText(text) || tokens == null) {
            return false;
        }
        for (String token : tokens) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static final class MatchupInfo {
        private String playerRace;
        private String opponentRace;
        private String matchupTag;
        private String matchupKoreanTag;
        private String boardTitle;
        private double confidence;
    }
}
