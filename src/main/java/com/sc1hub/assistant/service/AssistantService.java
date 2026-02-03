package com.sc1hub.assistant.service;

import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.assistant.config.AssistantRagProperties;
import com.sc1hub.assistant.dto.AssistantChatResponseDTO;
import com.sc1hub.assistant.dto.AssistantRelatedPostDTO;
import com.sc1hub.assistant.gemini.GeminiClient;
import com.sc1hub.assistant.gemini.GeminiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.rag.AssistantRagChunk;
import com.sc1hub.assistant.rag.AssistantRagSearchService;
import com.sc1hub.assistant.search.AssistantQueryParseResult;
import com.sc1hub.assistant.search.AssistantQueryParser;
import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.dto.BoardListDTO;
import com.sc1hub.board.mapper.BoardMapper;
import com.sc1hub.member.dto.MemberDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AssistantService {

    private static final Pattern SAFE_BOARD_TITLE = Pattern.compile("^[a-z0-9_]+$");
    private static final Pattern SOURCE_ID_PATTERN = Pattern.compile("^([a-z0-9_]+):(\\d+)$");
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
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
    private static final Set<String> FACT_KEYWORDS = new HashSet<>(Arrays.asList(
            "공격", "공격력", "방어", "방어력", "체력", "hp", "실드", "쉴드", "사거리", "사정거리", "시야",
            "이동속도", "속도", "공속", "공격속도", "쿨다운", "비용", "가격", "미네랄", "가스", "자원", "서플라이",
            "업그레이드", "업글", "기본", "특성", "능력치", "능력", "스탯", "스펙", "유닛",
            "unit", "stats", "stat", "damage", "armor", "health", "shield", "range", "speed", "cooldown", "cost", "supply"
    ));
    private static final int FACT_BOARD_SCORE_BONUS = 2;

    private final BoardMapper boardMapper;
    private final GeminiClient geminiClient;
    private final AssistantProperties assistantProperties;
    private final AssistantRagSearchService ragSearchService;
    private final AssistantRagProperties ragProperties;
    private final AssistantQueryParser queryParser;
    private final ObjectMapper objectMapper;
    private volatile List<BoardListDTO> cachedBoards = Collections.emptyList();
    private volatile long cachedBoardsAtMillis = 0L;

    private static final String LLM_MATCHUP_CACHE_VERSION = "v1";
    private static final String LLM_RERANK_CACHE_VERSION = "v1";
    private static final String LLM_RELATED_POSTS_CACHE_VERSION = "v1";
    private final ExpiringCache<LlmMatchupIntent> llmMatchupCache = new ExpiringCache<>();
    private final SimpleRateLimiter llmMatchupRateLimiter = new SimpleRateLimiter();
    private final ExpiringCache<List<Integer>> llmRerankCache = new ExpiringCache<>();
    private final SimpleRateLimiter llmRerankRateLimiter = new SimpleRateLimiter();
    private final ExpiringCache<LlmRelatedPostsSelection> llmRelatedPostsCache = new ExpiringCache<>();
    private final SimpleRateLimiter llmRelatedPostsRateLimiter = new SimpleRateLimiter();

    public AssistantService(BoardMapper boardMapper,
                            GeminiClient geminiClient,
                            AssistantProperties assistantProperties,
                            AssistantRagSearchService ragSearchService,
                            AssistantRagProperties ragProperties,
                            AssistantQueryParser queryParser,
                            ObjectMapper objectMapper) {
        this.boardMapper = boardMapper;
        this.geminiClient = geminiClient;
        this.assistantProperties = assistantProperties;
        this.ragSearchService = ragSearchService;
        this.ragProperties = ragProperties;
        this.queryParser = queryParser;
        this.objectMapper = objectMapper;
    }

    public AssistantChatResponseDTO chat(String message, MemberDTO member) {
        AssistantChatResponseDTO response = new AssistantChatResponseDTO();

        if (!assistantProperties.isEnabled()) {
            response.setError("AI 기능이 비활성화되어 있습니다.");
            return response;
        }

        if (assistantProperties.isRequireLogin() && member == null) {
            response.setError("로그인 후 이용할 수 있습니다.");
            return response;
        }

        if (!StringUtils.hasText(message)) {
            response.setError("질문을 입력해주세요.");
            return response;
        }

        String normalizedMessage = message.trim();

        AssistantQueryParseResult parseResult;
        try {
            parseResult = queryParser.parse(normalizedMessage);
        } catch (Exception e) {
            log.warn("검색 파서 처리 실패. 기본 키워드 로직으로 대체합니다.", e);
            parseResult = new AssistantQueryParseResult();
            List<String> fallbackKeywords = extractKeywords(normalizedMessage);
            parseResult.setKeywords(fallbackKeywords);
            parseResult.setExpandedTerms(fallbackKeywords);
        }

        applyLlmMatchupClassificationIfNeeded(normalizedMessage, parseResult);

        List<String> keywords = parseResult.getKeywords();
        List<String> expandedTerms = parseResult.getExpandedTerms();
        if (expandedTerms == null || expandedTerms.isEmpty()) {
            expandedTerms = keywords == null ? Collections.emptyList() : keywords;
            parseResult.setExpandedTerms(expandedTerms);
        }
        Map<String, Double> boardWeights = buildBoardWeights(parseResult.getBoardWeights());
        parseResult.setBoardWeights(boardWeights);
        logParserResult(parseResult, expandedTerms);
        boolean factQuery = isFactQuery(normalizedMessage, keywords);
        boolean aliasMatched = parseResult.isAliasMatched();
        String ragQuery = buildRagQuery(normalizedMessage, expandedTerms, aliasMatched);
        RagRetrieval ragRetrieval = tryRetrieveWithRag(ragQuery, expandedTerms, boardWeights, factQuery, aliasMatched);

        List<CandidatePost> candidates = Collections.emptyList();
        if (shouldLoadKeywordCandidates(expandedTerms, ragRetrieval, aliasMatched)) {
            candidates = findCandidates(expandedTerms, boardWeights, resolveCandidatePoolLimit(), factQuery);
            candidates = rerankCandidatesIfEnabled(normalizedMessage, candidates);
        }

        String prompt;
        Set<String> allowedSourceIds = new LinkedHashSet<>();
        if (ragRetrieval != null) {
            if (!candidates.isEmpty()) {
                prompt = buildHybridPrompt(normalizedMessage, ragRetrieval.matches, candidates, allowedSourceIds);
            } else {
                prompt = buildRagPrompt(normalizedMessage, ragRetrieval.matches, allowedSourceIds);
            }
        } else {
            List<CandidatePost> contextPosts = candidates.subList(0, Math.min(assistantProperties.getContextPosts(), candidates.size()));
            prompt = buildPrompt(normalizedMessage, contextPosts, allowedSourceIds);
        }

        AssistantAnswerResult answerResult;
        String answer;
        try {
            String rawAnswer = geminiClient.generateAnswer(prompt);
            answerResult = parseAnswerResult(rawAnswer, allowedSourceIds);
            answer = answerResult.getAnswer() == null ? "" : answerResult.getAnswer().trim();
            if (!StringUtils.hasText(answer)) {
                answer = "답변을 생성하지 못했습니다.";
            }
            response.setAnswer(answer);
            response.setUsedPostIds(answerResult.getUsedPostIds());
        } catch (GeminiException e) {
            log.error("Gemini API 호출 실패", e);
            response.setError("AI 설정 또는 API 호출에 실패했습니다. 관리자에게 문의해주세요.");
            return response;
        } catch (Exception e) {
            log.error("AI 응답 생성 중 오류 발생", e);
            response.setError("AI 응답 생성 중 오류가 발생했습니다.");
            return response;
        }

        RelatedPostsSelection selection = selectAnswerGroundedRelatedPosts(
                normalizedMessage,
                answer,
                expandedTerms,
                answerResult.getUsedPostIds(),
                ragRetrieval,
                candidates,
                boardWeights
        );
        response.setRelatedPosts(selection.relatedPosts);
        response.setRelatedPostsNotice(selection.notice);
        logAnswerGroundedRelatedPosts(normalizedMessage, parseResult, expandedTerms, answerResult.getUsedPostIds(), selection);
        return response;
    }

    private AssistantAnswerResult parseAnswerResult(String raw, Set<String> allowedSourceIds) {
        AssistantAnswerResult result = new AssistantAnswerResult();
        String trimmed = raw == null ? "" : raw.trim();
        if (!StringUtils.hasText(trimmed)) {
            result.setAnswer("");
            return result;
        }

        if (objectMapper == null) {
            result.setAnswer(trimmed);
            return result;
        }

        String json = extractFirstJsonObject(trimmed);
        if (!StringUtils.hasText(json)) {
            result.setAnswer(trimmed);
            return result;
        }

        try {
            JsonNode node = objectMapper.readTree(json);
            String answer = textOrNull(node.get("answer"));
            if (!StringUtils.hasText(answer)) {
                answer = textOrNull(node.get("text"));
            }
            if (!StringUtils.hasText(answer)) {
                answer = trimmed;
            }
            result.setAnswer(answer.trim());

            LinkedHashSet<String> used = new LinkedHashSet<>();
            addSourceIdsFromNode(used, node.get("citations"), allowedSourceIds);
            addSourceIdsFromNode(used, node.get("used_post_ids"), allowedSourceIds);
            addSourceIdsFromNode(used, node.get("usedPostIds"), allowedSourceIds);
            result.setUsedPostIds(new ArrayList<>(used));
            return result;
        } catch (Exception e) {
            result.setAnswer(trimmed);
            return result;
        }
    }

    private void addSourceIdsFromNode(Set<String> target, JsonNode node, Set<String> allowedSourceIds) {
        if (target == null || node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item == null) {
                    continue;
                }
                if (item.isObject()) {
                    addValidatedSourceId(target, textOrNull(item.get("sourceId")), allowedSourceIds);
                    addValidatedSourceId(target, textOrNull(item.get("id")), allowedSourceIds);
                } else {
                    addValidatedSourceId(target, textOrNull(item), allowedSourceIds);
                }
            }
            return;
        }
        if (node.isObject()) {
            addValidatedSourceId(target, textOrNull(node.get("sourceId")), allowedSourceIds);
            addValidatedSourceId(target, textOrNull(node.get("id")), allowedSourceIds);
            return;
        }
        addValidatedSourceId(target, textOrNull(node), allowedSourceIds);
    }

    private void addValidatedSourceId(Set<String> target, String raw, Set<String> allowedSourceIds) {
        if (target == null || !StringUtils.hasText(raw)) {
            return;
        }
        if (allowedSourceIds == null || allowedSourceIds.isEmpty()) {
            return;
        }
        String normalized = normalizeSourceId(raw);
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        if (!allowedSourceIds.contains(normalized)) {
            return;
        }
        target.add(normalized);
    }

    private static String normalizeSourceId(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        Matcher matcher = SOURCE_ID_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            return "";
        }
        String boardTitle = matcher.group(1);
        int postNum;
        try {
            postNum = Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException e) {
            return "";
        }
        if (!StringUtils.hasText(boardTitle) || postNum <= 0) {
            return "";
        }
        return boardTitle + ":" + postNum;
    }

    private void applyLlmMatchupClassificationIfNeeded(String message, AssistantQueryParseResult parseResult) {
        if (parseResult == null || assistantProperties == null) {
            return;
        }
        if (!assistantProperties.isLlmMatchupClassificationEnabled()) {
            return;
        }
        double maxParserConfidence = assistantProperties.getLlmMatchupClassificationMaxParserConfidence();
        if (parseResult.getConfidence() > maxParserConfidence) {
            return;
        }

        String normalized = normalizeLlmKey(message);
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        String cacheKey = "matchup:" + LLM_MATCHUP_CACHE_VERSION + ":" + normalized;
        int cacheSeconds = Math.max(0, assistantProperties.getLlmMatchupClassificationCacheSeconds());
        LlmMatchupIntent cached = llmMatchupCache.get(cacheKey, cacheSeconds);
        if (cached != null) {
            applyLlmMatchupIntent(parseResult, cached);
            return;
        }

        int rateLimitPerMinute = Math.max(0, assistantProperties.getLlmMatchupClassificationRateLimitPerMinute());
        if (llmMatchupRateLimiter.tryAcquire(rateLimitPerMinute)) {
            try {
                LlmMatchupIntent classified = classifyMatchupIntentWithGemini(message);
                if (classified == null) {
                    return;
                }
                double minConfidence = assistantProperties.getLlmMatchupClassificationMinConfidence();
                if (classified.confidence != null && classified.confidence < minConfidence) {
                    return;
                }
                llmMatchupCache.put(cacheKey, classified, cacheSeconds);
                applyLlmMatchupIntent(parseResult, classified);
            } catch (GeminiException e) {
                log.debug("LLM matchup/intent 분류 실패(GeminiException): {}", e.getMessage());
            } catch (Exception e) {
                log.debug("LLM matchup/intent 분류 실패", e);
            }
        }
    }

    private void applyLlmMatchupIntent(AssistantQueryParseResult parseResult, LlmMatchupIntent classified) {
        if (parseResult == null || classified == null) {
            return;
        }

        String intent = normalizeIntent(classified.intent);
        if (StringUtils.hasText(intent)) {
            String existing = parseResult.getIntent();
            if (!StringUtils.hasText(existing) || "general".equalsIgnoreCase(existing)) {
                parseResult.setIntent(intent);
            }
        }

        String playerRace = normalizeRaceToken(classified.playerRace);
        String opponentRace = normalizeRaceToken(classified.opponentRace);
        if (StringUtils.hasText(playerRace)) {
            parseResult.setPlayerRace(playerRace);
        }
        if (StringUtils.hasText(opponentRace)) {
            parseResult.setOpponentRace(opponentRace);
        }

        if (StringUtils.hasText(playerRace) && StringUtils.hasText(opponentRace)) {
            String matchup = playerRace + "v" + opponentRace;
            parseResult.setMatchup(matchup);

            String boardTitle = boardTitleForMatchup(playerRace, opponentRace);
            if (StringUtils.hasText(boardTitle)) {
                Map<String, Double> weights = parseResult.getBoardWeights();
                if (weights == null) {
                    weights = new LinkedHashMap<>();
                    parseResult.setBoardWeights(weights);
                }
                double existing = weights.getOrDefault(boardTitle, 1.0);
                weights.put(boardTitle, Math.max(existing, 1.6));
            }

            addExpandedTerm(parseResult, matchup);
            addExpandedTerm(parseResult, toKoreanMatchup(playerRace, opponentRace));
        }
    }

    private LlmMatchupIntent classifyMatchupIntentWithGemini(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String prompt = buildMatchupIntentPrompt(message);
        String raw = geminiClient.generateAnswer(prompt);
        return parseLlmMatchupIntent(raw);
    }

    private String buildMatchupIntentPrompt(String message) {
        String trimmed = message == null ? "" : message.trim();
        return "You are a strict JSON classifier for StarCraft: Brood War questions.\n"
                + "Return JSON only. Do not include markdown or explanations.\n\n"
                + "Task:\n"
                + "- intent: one of [\"build\",\"guide\",\"facts\",\"general\"]\n"
                + "- playerRace: one of [\"P\",\"T\",\"Z\",null]\n"
                + "- opponentRace: one of [\"P\",\"T\",\"Z\",null]\n"
                + "- confidence: number between 0 and 1\n\n"
                + "Rules:\n"
                + "- If only one race is mentioned, set it to playerRace and opponentRace=null.\n"
                + "- If a matchup is explicitly written like PvT / TvZ / \"테란 대 프로토스\", infer playerRace vs opponentRace.\n\n"
                + "User question: " + trimmed + "\n\n"
                + "Output schema:\n"
                + "{\"intent\":\"general\",\"playerRace\":null,\"opponentRace\":null,\"confidence\":0.0}\n";
    }

    private LlmMatchupIntent parseLlmMatchupIntent(String raw) {
        if (!StringUtils.hasText(raw) || objectMapper == null) {
            return null;
        }
        String json = extractFirstJsonObject(raw);
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            LlmMatchupIntent result = new LlmMatchupIntent();
            result.intent = textOrNull(node.get("intent"));
            result.playerRace = textOrNull(node.get("playerRace"));
            result.opponentRace = textOrNull(node.get("opponentRace"));
            if (node.hasNonNull("confidence")) {
                result.confidence = node.get("confidence").asDouble(0.0);
            }
            if (!StringUtils.hasText(result.intent)
                    && !StringUtils.hasText(result.playerRace)
                    && !StringUtils.hasText(result.opponentRace)) {
                return null;
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private List<CandidatePost> rerankCandidatesIfEnabled(String message, List<CandidatePost> candidates) {
        if (assistantProperties == null || !assistantProperties.isLlmRerankEnabled()) {
            return candidates;
        }
        if (candidates == null || candidates.size() <= 1) {
            return candidates;
        }

        int topN = Math.max(0, assistantProperties.getLlmRerankTopN());
        if (topN <= 1) {
            return candidates;
        }

        int limit = Math.min(topN, candidates.size());
        List<CandidatePost> top = candidates.subList(0, limit);

        String normalized = normalizeLlmKey(message);
        if (!StringUtils.hasText(normalized)) {
            return candidates;
        }
        String cacheKey = buildRerankCacheKey(normalized, top);
        int cacheSeconds = Math.max(0, assistantProperties.getLlmRerankCacheSeconds());
        List<Integer> cachedOrder = llmRerankCache.get(cacheKey, cacheSeconds);
        if (cachedOrder != null && !cachedOrder.isEmpty()) {
            return applyRerankOrder(candidates, cachedOrder, limit);
        }

        int rateLimitPerMinute = Math.max(0, assistantProperties.getLlmRerankRateLimitPerMinute());
        if (llmRerankRateLimiter.tryAcquire(rateLimitPerMinute)) {
            try {
                List<Integer> order = rerankWithGemini(message, top);
                if (order != null && !order.isEmpty()) {
                    llmRerankCache.put(cacheKey, order, cacheSeconds);
                    return applyRerankOrder(candidates, order, limit);
                }
            } catch (GeminiException e) {
                log.debug("LLM rerank 실패(GeminiException): {}", e.getMessage());
            } catch (Exception e) {
                log.debug("LLM rerank 실패", e);
            }
        }
        return candidates;
    }

    private String buildRerankCacheKey(String normalizedMessage, List<CandidatePost> top) {
        StringBuilder sb = new StringBuilder();
        sb.append("rerank:").append(LLM_RERANK_CACHE_VERSION).append(":").append(normalizedMessage).append(":");
        for (CandidatePost post : top) {
            if (post == null || post.post == null) {
                continue;
            }
            sb.append(postKey(post.boardTitle, post.post.getPostNum())).append(',');
        }
        return sb.toString();
    }

    private List<Integer> rerankWithGemini(String message, List<CandidatePost> candidates) {
        if (!StringUtils.hasText(message) || candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        String prompt = buildRerankPrompt(message, candidates);
        String raw = geminiClient.generateAnswer(prompt);
        return parseRerankOrder(raw, candidates.size());
    }

    private String buildRerankPrompt(String message, List<CandidatePost> candidates) {
        int excerptChars = Math.max(60, assistantProperties.getLlmRerankExcerptChars());

        StringBuilder sb = new StringBuilder();
        sb.append("You are a strict reranker for SC1Hub search results.\n");
        sb.append("Return JSON only. No markdown, no explanation.\n");
        sb.append("Return a JSON array of 1-based indices in best-to-worst order.\n");
        sb.append("The array must be a permutation of [1..N].\n\n");
        sb.append("Question: ").append(message.trim()).append("\n\n");
        sb.append("Candidates:\n");
        int idx = 1;
        for (CandidatePost post : candidates) {
            if (post == null || post.post == null) {
                continue;
            }
            String title = safeText(post.post.getTitle());
            String excerpt = safeText(stripHtmlToText(post.post.getContent()));
            excerpt = truncate(excerpt, excerptChars);
            sb.append(idx).append(") ");
            sb.append("title=").append(title);
            if (StringUtils.hasText(excerpt)) {
                sb.append(" | excerpt=").append(excerpt);
            }
            sb.append("\n");
            idx += 1;
        }
        sb.append("\nOutput example: [3,1,2]\n");
        return sb.toString();
    }

    private List<Integer> parseRerankOrder(String raw, int size) {
        if (!StringUtils.hasText(raw) || objectMapper == null || size <= 1) {
            return Collections.emptyList();
        }
        String json = extractFirstJsonArray(raw);
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            List<Integer> parsed = objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, Integer.class));
            if (parsed == null || parsed.isEmpty()) {
                return Collections.emptyList();
            }
            boolean[] used = new boolean[size + 1];
            List<Integer> order = new ArrayList<>(size);
            for (Integer value : parsed) {
                if (value == null) {
                    continue;
                }
                int idx = value;
                if (idx < 1 || idx > size) {
                    continue;
                }
                if (used[idx]) {
                    continue;
                }
                used[idx] = true;
                order.add(idx);
            }
            for (int i = 1; i <= size; i += 1) {
                if (!used[i]) {
                    order.add(i);
                }
            }
            return order;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<CandidatePost> applyRerankOrder(List<CandidatePost> candidates, List<Integer> order, int limit) {
        if (candidates == null || candidates.size() <= 1 || order == null || order.isEmpty()) {
            return candidates;
        }
        int safeLimit = Math.min(limit, candidates.size());
        if (safeLimit <= 1) {
            return candidates;
        }
        List<CandidatePost> top = candidates.subList(0, safeLimit);
        List<CandidatePost> reranked = new ArrayList<>(candidates.size());

        for (Integer idx : order) {
            if (idx == null) {
                continue;
            }
            int i = idx - 1;
            if (i < 0 || i >= top.size()) {
                continue;
            }
            reranked.add(top.get(i));
            if (reranked.size() >= top.size()) {
                break;
            }
        }

        if (reranked.size() != top.size()) {
            // Fallback: keep original order if something went wrong.
            return candidates;
        }
        if (candidates.size() > safeLimit) {
            reranked.addAll(candidates.subList(safeLimit, candidates.size()));
        }
        return reranked;
    }

    private static String normalizeLlmKey(String message) {
        if (!StringUtils.hasText(message)) {
            return "";
        }
        return message.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static void addExpandedTerm(AssistantQueryParseResult parseResult, String term) {
        if (parseResult == null || !StringUtils.hasText(term)) {
            return;
        }
        List<String> expanded = parseResult.getExpandedTerms();
        if (expanded == null) {
            expanded = new ArrayList<>();
            parseResult.setExpandedTerms(expanded);
        }
        String normalized = term.trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized) || normalized.length() < 2) {
            return;
        }
        for (String existing : expanded) {
            if (normalized.equals(existing == null ? "" : existing.trim().toLowerCase(Locale.ROOT))) {
                return;
            }
        }
        expanded.add(normalized);
    }

    private static String boardTitleForMatchup(String playerRace, String opponentRace) {
        if (!StringUtils.hasText(playerRace) || !StringUtils.hasText(opponentRace)) {
            return "";
        }
        return playerRace.toLowerCase(Locale.ROOT) + "vs" + opponentRace.toLowerCase(Locale.ROOT) + "board";
    }

    private static String toKoreanMatchup(String playerRace, String opponentRace) {
        String left = toKoreanRace(playerRace);
        String right = toKoreanRace(opponentRace);
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return "";
        }
        return left + right;
    }

    private static String toKoreanRace(String race) {
        if (!StringUtils.hasText(race)) {
            return "";
        }
        String normalized = race.trim().toUpperCase(Locale.ROOT);
        if ("P".equals(normalized)) {
            return "프";
        }
        if ("T".equals(normalized)) {
            return "테";
        }
        if ("Z".equals(normalized)) {
            return "저";
        }
        return "";
    }

    private static String normalizeRaceToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String trimmed = raw.trim();
        String normalized = trimmed.toUpperCase(Locale.ROOT);
        if ("P".equals(normalized) || "PROTOSS".equals(normalized) || "프로토스".equals(trimmed) || "프".equals(trimmed)) {
            return "P";
        }
        if ("T".equals(normalized) || "TERRAN".equals(normalized) || "테란".equals(trimmed) || "테".equals(trimmed)) {
            return "T";
        }
        if ("Z".equals(normalized) || "ZERG".equals(normalized) || "저그".equals(trimmed) || "저".equals(trimmed)) {
            return "Z";
        }
        return "";
    }

    private static String normalizeIntent(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if ("build".equals(normalized) || "buildorder".equals(normalized) || "build_order".equals(normalized)) {
            return "build";
        }
        if ("guide".equals(normalized) || "howto".equals(normalized) || "how_to".equals(normalized)) {
            return "guide";
        }
        if ("facts".equals(normalized) || "fact".equals(normalized) || "stats".equals(normalized)) {
            return "facts";
        }
        if ("general".equals(normalized)) {
            return "general";
        }
        return "";
    }

    private static String extractFirstJsonObject(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return "";
        }
        return raw.substring(start, end + 1).trim();
    }

    private static String extractFirstJsonArray(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return "";
        }
        return raw.substring(start, end + 1).trim();
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String value = node.asText(null);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private RagRetrieval tryRetrieveWithRag(String ragQuery,
                                            List<String> expandedTerms,
                                            Map<String, Double> boardWeights,
                                            boolean preferFactBoards,
                                            boolean aliasMatched) {
        if (ragSearchService == null || ragProperties == null || !ragSearchService.isEnabled()) {
            return null;
        }
        try {
            List<AssistantRagSearchService.Match> matches = ragSearchService.search(ragQuery, ragProperties.getSearchTopChunks());
            if (matches == null || matches.isEmpty()) {
                return null;
            }
            List<AssistantRagSearchService.Match> filtered = filterRagMatches(matches, expandedTerms, aliasMatched);
            if (filtered.isEmpty()) {
                return null;
            }
            if (preferFactBoards) {
                filtered = preferFactBoardMatches(filtered);
                if (filtered.isEmpty()) {
                    return null;
                }
            }
            List<AssistantRagSearchService.Match> weightedMatches = applyBoardWeights(filtered, boardWeights);
            logRagMatches(weightedMatches);
            return new RagRetrieval(weightedMatches);
        } catch (Exception e) {
            log.warn("RAG 검색 실패. 키워드 검색으로 fallback 합니다.", e);
            return null;
        }
    }

    private List<AssistantRagSearchService.Match> filterRagMatches(List<AssistantRagSearchService.Match> matches,
                                                                   List<String> keywords,
                                                                   boolean aliasMatched) {
        if (matches == null || matches.isEmpty()) {
            return Collections.emptyList();
        }
        List<AssistantRagSearchService.Match> candidates = new ArrayList<>();
        double bestScore = 0.0;
        for (AssistantRagSearchService.Match match : matches) {
            if (match == null || match.getChunk() == null) {
                continue;
            }
            AssistantRagChunk chunk = match.getChunk();
            String boardTitle = normalizeBoardTitle(chunk.getBoardTitle());
            if (!StringUtils.hasText(boardTitle) || !SAFE_BOARD_TITLE.matcher(boardTitle).matches()) {
                continue;
            }
            if (isExcludedBoard(boardTitle)) {
                continue;
            }
            candidates.add(match);
            if (match.getScore() > bestScore) {
                bestScore = match.getScore();
            }
        }

        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        double minScore = clampScore(ragProperties == null ? 0.0 : ragProperties.getMinScore());
        double minScoreRatio = clampScore(ragProperties == null ? 0.0 : ragProperties.getMinScoreRatio());
        if (bestScore < minScore) {
            return Collections.emptyList();
        }

        double threshold = Math.max(minScore, bestScore * minScoreRatio);
        List<AssistantRagSearchService.Match> filtered = new ArrayList<>();
        for (AssistantRagSearchService.Match match : candidates) {
            if (match == null) {
                continue;
            }
            if (match.getScore() >= threshold) {
                filtered.add(match);
            }
        }

        if (filtered.isEmpty()) {
            return Collections.emptyList();
        }

        if (aliasMatched) {
            return filtered;
        }
        return filterMatchesByKeywords(filtered, keywords);
    }

    private List<AssistantRagSearchService.Match> filterMatchesByKeywords(List<AssistantRagSearchService.Match> matches, List<String> keywords) {
        if (matches == null || matches.isEmpty() || keywords == null || keywords.isEmpty()) {
            return matches == null ? Collections.emptyList() : matches;
        }

        List<String> normalizedKeywords = new ArrayList<>();
        for (String keyword : keywords) {
            if (!StringUtils.hasText(keyword)) {
                continue;
            }
            normalizedKeywords.add(keyword.toLowerCase(Locale.ROOT));
        }
        if (normalizedKeywords.isEmpty()) {
            return matches;
        }

        List<AssistantRagSearchService.Match> keywordMatches = new ArrayList<>();
        for (AssistantRagSearchService.Match match : matches) {
            if (match == null || match.getChunk() == null) {
                continue;
            }
            AssistantRagChunk chunk = match.getChunk();
            String haystack = safeLower(chunk.getTitle()) + " " + safeLower(chunk.getText());
            boolean hit = false;
            for (String keyword : normalizedKeywords) {
                if (haystack.contains(keyword)) {
                    hit = true;
                    break;
                }
            }
            if (hit) {
                keywordMatches.add(match);
            }
        }

        return keywordMatches.isEmpty() ? matches : keywordMatches;
    }

    private List<AssistantRagSearchService.Match> applyBoardWeights(List<AssistantRagSearchService.Match> matches,
                                                                    Map<String, Double> boardWeights) {
        if (matches == null || matches.isEmpty()) {
            return Collections.emptyList();
        }
        List<AssistantRagSearchService.Match> weighted = new ArrayList<>(matches.size());
        for (AssistantRagSearchService.Match match : matches) {
            if (match == null || match.getChunk() == null) {
                continue;
            }
            String boardTitle = normalizeBoardTitle(match.getChunk().getBoardTitle());
            double weight = resolveBoardWeight(boardTitle, boardWeights);
            double score = match.getScore() * weight;
            weighted.add(AssistantRagSearchService.Match.of(match.getChunk(), score));
        }
        weighted.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return weighted;
    }

    private static double clampScore(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.min(Math.max(value, 0.0), 1.0);
    }

    private String buildRagPrompt(String message, List<AssistantRagSearchService.Match> matches, Set<String> allowedSourceIds) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the SC1Hub assistant.\n");
        sb.append("Answer in Korean.\n");
        sb.append("Return JSON only. Do not include markdown or explanations.\n");
        sb.append("Use only the information provided in 'Site snippets' as factual ground.\n");
        sb.append("If the snippets are insufficient, say you cannot find enough information and suggest checking related posts.\n");
        sb.append("Keep the answer concise (max 5 sentences).\n");
        sb.append("If you used any snippet, include its sourceId in citations.\n");
        sb.append("citations must be a subset of the provided sourceId values.\n");
        sb.append("Do not output raw HTML.\n\n");
        sb.append("Output schema:\n");
        sb.append("{\"answer\":\"...\",\"citations\":[\"board:postNum\"]}\n\n");

        sb.append("User question: ").append(message).append("\n\n");
        sb.append("Site snippets:\n");

        if (matches == null || matches.isEmpty()) {
            sb.append("- (no related snippets found)\n");
        } else {
            int index = 1;
            int maxPromptChars = assistantProperties.getMaxPromptChars();

            for (AssistantRagSearchService.Match match : matches) {
                if (match == null || match.getChunk() == null) {
                    continue;
                }
                AssistantRagChunk chunk = match.getChunk();

                String boardTitle = normalizeBoardTitle(chunk.getBoardTitle());
                if (!StringUtils.hasText(boardTitle) || !SAFE_BOARD_TITLE.matcher(boardTitle).matches()) {
                    continue;
                }

                String title = safeText(chunk.getTitle());
                String excerpt = safeText(chunk.getText());
                excerpt = truncate(excerpt, assistantProperties.getMaxPostSnippetChars());
                String sourceId = postKey(boardTitle, chunk.getPostNum());

                String snippet = "[" + index + "] "
                        + "sourceId=" + sourceId + "\n"
                        + "board=" + boardTitle + ", "
                        + "postNum=" + chunk.getPostNum() + ", "
                        + "chunkIndex=" + chunk.getChunkIndex() + ", "
                        + "score=" + String.format(Locale.ROOT, "%.4f", match.getScore()) + "\n"
                        + "title=" + title + "\n"
                        + "text=" + excerpt + "\n"
                        + "url=" + (StringUtils.hasText(chunk.getUrl()) ? chunk.getUrl() : buildPostUrl(boardTitle, chunk.getPostNum()))
                        + "\n\n";

                if (sb.length() + snippet.length() > maxPromptChars) {
                    break;
                }
                sb.append(snippet);
                if (allowedSourceIds != null) {
                    allowedSourceIds.add(sourceId);
                }
                index += 1;
            }
        }

        String prompt = sb.toString();
        if (prompt.length() <= assistantProperties.getMaxPromptChars()) {
            return prompt;
        }
        return prompt.substring(0, assistantProperties.getMaxPromptChars());
    }

    private String buildHybridPrompt(String message, List<AssistantRagSearchService.Match> matches, List<CandidatePost> candidates, Set<String> allowedSourceIds) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the SC1Hub assistant.\n");
        sb.append("Answer in Korean.\n");
        sb.append("Return JSON only. Do not include markdown or explanations.\n");
        sb.append("Use only the information provided in 'Site snippets' and 'Site posts' as factual ground.\n");
        sb.append("If the snippets are insufficient, say you cannot find enough information and suggest checking related posts.\n");
        sb.append("Keep the answer concise (max 5 sentences).\n");
        sb.append("If you used any source, include its sourceId in citations.\n");
        sb.append("citations must be a subset of the provided sourceId values.\n");
        sb.append("Do not output raw HTML.\n\n");
        sb.append("Output schema:\n");
        sb.append("{\"answer\":\"...\",\"citations\":[\"board:postNum\"]}\n\n");

        sb.append("User question: ").append(message).append("\n\n");
        sb.append("Site snippets:\n");

        int maxPromptChars = assistantProperties.getMaxPromptChars();
        int maxSnippetChars = assistantProperties.getMaxPostSnippetChars();
        int ragLimit = Math.max(1, assistantProperties.getContextPosts() * 2);

        List<RagEvidence> ragEvidence = buildRagEvidence(matches);
        Set<String> includedKeys = new HashSet<>();
        if (ragEvidence.isEmpty()) {
            sb.append("- (no related snippets found)\n");
        } else {
            int index = 1;
            for (RagEvidence evidence : ragEvidence) {
                if (evidence == null) {
                    continue;
                }
                if (index > ragLimit) {
                    break;
                }
                String excerpt = truncate(safeText(evidence.text), maxSnippetChars);
                String sourceId = postKey(evidence.boardTitle, evidence.postNum);
                String snippet = "[" + index + "] "
                        + "sourceId=" + sourceId + "\n"
                        + "board=" + evidence.boardTitle + ", "
                        + "postNum=" + evidence.postNum + ", "
                        + "score=" + String.format(Locale.ROOT, "%.4f", evidence.score) + "\n"
                        + "title=" + safeText(evidence.title) + "\n"
                        + "text=" + excerpt + "\n"
                        + "url=" + evidence.url
                        + "\n\n";
                if (sb.length() + snippet.length() > maxPromptChars) {
                    break;
                }
                sb.append(snippet);
                includedKeys.add(postKey(evidence.boardTitle, evidence.postNum));
                if (allowedSourceIds != null) {
                    allowedSourceIds.add(sourceId);
                }
                index += 1;
            }
        }

        sb.append("Site posts:\n");

        if (candidates == null || candidates.isEmpty()) {
            sb.append("- (no related posts found)\n");
        } else {
            int index = 1;
            int keywordLimit = Math.max(0, assistantProperties.getContextPosts());
            for (CandidatePost post : candidates) {
                if (post == null || post.post == null) {
                    continue;
                }
                if (index > keywordLimit) {
                    break;
                }
                String key = postKey(post.boardTitle, post.post.getPostNum());
                if (includedKeys.contains(key)) {
                    continue;
                }
                String title = safeText(post.post.getTitle());
                String excerpt = safeText(stripHtmlToText(post.post.getContent()));
                excerpt = truncate(excerpt, maxSnippetChars);
                String snippet = "[" + index + "] "
                        + "sourceId=" + key + "\n"
                        + "board=" + post.boardTitle + ", "
                        + "postNum=" + post.post.getPostNum() + "\n"
                        + "title=" + title + "\n"
                        + "excerpt=" + excerpt + "\n"
                        + "url=" + buildPostUrl(post.boardTitle, post.post.getPostNum())
                        + "\n\n";
                if (sb.length() + snippet.length() > maxPromptChars) {
                    break;
                }
                sb.append(snippet);
                includedKeys.add(key);
                if (allowedSourceIds != null) {
                    allowedSourceIds.add(key);
                }
                index += 1;
            }
        }

        String prompt = sb.toString();
        if (prompt.length() <= maxPromptChars) {
            return prompt;
        }
        return prompt.substring(0, maxPromptChars);
    }

    private List<RagEvidence> buildRagEvidence(List<AssistantRagSearchService.Match> matches) {
        if (matches == null || matches.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, RagEvidence> bestByPost = new HashMap<>();
        for (AssistantRagSearchService.Match match : matches) {
            if (match == null || match.getChunk() == null) {
                continue;
            }
            AssistantRagChunk chunk = match.getChunk();
            String boardTitle = normalizeBoardTitle(chunk.getBoardTitle());
            if (!StringUtils.hasText(boardTitle) || !SAFE_BOARD_TITLE.matcher(boardTitle).matches()) {
                continue;
            }
            int postNum = chunk.getPostNum();
            String key = postKey(boardTitle, postNum);
            RagEvidence existing = bestByPost.get(key);
            if (existing == null || match.getScore() > existing.score) {
                String url = StringUtils.hasText(chunk.getUrl()) ? chunk.getUrl() : buildPostUrl(boardTitle, postNum);
                RagEvidence evidence = new RagEvidence(boardTitle, postNum, chunk.getTitle(), chunk.getRegDate(), url, chunk.getText(), match.getScore());
                bestByPost.put(key, evidence);
            }
        }
        List<RagEvidence> results = new ArrayList<>(bestByPost.values());
        results.sort((a, b) -> {
            int scoreCompare = Double.compare(b.score, a.score);
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            if (a.regDate != null && b.regDate != null) {
                int dateCompare = b.regDate.compareTo(a.regDate);
                if (dateCompare != 0) {
                    return dateCompare;
                }
            } else if (a.regDate != null) {
                return -1;
            } else if (b.regDate != null) {
                return 1;
            }
            return Integer.compare(b.postNum, a.postNum);
        });
        return results;
    }

    private boolean shouldLoadKeywordCandidates(List<String> expandedTerms,
                                                RagRetrieval ragRetrieval,
                                                boolean forceCandidates) {
        if (expandedTerms == null || expandedTerms.isEmpty()) {
            return false;
        }
        int candidateLimit = resolveCandidatePoolLimit();
        if (candidateLimit <= 0) {
            return false;
        }
        if (forceCandidates) {
            return true;
        }
        if (ragRetrieval == null) {
            return true;
        }
        int ragCount = ragRetrieval.matches == null ? 0 : ragRetrieval.matches.size();
        return ragCount < candidateLimit;
    }

    private int resolveCandidateLimit() {
        int contextPosts = Math.max(0, assistantProperties.getContextPosts());
        int relatedPosts = Math.max(0, assistantProperties.getMaxRelatedPosts());
        return Math.max(contextPosts, relatedPosts);
    }

    private int resolveCandidatePoolLimit() {
        int baseLimit = resolveCandidateLimit();
        int pool = Math.max(0, assistantProperties.getRelatedCandidatePoolSize());
        return Math.max(baseLimit, pool);
    }

    private List<BoardListDTO> getBoardListCached() {
        int cacheSeconds = Math.max(0, assistantProperties.getBoardListCacheSeconds());
        if (cacheSeconds == 0) {
            try {
                List<BoardListDTO> boards = boardMapper.getBoardList();
                return boards == null ? Collections.emptyList() : boards;
            } catch (Exception e) {
                return Collections.emptyList();
            }
        }

        long now = System.currentTimeMillis();
        long cacheMillis = cacheSeconds * 1000L;
        if (now - cachedBoardsAtMillis < cacheMillis && cachedBoards != null && !cachedBoards.isEmpty()) {
            return cachedBoards;
        }

        synchronized (this) {
            if (now - cachedBoardsAtMillis < cacheMillis && cachedBoards != null && !cachedBoards.isEmpty()) {
                return cachedBoards;
            }
            try {
                List<BoardListDTO> boards = boardMapper.getBoardList();
                if (boards == null || boards.isEmpty()) {
                    cachedBoards = Collections.emptyList();
                } else {
                    cachedBoards = boards;
                }
            } catch (Exception e) {
                return cachedBoards == null ? Collections.emptyList() : cachedBoards;
            }
            cachedBoardsAtMillis = now;
            return cachedBoards;
        }
    }

    private Map<String, Double> buildBoardWeights(Map<String, Double> parsedWeights) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (parsedWeights != null) {
            for (Map.Entry<String, Double> entry : parsedWeights.entrySet()) {
                if (entry == null || !StringUtils.hasText(entry.getKey())) {
                    continue;
                }
                result.put(normalizeBoardTitle(entry.getKey()), sanitizeWeight(entry.getValue()));
            }
        }
        List<BoardListDTO> boards = getBoardListCached();
        if (boards != null) {
            for (BoardListDTO board : boards) {
                String boardTitle = normalizeBoardTitle(board == null ? null : board.getBoardTitle());
                if (!StringUtils.hasText(boardTitle)) {
                    continue;
                }
                result.putIfAbsent(boardTitle, 1.0);
            }
        }
        return result;
    }

    private static double resolveBoardWeight(String boardTitle, Map<String, Double> boardWeights) {
        if (boardWeights == null || boardWeights.isEmpty()) {
            return 1.0;
        }
        String normalized = normalizeBoardTitle(boardTitle);
        Double weight = boardWeights.get(normalized);
        if (weight == null) {
            return 1.0;
        }
        return sanitizeWeight(weight);
    }

    private static boolean hasBoostedBoards(Map<String, Double> boardWeights) {
        if (boardWeights == null || boardWeights.isEmpty()) {
            return false;
        }
        for (double weight : boardWeights.values()) {
            if (weight > 1.05) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Double> relaxBoardWeights(Map<String, Double> boardWeights) {
        if (boardWeights == null || boardWeights.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Double> relaxed = new LinkedHashMap<>();
        for (String key : boardWeights.keySet()) {
            if (!StringUtils.hasText(key)) {
                continue;
            }
            relaxed.put(key, 1.0);
        }
        return relaxed;
    }

    private static double sanitizeWeight(Double weight) {
        if (weight == null) {
            return 1.0;
        }
        if (Double.isNaN(weight) || Double.isInfinite(weight)) {
            return 1.0;
        }
        if (weight <= 0.1) {
            return 0.1;
        }
        return Math.min(weight, 5.0);
    }

    private List<CandidatePost> findCandidates(List<String> keywords,
                                               Map<String, Double> boardWeights,
                                               int maxResults,
                                               boolean preferFactBoards) {
        List<CandidatePost> primary = findCandidatesInternal(keywords, boardWeights, maxResults, preferFactBoards);
        if (primary.isEmpty()) {
            return primary;
        }
        if (primary.size() < maxResults && hasBoostedBoards(boardWeights)) {
            Map<String, Double> relaxedWeights = relaxBoardWeights(boardWeights);
            List<CandidatePost> relaxed = findCandidatesInternal(keywords, relaxedWeights, maxResults, preferFactBoards);
            primary = mergeCandidateResults(primary, relaxed, maxResults);
        }
        logKeywordCandidates(primary);
        return primary;
    }

    private List<CandidatePost> findCandidatesInternal(List<String> keywords,
                                                       Map<String, Double> boardWeights,
                                                       int maxResults,
                                                       boolean preferFactBoards) {
        if (maxResults <= 0 || keywords == null || keywords.isEmpty()) {
            return Collections.emptyList();
        }

        List<BoardListDTO> boards = getBoardListCached();
        if (boards == null || boards.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> factBoards = preferFactBoards ? resolveFactBoards() : Collections.emptySet();
        List<BoardListDTO> orderedBoards = boards;
        if (preferFactBoards && !factBoards.isEmpty()) {
            List<BoardListDTO> preferred = new ArrayList<>();
            List<BoardListDTO> others = new ArrayList<>();
            for (BoardListDTO board : boards) {
                String boardTitle = normalizeBoardTitle(board == null ? null : board.getBoardTitle());
                if (factBoards.contains(boardTitle)) {
                    preferred.add(board);
                } else {
                    others.add(board);
                }
            }
            if (!preferred.isEmpty()) {
                orderedBoards = new ArrayList<>(preferred.size() + others.size());
                orderedBoards.addAll(preferred);
                orderedBoards.addAll(others);
            }
        }

        List<String> normalizedKeywords = new ArrayList<>();
        for (String keyword : keywords) {
            if (!StringUtils.hasText(keyword)) {
                continue;
            }
            normalizedKeywords.add(keyword.toLowerCase(Locale.ROOT));
        }
        if (normalizedKeywords.isEmpty()) {
            return Collections.emptyList();
        }

        Comparator<CandidatePost> comparator = candidateComparator();
        PriorityQueue<CandidatePost> heap = new PriorityQueue<>(maxResults, (a, b) -> comparator.compare(b, a));

        for (BoardListDTO board : orderedBoards) {
            String boardTitle = normalizeBoardTitle(board == null ? null : board.getBoardTitle());
            if (!StringUtils.hasText(boardTitle) || !SAFE_BOARD_TITLE.matcher(boardTitle).matches()) {
                continue;
            }
            if (isExcludedBoard(boardTitle)) {
                continue;
            }
            try {
                List<BoardDTO> posts = boardMapper.searchPostsByKeywords(boardTitle, normalizedKeywords, assistantProperties.getPerBoardLimit());
                if (posts == null || posts.isEmpty()) {
                    continue;
                }
                double boardWeight = resolveBoardWeight(boardTitle, boardWeights);
                for (BoardDTO post : posts) {
                    if (post == null) {
                        continue;
                    }
                    String titleLower = safeLower(post.getTitle());
                    String contentLower = safeLower(stripHtmlToText(post.getContent()));
                    String searchTermsLower = safeLower(post.getSearchTerms());
                    double score = scoreCandidate(titleLower, contentLower, searchTermsLower, normalizedKeywords);
                    if (preferFactBoards && factBoards.contains(boardTitle)) {
                        score += FACT_BOARD_SCORE_BONUS;
                    }
                    score *= boardWeight;
                    CandidatePost candidate = new CandidatePost(boardTitle, post, score);
                    if (heap.size() < maxResults) {
                        heap.add(candidate);
                    } else if (compareCandidates(candidate, heap.peek()) < 0) {
                        heap.poll();
                        heap.add(candidate);
                    }
                }
            } catch (Exception ignored) {
                // Skip broken boards without failing the whole request
            }
        }

        if (heap.isEmpty()) {
            return Collections.emptyList();
        }

        List<CandidatePost> results = new ArrayList<>(heap);
        results.sort(comparator);
        return results;
    }

    private static List<CandidatePost> mergeCandidateResults(List<CandidatePost> primary,
                                                             List<CandidatePost> secondary,
                                                             int limit) {
        Map<String, CandidatePost> merged = new LinkedHashMap<>();
        if (primary != null) {
            for (CandidatePost post : primary) {
                if (post == null || post.post == null) {
                    continue;
                }
                merged.put(postKey(post.boardTitle, post.post.getPostNum()), post);
            }
        }
        if (secondary != null) {
            for (CandidatePost post : secondary) {
                if (post == null || post.post == null) {
                    continue;
                }
                String key = postKey(post.boardTitle, post.post.getPostNum());
                CandidatePost existing = merged.get(key);
                if (existing == null || post.score > existing.score) {
                    merged.put(key, post);
                }
            }
        }
        List<CandidatePost> results = new ArrayList<>(merged.values());
        results.sort(candidateComparator());
        if (results.size() <= limit) {
            return results;
        }
        return results.subList(0, limit);
    }

    private static Comparator<CandidatePost> candidateComparator() {
        return AssistantService::compareCandidates;
    }

    private static int compareCandidates(CandidatePost a, CandidatePost b) {
        int scoreCompare = Double.compare(b.score, a.score);
        if (scoreCompare != 0) {
            return scoreCompare;
        }
        Date dateA = a.post.getRegDate();
        Date dateB = b.post.getRegDate();
        if (dateA != null && dateB != null) {
            int dateCompare = dateB.compareTo(dateA);
            if (dateCompare != 0) {
                return dateCompare;
            }
        } else if (dateA != null) {
            return -1;
        } else if (dateB != null) {
            return 1;
        }
        return Integer.compare(b.post.getPostNum(), a.post.getPostNum());
    }

    private static double scoreCandidate(String titleLower, String contentLower, String searchTermsLower, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return 0;
        }
        String safeTitleLower = titleLower == null ? "" : titleLower;
        String safeContentLower = contentLower == null ? "" : contentLower;
        String safeSearchTermsLower = searchTermsLower == null ? "" : searchTermsLower;
        double score = 0;
        for (String keyword : keywords) {
            if (!StringUtils.hasText(keyword)) {
                continue;
            }
            if (safeTitleLower.contains(keyword)) {
                score += 3;
            }
            if (safeContentLower.contains(keyword)) {
                score += 1;
            }
            if (safeSearchTermsLower.contains(keyword)) {
                score += 1;
            }
        }
        return score;
    }

    private String buildPrompt(String message, List<CandidatePost> contextPosts, Set<String> allowedSourceIds) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the SC1Hub assistant.\n");
        sb.append("Answer in Korean.\n");
        sb.append("Return JSON only. Do not include markdown or explanations.\n");
        sb.append("Use only the information provided in 'Site posts' as factual ground.\n");
        sb.append("If the posts are insufficient, say you cannot find enough information and suggest checking related posts.\n");
        sb.append("Keep the answer concise (max 5 sentences).\n");
        sb.append("If you used any post, include its sourceId in citations.\n");
        sb.append("citations must be a subset of the provided sourceId values.\n");
        sb.append("Do not output raw HTML.\n\n");
        sb.append("Output schema:\n");
        sb.append("{\"answer\":\"...\",\"citations\":[\"board:postNum\"]}\n\n");

        sb.append("User question: ").append(message).append("\n\n");
        sb.append("Site posts:\n");

        int maxPromptChars = assistantProperties.getMaxPromptChars();
        if (contextPosts == null || contextPosts.isEmpty()) {
            sb.append("- (no related posts found)\n");
        } else {
            int index = 1;
            for (CandidatePost post : contextPosts) {
                if (post == null || post.post == null) {
                    continue;
                }
                String sourceId = postKey(post.boardTitle, post.post.getPostNum());
                String title = safeText(post.post.getTitle());
                String excerpt = safeText(stripHtmlToText(post.post.getContent()));
                excerpt = truncate(excerpt, assistantProperties.getMaxPostSnippetChars());
                String snippet = "[" + index + "] "
                        + "sourceId=" + sourceId + "\n"
                        + "board=" + post.boardTitle + ", "
                        + "postNum=" + post.post.getPostNum() + "\n"
                        + "title=" + title + "\n"
                        + "excerpt=" + excerpt + "\n"
                        + "url=" + buildPostUrl(post.boardTitle, post.post.getPostNum())
                        + "\n\n";
                if (sb.length() + snippet.length() > maxPromptChars) {
                    break;
                }
                sb.append(snippet);
                if (allowedSourceIds != null) {
                    allowedSourceIds.add(sourceId);
                }
                index += 1;
            }
        }

        String prompt = sb.toString();
        if (prompt.length() <= maxPromptChars) {
            return prompt;
        }
        return prompt.substring(0, maxPromptChars);
    }

    private RelatedPostsSelection selectAnswerGroundedRelatedPosts(String query,
                                                                  String answer,
                                                                  List<String> expandedTerms,
                                                                  List<String> usedPostIds,
                                                                  RagRetrieval ragRetrieval,
                                                                  List<CandidatePost> keywordCandidates,
                                                                  Map<String, Double> boardWeights) {
        int maxLinks = Math.min(3, Math.max(0, assistantProperties.getMaxRelatedPosts()));
        if (maxLinks <= 0) {
            return RelatedPostsSelection.empty(null);
        }

        if (usedPostIds == null || usedPostIds.isEmpty()) {
            return RelatedPostsSelection.empty("관련 글을 찾지 못했습니다.");
        }

        Map<String, RelatedPostCandidate> candidatePool = buildRelatedCandidatePool(ragRetrieval, keywordCandidates);
        RelatedPostCandidate evidence = pickEvidenceCandidate(usedPostIds, candidatePool);
        if (evidence == null) {
            return RelatedPostsSelection.empty("관련 글을 찾지 못했습니다.");
        }

        List<String> dbKeywords = buildDbKeywords(expandedTerms);
        expandBoostedBoardCandidates(candidatePool, evidence, dbKeywords, boardWeights);

        List<ScoredRelatedCandidate> scored = scoreSupportingCandidates(query, answer, expandedTerms, evidence, candidatePool, boardWeights);
        int supportingLimit = Math.max(0, maxLinks - 1);

        double threshold = sanitizeRelatedThreshold(assistantProperties.getRelatedPostThreshold());

        RelatedPostsSelection llmSelected = trySelectRelatedPostsWithLlm(query, answer, usedPostIds, evidence, scored, supportingLimit, threshold, candidatePool);
        if (llmSelected != null) {
            return llmSelected;
        }

        List<AssistantRelatedPostDTO> selected = new ArrayList<>(maxLinks);
        selected.add(toRelatedPostDto(evidence));

        String evidenceTitleKey = normalizeTitleKey(evidence.title);
        Set<String> usedTitleKeys = new HashSet<>();
        if (StringUtils.hasText(evidenceTitleKey)) {
            usedTitleKeys.add(evidenceTitleKey);
        }
        Set<String> usedWriters = new HashSet<>();
        String evidenceWriter = normalizeWriterKey(evidence.writer);
        if (StringUtils.hasText(evidenceWriter)) {
            usedWriters.add(evidenceWriter);
        }

        Map<String, RelatedScoreBreakdown> selectedBreakdowns = new LinkedHashMap<>();

        for (ScoredRelatedCandidate candidate : scored) {
            if (candidate == null || candidate.candidate == null || candidate.breakdown == null) {
                continue;
            }
            if (selected.size() >= 1 + supportingLimit) {
                break;
            }
            if (candidate.breakdown.total < threshold) {
                break;
            }

            String titleKey = normalizeTitleKey(candidate.candidate.title);
            if (StringUtils.hasText(titleKey) && usedTitleKeys.contains(titleKey)) {
                continue;
            }

            String writerKey = normalizeWriterKey(candidate.candidate.writer);
            if (StringUtils.hasText(writerKey) && usedWriters.contains(writerKey)) {
                continue;
            }

            selected.add(toRelatedPostDto(candidate.candidate));
            selectedBreakdowns.put(candidate.candidate.sourceId, candidate.breakdown);
            if (StringUtils.hasText(titleKey)) {
                usedTitleKeys.add(titleKey);
            }
            if (StringUtils.hasText(writerKey)) {
                usedWriters.add(writerKey);
            }
        }

        String notice = null;
        if (selected.size() < maxLinks) {
            notice = "관련 글이 부족합니다.";
        }
        return new RelatedPostsSelection(selected, notice, evidence.sourceId, selectedBreakdowns, false, Collections.emptyMap());
    }

    private Map<String, RelatedPostCandidate> buildRelatedCandidatePool(RagRetrieval ragRetrieval,
                                                                        List<CandidatePost> keywordCandidates) {
        LinkedHashMap<String, RelatedPostCandidate> pool = new LinkedHashMap<>();

        if (ragRetrieval != null) {
            List<RagEvidence> ragEvidence = buildRagEvidence(ragRetrieval.matches);
            for (RagEvidence evidence : ragEvidence) {
                if (evidence == null) {
                    continue;
                }
                String boardTitle = normalizeBoardTitle(evidence.boardTitle);
                if (!StringUtils.hasText(boardTitle) || !SAFE_BOARD_TITLE.matcher(boardTitle).matches()) {
                    continue;
                }
                if (isExcludedBoard(boardTitle)) {
                    continue;
                }
                String sourceId = postKey(boardTitle, evidence.postNum);
                RelatedPostCandidate candidate = pool.computeIfAbsent(sourceId,
                        key -> new RelatedPostCandidate(sourceId, boardTitle, evidence.postNum));
                if (!StringUtils.hasText(candidate.title)) {
                    candidate.title = evidence.title;
                }
                if (candidate.regDate == null) {
                    candidate.regDate = evidence.regDate;
                }
                if (!StringUtils.hasText(candidate.url)) {
                    candidate.url = evidence.url;
                }
                if (!StringUtils.hasText(candidate.snippet)) {
                    candidate.snippet = evidence.text;
                }
                candidate.baseScore = Math.max(candidate.baseScore, evidence.score);
            }
        }

        if (keywordCandidates != null) {
            for (CandidatePost post : keywordCandidates) {
                if (post == null || post.post == null) {
                    continue;
                }
                String boardTitle = normalizeBoardTitle(post.boardTitle);
                if (!StringUtils.hasText(boardTitle) || !SAFE_BOARD_TITLE.matcher(boardTitle).matches()) {
                    continue;
                }
                if (isExcludedBoard(boardTitle)) {
                    continue;
                }
                BoardDTO dto = post.post;
                if (dto.getPostNum() <= 0) {
                    continue;
                }
                String sourceId = postKey(boardTitle, dto.getPostNum());
                RelatedPostCandidate candidate = pool.computeIfAbsent(sourceId,
                        key -> new RelatedPostCandidate(sourceId, boardTitle, dto.getPostNum()));
                if (!StringUtils.hasText(candidate.title)) {
                    candidate.title = dto.getTitle();
                }
                if (candidate.regDate == null) {
                    candidate.regDate = dto.getRegDate();
                }
                if (!StringUtils.hasText(candidate.url)) {
                    candidate.url = buildPostUrl(boardTitle, dto.getPostNum());
                }
                String snippet = stripHtmlToText(dto.getContent());
                if (StringUtils.hasText(snippet) && snippet.length() > safeText(candidate.snippet).length()) {
                    candidate.snippet = snippet;
                }
                if (!StringUtils.hasText(candidate.writer)) {
                    candidate.writer = dto.getWriter();
                }
                if (!StringUtils.hasText(candidate.searchTerms)) {
                    candidate.searchTerms = dto.getSearchTerms();
                }
                candidate.baseScore = Math.max(candidate.baseScore, post.score);
            }
        }

        return pool;
    }

    private static RelatedPostCandidate pickEvidenceCandidate(List<String> usedPostIds, Map<String, RelatedPostCandidate> pool) {
        if (usedPostIds == null || usedPostIds.isEmpty() || pool == null || pool.isEmpty()) {
            return null;
        }
        for (String sourceId : usedPostIds) {
            if (!StringUtils.hasText(sourceId)) {
                continue;
            }
            RelatedPostCandidate candidate = pool.get(sourceId);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private List<String> buildDbKeywords(List<String> expandedTerms) {
        if (expandedTerms == null || expandedTerms.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String term : expandedTerms) {
            if (!StringUtils.hasText(term)) {
                continue;
            }
            String normalized = term.trim().toLowerCase(Locale.ROOT);
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            String[] tokens = normalized.split("[^\\p{L}\\p{N}]+");
            for (String token : tokens) {
                if (!StringUtils.hasText(token)) {
                    continue;
                }
                String cleaned = normalizeKeywordToken(token);
                if (!StringUtils.hasText(cleaned) || cleaned.length() < 2) {
                    continue;
                }
                if (STOPWORDS.contains(cleaned)) {
                    continue;
                }
                unique.add(cleaned);
                if (unique.size() >= 12) {
                    break;
                }
            }
            if (unique.size() >= 12) {
                break;
            }
        }
        return new ArrayList<>(unique);
    }

    private void expandBoostedBoardCandidates(Map<String, RelatedPostCandidate> pool,
                                             RelatedPostCandidate evidence,
                                             List<String> keywords,
                                             Map<String, Double> boardWeights) {
        if (pool == null || evidence == null || !StringUtils.hasText(evidence.boardTitle)) {
            return;
        }
        if (keywords == null || keywords.isEmpty()) {
            return;
        }

        String evidenceBoard = normalizeBoardTitle(evidence.boardTitle);
        addExtraBoardCandidates(pool, evidenceBoard, keywords, boardWeights, 15);

        String boostedBoard = findTopBoostedBoard(boardWeights, evidenceBoard);
        if (StringUtils.hasText(boostedBoard) && !boostedBoard.equals(evidenceBoard)) {
            addExtraBoardCandidates(pool, boostedBoard, keywords, boardWeights, 10);
        }
    }

    private void addExtraBoardCandidates(Map<String, RelatedPostCandidate> pool,
                                         String boardTitle,
                                         List<String> keywords,
                                         Map<String, Double> boardWeights,
                                         int limit) {
        String normalizedBoardTitle = normalizeBoardTitle(boardTitle);
        if (!StringUtils.hasText(normalizedBoardTitle) || !SAFE_BOARD_TITLE.matcher(normalizedBoardTitle).matches()) {
            return;
        }
        if (isExcludedBoard(normalizedBoardTitle)) {
            return;
        }
        int resolvedLimit = Math.max(1, limit);
        try {
            List<BoardDTO> posts = boardMapper.searchPostsByKeywords(normalizedBoardTitle, keywords, resolvedLimit);
            if (posts == null || posts.isEmpty()) {
                return;
            }
            double boardWeight = resolveBoardWeight(normalizedBoardTitle, boardWeights);
            for (BoardDTO post : posts) {
                if (post == null || post.getPostNum() <= 0) {
                    continue;
                }
                String sourceId = postKey(normalizedBoardTitle, post.getPostNum());
                RelatedPostCandidate candidate = pool.computeIfAbsent(sourceId,
                        key -> new RelatedPostCandidate(sourceId, normalizedBoardTitle, post.getPostNum()));

                if (!StringUtils.hasText(candidate.title)) {
                    candidate.title = post.getTitle();
                }
                if (candidate.regDate == null) {
                    candidate.regDate = post.getRegDate();
                }
                if (!StringUtils.hasText(candidate.url)) {
                    candidate.url = buildPostUrl(normalizedBoardTitle, post.getPostNum());
                }
                String snippet = stripHtmlToText(post.getContent());
                if (StringUtils.hasText(snippet) && snippet.length() > safeText(candidate.snippet).length()) {
                    candidate.snippet = snippet;
                }
                if (!StringUtils.hasText(candidate.writer)) {
                    candidate.writer = post.getWriter();
                }
                if (!StringUtils.hasText(candidate.searchTerms)) {
                    candidate.searchTerms = post.getSearchTerms();
                }

                String titleLower = safeLower(post.getTitle());
                String contentLower = safeLower(stripHtmlToText(post.getContent()));
                String searchTermsLower = safeLower(post.getSearchTerms());
                double baseScore = scoreCandidate(titleLower, contentLower, searchTermsLower, keywords) * boardWeight;
                candidate.baseScore = Math.max(candidate.baseScore, baseScore);
            }
        } catch (Exception ignored) {
            // ignore broken boards
        }
    }

    private static String findTopBoostedBoard(Map<String, Double> boardWeights, String excludedBoardTitle) {
        if (boardWeights == null || boardWeights.isEmpty()) {
            return "";
        }
        String excluded = normalizeBoardTitle(excludedBoardTitle);
        String best = "";
        double bestWeight = 1.05;
        for (Map.Entry<String, Double> entry : boardWeights.entrySet()) {
            if (entry == null || !StringUtils.hasText(entry.getKey())) {
                continue;
            }
            double weight = sanitizeWeight(entry.getValue());
            if (weight <= bestWeight) {
                continue;
            }
            String boardTitle = normalizeBoardTitle(entry.getKey());
            if (!StringUtils.hasText(boardTitle) || boardTitle.equals(excluded)) {
                continue;
            }
            if (!SAFE_BOARD_TITLE.matcher(boardTitle).matches()) {
                continue;
            }
            bestWeight = weight;
            best = boardTitle;
        }
        return best;
    }

    private List<ScoredRelatedCandidate> scoreSupportingCandidates(String query,
                                                                   String answer,
                                                                   List<String> expandedTerms,
                                                                   RelatedPostCandidate evidence,
                                                                   Map<String, RelatedPostCandidate> pool,
                                                                   Map<String, Double> boardWeights) {
        if (pool == null || pool.isEmpty() || evidence == null) {
            return Collections.emptyList();
        }

        Set<String> queryTokens = tokenizeForSimilarity(query);
        Set<String> answerTokens = tokenizeForSimilarity(answer);
        Set<String> expandedTokens = tokenizeForSimilarity(expandedTerms == null ? "" : String.join(" ", expandedTerms));

        List<ScoredRelatedCandidate> scored = new ArrayList<>();
        for (RelatedPostCandidate candidate : pool.values()) {
            if (candidate == null) {
                continue;
            }
            if (candidate.sourceId.equals(evidence.sourceId)) {
                continue;
            }

            Set<String> postTokens = tokenizeForSimilarity(safeText(candidate.title) + " " + safeText(candidate.searchTerms));
            Set<String> snippetTokens = tokenizeForSimilarity(candidate.snippet);

            double querySimilarity = binaryCosineSimilarity(queryTokens, postTokens);
            double answerSimilarity = binaryCosineSimilarity(answerTokens, snippetTokens);
            double keywordOverlap = overlapRatio(expandedTokens, postTokens);

            double boardBonus = computeBoardBonus(candidate.boardTitle, evidence.boardTitle, boardWeights);
            double penalty = computePenalty(candidate);

            double total = 0.35 * querySimilarity + 0.45 * answerSimilarity + 0.20 * keywordOverlap + boardBonus - penalty;

            RelatedScoreBreakdown breakdown = new RelatedScoreBreakdown(
                    querySimilarity,
                    answerSimilarity,
                    keywordOverlap,
                    boardBonus,
                    penalty,
                    total
            );
            scored.add(new ScoredRelatedCandidate(candidate, breakdown));
        }

        scored.sort((a, b) -> {
            if (a == null || b == null) {
                return 0;
            }
            double scoreA = a.breakdown == null ? 0.0 : a.breakdown.total;
            double scoreB = b.breakdown == null ? 0.0 : b.breakdown.total;
            int scoreCompare = Double.compare(scoreB, scoreA);
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            Date dateA = a.candidate == null ? null : a.candidate.regDate;
            Date dateB = b.candidate == null ? null : b.candidate.regDate;
            if (dateA != null && dateB != null) {
                int dateCompare = dateB.compareTo(dateA);
                if (dateCompare != 0) {
                    return dateCompare;
                }
            } else if (dateA != null) {
                return -1;
            } else if (dateB != null) {
                return 1;
            }
            int postNumA = a.candidate == null ? 0 : a.candidate.postNum;
            int postNumB = b.candidate == null ? 0 : b.candidate.postNum;
            return Integer.compare(postNumB, postNumA);
        });
        return scored;
    }

    private RelatedPostsSelection trySelectRelatedPostsWithLlm(String query,
                                                              String answer,
                                                              List<String> usedPostIds,
                                                              RelatedPostCandidate fallbackEvidence,
                                                              List<ScoredRelatedCandidate> scoredCandidates,
                                                              int supportingLimit,
                                                              double threshold,
                                                              Map<String, RelatedPostCandidate> candidatePool) {
        if (assistantProperties == null || !assistantProperties.isLlmRelatedPostsEnabled()) {
            return null;
        }
        if (!StringUtils.hasText(query) || !StringUtils.hasText(answer)) {
            return null;
        }
        if (fallbackEvidence == null || candidatePool == null || candidatePool.isEmpty()) {
            return null;
        }
        if (usedPostIds == null || usedPostIds.isEmpty()) {
            return null;
        }
        if (scoredCandidates == null || scoredCandidates.isEmpty()) {
            return null;
        }

        List<String> evidenceCandidates = new ArrayList<>();
        for (String sourceId : usedPostIds) {
            if (!StringUtils.hasText(sourceId)) {
                continue;
            }
            if (candidatePool.containsKey(sourceId)) {
                evidenceCandidates.add(sourceId);
            }
        }
        if (evidenceCandidates.isEmpty()) {
            return null;
        }

        int topN = Math.max(0, assistantProperties.getLlmRelatedPostsTopN());
        if (topN <= 0) {
            return null;
        }
        int limit = Math.min(topN, scoredCandidates.size());
        if (limit <= 0) {
            return null;
        }
        List<ScoredRelatedCandidate> topCandidates = scoredCandidates.subList(0, limit);

        String normalizedQuery = normalizeLlmKey(query);
        String normalizedAnswer = normalizeLlmKey(answer);
        if (normalizedAnswer.length() > 200) {
            normalizedAnswer = normalizedAnswer.substring(0, 200);
        }

        String cacheKey = buildLlmRelatedPostsCacheKey(normalizedQuery, normalizedAnswer, evidenceCandidates, topCandidates);
        int cacheSeconds = Math.max(0, assistantProperties.getLlmRelatedPostsCacheSeconds());
        LlmRelatedPostsSelection cached = llmRelatedPostsCache.get(cacheKey, cacheSeconds);
        if (cached != null) {
            return applyLlmRelatedPostsSelection(cached, fallbackEvidence, topCandidates, supportingLimit, threshold, candidatePool);
        }

        int rateLimitPerMinute = Math.max(0, assistantProperties.getLlmRelatedPostsRateLimitPerMinute());
        if (llmRelatedPostsRateLimiter.tryAcquire(rateLimitPerMinute)) {
            try {
                String prompt = buildLlmRelatedPostsPrompt(query, answer, evidenceCandidates, topCandidates);
                String raw = geminiClient.generateAnswer(prompt);
                LlmRelatedPostsSelection selection = parseLlmRelatedPostsSelection(raw);
                selection = sanitizeLlmRelatedPostsSelection(selection, evidenceCandidates, topCandidates);
                if (selection == null) {
                    return null;
                }
                llmRelatedPostsCache.put(cacheKey, selection, cacheSeconds);
                return applyLlmRelatedPostsSelection(selection, fallbackEvidence, topCandidates, supportingLimit, threshold, candidatePool);
            } catch (GeminiException e) {
                log.debug("LLM relatedPosts 선택 실패(GeminiException): {}", e.getMessage());
                return null;
            } catch (Exception e) {
                log.debug("LLM relatedPosts 선택 실패", e);
                return null;
            }
        }
        return null;
    }

    private String buildLlmRelatedPostsCacheKey(String normalizedQuery,
                                                String normalizedAnswer,
                                                List<String> evidenceCandidates,
                                                List<ScoredRelatedCandidate> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("relposts:").append(LLM_RELATED_POSTS_CACHE_VERSION).append(':');
        sb.append(normalizedQuery).append(':').append(normalizedAnswer).append(':');
        if (evidenceCandidates != null) {
            for (String id : evidenceCandidates) {
                if (StringUtils.hasText(id)) {
                    sb.append(id).append(',');
                }
            }
        }
        sb.append(':');
        if (candidates != null) {
            for (ScoredRelatedCandidate candidate : candidates) {
                if (candidate == null || candidate.candidate == null) {
                    continue;
                }
                sb.append(candidate.candidate.sourceId).append(',');
            }
        }
        return sb.toString();
    }

    private String buildLlmRelatedPostsPrompt(String query,
                                             String answer,
                                             List<String> evidenceCandidates,
                                             List<ScoredRelatedCandidate> candidates) {
        int excerptChars = Math.max(80, assistantProperties.getLlmRelatedPostsExcerptChars());
        StringBuilder sb = new StringBuilder();
        sb.append("You are a strict JSON selector for SC1Hub related posts.\n");
        sb.append("Return JSON only. Do not include markdown or explanations.\n\n");
        sb.append("Task:\n");
        sb.append("- Choose 1 evidence_post_id that best supports the answer.\n");
        sb.append("- Choose up to 2 supporting_post_ids that are highly relevant to both the question and the answer.\n\n");
        sb.append("Rules:\n");
        sb.append("- evidence_post_id must be one of evidence_candidates.\n");
        sb.append("- supporting_post_ids must be a subset of supporting_candidates and must not include evidence_post_id.\n");
        sb.append("- If there are not enough relevant supporting posts, return an empty array or 1 item.\n");
        sb.append("- reasons must be a JSON object with short Korean explanations keyed by id.\n\n");
        sb.append("Question: ").append(safeText(query)).append("\n\n");
        sb.append("Answer: ").append(safeText(answer)).append("\n\n");
        sb.append("evidence_candidates: ").append(evidenceCandidates == null ? "[]" : evidenceCandidates).append("\n\n");
        sb.append("supporting_candidates:\n");
        if (candidates != null) {
            for (ScoredRelatedCandidate candidate : candidates) {
                if (candidate == null || candidate.candidate == null) {
                    continue;
                }
                RelatedPostCandidate post = candidate.candidate;
                String snippet = truncate(safeText(post.snippet), excerptChars);
                sb.append("- id=").append(post.sourceId).append(", ");
                sb.append("board=").append(post.boardTitle).append(", ");
                sb.append("title=").append(safeText(post.title)).append("\n");
                sb.append("  snippet=").append(snippet).append("\n");
            }
        }
        sb.append("\nOutput schema:\n");
        sb.append("{\"evidence_post_id\":\"pvszboard:1\",\"supporting_post_ids\":[\"pvszboard:2\",\"pvszboard:3\"],\"reasons\":{\"pvszboard:1\":\"...\"}}\n");
        return sb.toString();
    }

    private LlmRelatedPostsSelection parseLlmRelatedPostsSelection(String raw) {
        if (!StringUtils.hasText(raw) || objectMapper == null) {
            return null;
        }
        String json = extractFirstJsonObject(raw);
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            LlmRelatedPostsSelection result = new LlmRelatedPostsSelection();
            result.evidencePostId = textOrNull(node.get("evidence_post_id"));
            if (!StringUtils.hasText(result.evidencePostId)) {
                result.evidencePostId = textOrNull(node.get("evidencePostId"));
            }
            result.supportingPostIds = new ArrayList<>();
            JsonNode supportingNode = node.get("supporting_post_ids");
            if (supportingNode == null || supportingNode.isMissingNode()) {
                supportingNode = node.get("supportingPostIds");
            }
            if (supportingNode != null && supportingNode.isArray()) {
                for (JsonNode item : supportingNode) {
                    String id = normalizeSourceId(textOrNull(item));
                    if (StringUtils.hasText(id)) {
                        result.supportingPostIds.add(id);
                    }
                }
            }
            result.reasons = new LinkedHashMap<>();
            JsonNode reasonsNode = node.get("reasons");
            if (reasonsNode != null && reasonsNode.isObject()) {
                Iterator<String> fields = reasonsNode.fieldNames();
                while (fields.hasNext()) {
                    String key = fields.next();
                    String id = normalizeSourceId(key);
                    String reason = textOrNull(reasonsNode.get(key));
                    if (StringUtils.hasText(id) && StringUtils.hasText(reason)) {
                        result.reasons.put(id, reason.trim());
                    }
                }
            }
            result.evidencePostId = normalizeSourceId(result.evidencePostId);
            if (!StringUtils.hasText(result.evidencePostId) && result.supportingPostIds.isEmpty()) {
                return null;
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private LlmRelatedPostsSelection sanitizeLlmRelatedPostsSelection(LlmRelatedPostsSelection selection,
                                                                     List<String> evidenceCandidates,
                                                                     List<ScoredRelatedCandidate> supportingCandidates) {
        if (selection == null) {
            return null;
        }
        LinkedHashSet<String> evidenceAllowed = new LinkedHashSet<>();
        if (evidenceCandidates != null) {
            for (String id : evidenceCandidates) {
                String normalized = normalizeSourceId(id);
                if (StringUtils.hasText(normalized)) {
                    evidenceAllowed.add(normalized);
                }
            }
        }
        if (evidenceAllowed.isEmpty()) {
            return null;
        }

        String evidenceId = normalizeSourceId(selection.evidencePostId);
        if (!evidenceAllowed.contains(evidenceId)) {
            evidenceId = evidenceAllowed.iterator().next();
        }

        LinkedHashSet<String> supportAllowed = new LinkedHashSet<>();
        if (supportingCandidates != null) {
            for (ScoredRelatedCandidate candidate : supportingCandidates) {
                if (candidate == null || candidate.candidate == null) {
                    continue;
                }
                supportAllowed.add(candidate.candidate.sourceId);
            }
        }
        if (supportAllowed.isEmpty()) {
            return null;
        }

        LinkedHashSet<String> sanitizedSupport = new LinkedHashSet<>();
        if (selection.supportingPostIds != null) {
            for (String id : selection.supportingPostIds) {
                String normalized = normalizeSourceId(id);
                if (!StringUtils.hasText(normalized)) {
                    continue;
                }
                if (normalized.equals(evidenceId)) {
                    continue;
                }
                if (!supportAllowed.contains(normalized)) {
                    continue;
                }
                sanitizedSupport.add(normalized);
                if (sanitizedSupport.size() >= 2) {
                    break;
                }
            }
        }

        LlmRelatedPostsSelection sanitized = new LlmRelatedPostsSelection();
        sanitized.evidencePostId = evidenceId;
        sanitized.supportingPostIds = new ArrayList<>(sanitizedSupport);
        sanitized.reasons = selection.reasons == null ? Collections.emptyMap() : selection.reasons;
        return sanitized;
    }

    private RelatedPostsSelection applyLlmRelatedPostsSelection(LlmRelatedPostsSelection selection,
                                                               RelatedPostCandidate fallbackEvidence,
                                                               List<ScoredRelatedCandidate> scoredCandidates,
                                                               int supportingLimit,
                                                               double threshold,
                                                               Map<String, RelatedPostCandidate> candidatePool) {
        if (selection == null || fallbackEvidence == null || candidatePool == null || candidatePool.isEmpty()) {
            return null;
        }
        RelatedPostCandidate evidence = candidatePool.get(selection.evidencePostId);
        if (evidence == null) {
            evidence = fallbackEvidence;
        }

        Map<String, RelatedScoreBreakdown> breakdownById = new HashMap<>();
        if (scoredCandidates != null) {
            for (ScoredRelatedCandidate candidate : scoredCandidates) {
                if (candidate == null || candidate.candidate == null || candidate.breakdown == null) {
                    continue;
                }
                breakdownById.put(candidate.candidate.sourceId, candidate.breakdown);
            }
        }

        List<AssistantRelatedPostDTO> selected = new ArrayList<>(1 + Math.max(0, supportingLimit));
        selected.add(toRelatedPostDto(evidence));

        Set<String> usedTitleKeys = new HashSet<>();
        String evidenceTitleKey = normalizeTitleKey(evidence.title);
        if (StringUtils.hasText(evidenceTitleKey)) {
            usedTitleKeys.add(evidenceTitleKey);
        }
        Set<String> usedWriters = new HashSet<>();
        String evidenceWriter = normalizeWriterKey(evidence.writer);
        if (StringUtils.hasText(evidenceWriter)) {
            usedWriters.add(evidenceWriter);
        }

        Map<String, RelatedScoreBreakdown> selectedBreakdowns = new LinkedHashMap<>();
        List<String> supportIds = selection.supportingPostIds == null ? Collections.emptyList() : selection.supportingPostIds;
        for (String id : supportIds) {
            if (selected.size() >= 1 + Math.max(0, supportingLimit)) {
                break;
            }
            if (!StringUtils.hasText(id) || id.equals(evidence.sourceId)) {
                continue;
            }
            RelatedPostCandidate candidate = candidatePool.get(id);
            if (candidate == null) {
                continue;
            }
            RelatedScoreBreakdown breakdown = breakdownById.get(id);
            if (breakdown != null && breakdown.total < threshold) {
                continue;
            }
            String titleKey = normalizeTitleKey(candidate.title);
            if (StringUtils.hasText(titleKey) && usedTitleKeys.contains(titleKey)) {
                continue;
            }
            String writerKey = normalizeWriterKey(candidate.writer);
            if (StringUtils.hasText(writerKey) && usedWriters.contains(writerKey)) {
                continue;
            }

            selected.add(toRelatedPostDto(candidate));
            if (breakdown != null) {
                selectedBreakdowns.put(id, breakdown);
            }
            if (StringUtils.hasText(titleKey)) {
                usedTitleKeys.add(titleKey);
            }
            if (StringUtils.hasText(writerKey)) {
                usedWriters.add(writerKey);
            }
        }

        String notice = null;
        int maxLinks = Math.min(3, Math.max(0, assistantProperties.getMaxRelatedPosts()));
        if (selected.size() < maxLinks) {
            notice = "관련 글이 부족합니다.";
        }
        Map<String, String> reasons = selection.reasons == null ? Collections.emptyMap() : selection.reasons;
        return new RelatedPostsSelection(selected, notice, evidence.sourceId, selectedBreakdowns, true, reasons);
    }

    private static double sanitizeRelatedThreshold(double threshold) {
        if (Double.isNaN(threshold) || Double.isInfinite(threshold)) {
            return 0.0;
        }
        if (threshold <= 0.0) {
            return 0.0;
        }
        return Math.min(threshold, 2.0);
    }

    private static Set<String> tokenizeForSimilarity(String text) {
        if (!StringUtils.hasText(text)) {
            return Collections.emptySet();
        }
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        if (!StringUtils.hasText(normalized)) {
            return Collections.emptySet();
        }
        String[] tokens = normalized.split("\\s+");
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String token : tokens) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            String cleaned = normalizeKeywordToken(token);
            if (!StringUtils.hasText(cleaned) || cleaned.length() < 2) {
                continue;
            }
            if (STOPWORDS.contains(cleaned)) {
                continue;
            }
            result.add(cleaned);
            if (result.size() >= 80) {
                break;
            }
        }
        return result;
    }

    private static double binaryCosineSimilarity(Set<String> a, Set<String> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        int intersection = 0;
        for (String token : a) {
            if (b.contains(token)) {
                intersection += 1;
            }
        }
        double denom = Math.sqrt((double) a.size() * (double) b.size());
        if (denom == 0.0) {
            return 0.0;
        }
        return intersection / denom;
    }

    private static double overlapRatio(Set<String> a, Set<String> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        int intersection = 0;
        for (String token : a) {
            if (b.contains(token)) {
                intersection += 1;
            }
        }
        return intersection / (double) a.size();
    }

    private static double computeBoardBonus(String candidateBoardTitle,
                                            String evidenceBoardTitle,
                                            Map<String, Double> boardWeights) {
        String candidate = normalizeBoardTitle(candidateBoardTitle);
        String evidence = normalizeBoardTitle(evidenceBoardTitle);
        double bonus = 0.0;
        if (StringUtils.hasText(candidate) && StringUtils.hasText(evidence) && candidate.equals(evidence)) {
            bonus += 0.15;
        }
        double weight = resolveBoardWeight(candidate, boardWeights);
        if (weight > 1.05) {
            bonus += Math.min(0.15, (weight - 1.0) * 0.10);
        }
        return bonus;
    }

    private static double computePenalty(RelatedPostCandidate candidate) {
        if (candidate == null) {
            return 0.0;
        }
        String title = safeText(candidate.title);
        String snippet = safeText(candidate.snippet);

        double penalty = 0.0;
        if (!StringUtils.hasText(snippet)) {
            penalty += 0.25;
        } else if (snippet.length() < 60) {
            penalty += 0.12;
        } else if (snippet.length() < 120) {
            penalty += 0.06;
        }
        if (title.length() < 4) {
            penalty += 0.08;
        }

        String haystack = safeLower(title + " " + snippet);
        if (containsAny(haystack, "광고", "홍보", "카톡", "오픈채팅", "텔레", "t.me")) {
            penalty += 0.12;
        }
        return penalty;
    }

    private static String normalizeTitleKey(String title) {
        if (!StringUtils.hasText(title)) {
            return "";
        }
        return title.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    private static String normalizeWriterKey(String writer) {
        if (!StringUtils.hasText(writer)) {
            return "";
        }
        return writer.trim().toLowerCase(Locale.ROOT);
    }

    private static AssistantRelatedPostDTO toRelatedPostDto(RelatedPostCandidate candidate) {
        AssistantRelatedPostDTO dto = new AssistantRelatedPostDTO();
        if (candidate == null) {
            return dto;
        }
        dto.setBoardTitle(normalizeBoardTitle(candidate.boardTitle));
        dto.setPostNum(candidate.postNum);
        dto.setTitle(candidate.title);
        dto.setRegDate(candidate.regDate);
        String url = StringUtils.hasText(candidate.url) ? candidate.url : buildPostUrl(normalizeBoardTitle(candidate.boardTitle), candidate.postNum);
        dto.setUrl(url);
        return dto;
    }

    private boolean isFactQuery(String message, List<String> keywords) {
        if (!StringUtils.hasText(message) && (keywords == null || keywords.isEmpty())) {
            return false;
        }
        String compact = safeLower(message).replaceAll("\\s+", "");
        if (StringUtils.hasText(compact)) {
            for (String keyword : FACT_KEYWORDS) {
                if (!StringUtils.hasText(keyword)) {
                    continue;
                }
                if (compact.contains(keyword.replaceAll("\\s+", ""))) {
                    return true;
                }
            }
        }
        if (keywords != null) {
            for (String keyword : keywords) {
                if (!StringUtils.hasText(keyword)) {
                    continue;
                }
                if (FACT_KEYWORDS.contains(keyword)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<String> resolveFactBoards() {
        if (assistantProperties == null) {
            return Collections.emptySet();
        }
        List<String> boards = assistantProperties.getFactBoards();
        if (boards == null || boards.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String board : boards) {
            if (!StringUtils.hasText(board)) {
                continue;
            }
            normalized.add(normalizeBoardTitle(board));
        }
        return normalized;
    }

    private List<AssistantRagSearchService.Match> preferFactBoardMatches(List<AssistantRagSearchService.Match> matches) {
        if (matches == null || matches.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> factBoards = resolveFactBoards();
        if (factBoards.isEmpty()) {
            return matches;
        }
        List<AssistantRagSearchService.Match> preferred = new ArrayList<>();
        for (AssistantRagSearchService.Match match : matches) {
            if (match == null || match.getChunk() == null) {
                continue;
            }
            String boardTitle = normalizeBoardTitle(match.getChunk().getBoardTitle());
            if (factBoards.contains(boardTitle)) {
                preferred.add(match);
            }
        }
        return preferred.isEmpty() ? matches : preferred;
    }

    private boolean isExcludedBoard(String boardTitle) {
        if (!StringUtils.hasText(boardTitle)) {
            return false;
        }
        if (assistantProperties == null || assistantProperties.getExcludedBoards() == null || assistantProperties.getExcludedBoards().isEmpty()) {
            return false;
        }
        String normalized = normalizeBoardTitle(boardTitle);
        for (String excluded : assistantProperties.getExcludedBoards()) {
            if (!StringUtils.hasText(excluded)) {
                continue;
            }
            if (normalizeBoardTitle(excluded).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static String buildPostUrl(String boardTitle, int postNum) {
        return "/boards/" + boardTitle + "/readPost?postNum=" + postNum;
    }

    private static String postKey(String boardTitle, int postNum) {
        return normalizeBoardTitle(boardTitle) + ":" + postNum;
    }

    private static String normalizeBoardTitle(String boardTitle) {
        if (boardTitle == null) {
            return "";
        }
        return boardTitle.trim().toLowerCase(Locale.ROOT);
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String safeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private static boolean containsAny(String haystack, String... needles) {
        if (!StringUtils.hasText(haystack) || needles == null) {
            return false;
        }
        for (String needle : needles) {
            if (!StringUtils.hasText(needle)) {
                continue;
            }
            String normalized = safeLower(needle.trim());
            if (StringUtils.hasText(normalized) && haystack.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private String buildRagQuery(String message, List<String> expandedTerms, boolean aliasMatched) {
        if (!aliasMatched || expandedTerms == null || expandedTerms.isEmpty()) {
            return message == null ? "" : message;
        }
        String base = message == null ? "" : message.trim();
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String term : expandedTerms) {
            if (!StringUtils.hasText(term)) {
                continue;
            }
            String cleaned = term.trim();
            if (!cleaned.isEmpty()) {
                terms.add(cleaned);
            }
        }
        if (terms.isEmpty()) {
            return base;
        }
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(base)) {
            sb.append(base);
        }
        for (String term : terms) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(term);
            if (sb.length() >= 8000) {
                break;
            }
        }
        return sb.toString();
    }

    private static String truncate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        if (maxChars <= 0) {
            return "";
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars - 1)).trim() + "…";
    }

    private static List<String> extractKeywords(String message) {
        if (!StringUtils.hasText(message)) {
            return Collections.emptyList();
        }
        String normalized = message
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();

        if (normalized.isEmpty()) {
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

    private void logAnswerGroundedRelatedPosts(String query,
                                               AssistantQueryParseResult parseResult,
                                               List<String> expandedTerms,
                                               List<String> usedPostIds,
                                               RelatedPostsSelection selection) {
        if (selection == null) {
            return;
        }
        try {
            Map<String, Object> logMap = new LinkedHashMap<>();
            logMap.put("query", safeText(query));
            if (parseResult != null) {
                logMap.put("intent", parseResult.getIntent());
                logMap.put("matchup", parseResult.getMatchup());
                logMap.put("playerRace", parseResult.getPlayerRace());
                logMap.put("opponentRace", parseResult.getOpponentRace());
                logMap.put("confidence", parseResult.getConfidence());
                logMap.put("aliasMatched", parseResult.isAliasMatched());
            }
            if (expandedTerms != null && !expandedTerms.isEmpty()) {
                logMap.put("expandedTerms", expandedTerms);
            }
            if (usedPostIds != null && !usedPostIds.isEmpty()) {
                logMap.put("usedPostIds", usedPostIds);
            }
            logMap.put("evidencePostId", selection.evidenceSourceId);
            if (StringUtils.hasText(selection.evidenceSourceId)) {
                String evidenceReason = selection.llmSelected
                        ? safeText(selection.llmReasons.get(selection.evidenceSourceId))
                        : "citations";
                if (StringUtils.hasText(evidenceReason)) {
                    logMap.put("evidenceReason", evidenceReason);
                }
            }
            logMap.put("llmSelected", selection.llmSelected);
            if (selection.llmSelected && !selection.llmReasons.isEmpty()) {
                logMap.put("llmReasons", selection.llmReasons);
            }
            logMap.put("notice", selection.notice);

            List<Map<String, Object>> supporting = new ArrayList<>();
            if (!selection.supportingBreakdowns.isEmpty()) {
                for (Map.Entry<String, RelatedScoreBreakdown> entry : selection.supportingBreakdowns.entrySet()) {
                    if (entry == null || entry.getValue() == null) {
                        continue;
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("postId", entry.getKey());
                    RelatedScoreBreakdown b = entry.getValue();
                    item.put("querySimilarity", b.querySimilarity);
                    item.put("answerSimilarity", b.answerSimilarity);
                    item.put("keywordOverlap", b.keywordOverlap);
                    item.put("boardBonus", b.boardBonus);
                    item.put("penalty", b.penalty);
                    item.put("total", b.total);
                    supporting.add(item);
                }
            }
            logMap.put("supportingScores", supporting);

            List<Map<String, Object>> finalLinks = new ArrayList<>();
            int index = 0;
            for (AssistantRelatedPostDTO post : selection.relatedPosts) {
                if (post == null) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                String boardTitle = normalizeBoardTitle(post.getBoardTitle());
                int postNum = post.getPostNum();
                item.put("postId", postKey(boardTitle, postNum));
                item.put("boardTitle", boardTitle);
                item.put("postNum", postNum);
                item.put("title", post.getTitle());
                if (index == 0) {
                    item.put("role", "evidence");
                } else {
                    item.put("role", "supporting_" + index);
                }
                finalLinks.add(item);
                index += 1;
            }
            logMap.put("finalLinks", finalLinks);

            if (objectMapper != null) {
                log.info("assistant.related_posts {}", objectMapper.writeValueAsString(logMap));
            } else {
                log.info("assistant.related_posts {}", logMap);
            }
        } catch (Exception e) {
            log.debug("assistant.related_posts 로깅 실패", e);
        }
    }

    private void logParserResult(AssistantQueryParseResult parseResult, List<String> expandedTerms) {
        if (parseResult == null) {
            return;
        }
        try {
            String json = objectMapper == null ? parseResult.toString() : objectMapper.writeValueAsString(parseResult);
            log.info("검색 파서 결과={}", json);
        } catch (Exception e) {
            log.info("검색 파서 결과 로깅 실패: {}", parseResult);
        }
        if (expandedTerms != null && !expandedTerms.isEmpty()) {
            log.info("확장 검색어={}", expandedTerms);
        }
    }

    private void logRagMatches(List<AssistantRagSearchService.Match> matches) {
        if (matches == null || matches.isEmpty()) {
            return;
        }
        List<String> top = new ArrayList<>();
        int limit = Math.min(10, matches.size());
        for (int i = 0; i < limit; i += 1) {
            AssistantRagSearchService.Match match = matches.get(i);
            if (match == null || match.getChunk() == null) {
                continue;
            }
            AssistantRagChunk chunk = match.getChunk();
            String boardTitle = normalizeBoardTitle(chunk.getBoardTitle());
            top.add(boardTitle + ":" + chunk.getPostNum() + ":" + String.format(Locale.ROOT, "%.4f", match.getScore()));
        }
        if (!top.isEmpty()) {
            log.info("RAG 상위 문서={}", top);
        }
    }

    private void logKeywordCandidates(List<CandidatePost> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        List<String> top = new ArrayList<>();
        int limit = Math.min(10, candidates.size());
        for (int i = 0; i < limit; i += 1) {
            CandidatePost post = candidates.get(i);
            if (post == null || post.post == null) {
                continue;
            }
            String boardTitle = normalizeBoardTitle(post.boardTitle);
            top.add(boardTitle + ":" + post.post.getPostNum() + ":" + String.format(Locale.ROOT, "%.2f", post.score));
        }
        if (!top.isEmpty()) {
            log.info("키워드 상위 문서={}", top);
        }
    }

    private static final class CandidatePost {
        private final String boardTitle;
        private final BoardDTO post;
        private final double score;

        private CandidatePost(String boardTitle, BoardDTO post, double score) {
            this.boardTitle = boardTitle;
            this.post = post;
            this.score = score;
        }
    }

    private static final class RelatedPostsSelection {
        private final List<AssistantRelatedPostDTO> relatedPosts;
        private final String notice;
        private final String evidenceSourceId;
        private final Map<String, RelatedScoreBreakdown> supportingBreakdowns;
        private final boolean llmSelected;
        private final Map<String, String> llmReasons;

        private RelatedPostsSelection(List<AssistantRelatedPostDTO> relatedPosts,
                                      String notice,
                                      String evidenceSourceId,
                                      Map<String, RelatedScoreBreakdown> supportingBreakdowns,
                                      boolean llmSelected,
                                      Map<String, String> llmReasons) {
            this.relatedPosts = relatedPosts == null ? Collections.emptyList() : relatedPosts;
            this.notice = notice;
            this.evidenceSourceId = evidenceSourceId;
            this.supportingBreakdowns = supportingBreakdowns == null ? Collections.emptyMap() : supportingBreakdowns;
            this.llmSelected = llmSelected;
            this.llmReasons = llmReasons == null ? Collections.emptyMap() : llmReasons;
        }

        private static RelatedPostsSelection empty(String notice) {
            return new RelatedPostsSelection(Collections.emptyList(), notice, null, Collections.emptyMap(), false, Collections.emptyMap());
        }
    }

    private static final class RelatedPostCandidate {
        private final String sourceId;
        private final String boardTitle;
        private final int postNum;
        private String title;
        private Date regDate;
        private String url;
        private String snippet;
        private String writer;
        private String searchTerms;
        private double baseScore;

        private RelatedPostCandidate(String sourceId, String boardTitle, int postNum) {
            this.sourceId = sourceId;
            this.boardTitle = boardTitle;
            this.postNum = postNum;
        }
    }

    private static final class RelatedScoreBreakdown {
        private final double querySimilarity;
        private final double answerSimilarity;
        private final double keywordOverlap;
        private final double boardBonus;
        private final double penalty;
        private final double total;

        private RelatedScoreBreakdown(double querySimilarity,
                                      double answerSimilarity,
                                      double keywordOverlap,
                                      double boardBonus,
                                      double penalty,
                                      double total) {
            this.querySimilarity = querySimilarity;
            this.answerSimilarity = answerSimilarity;
            this.keywordOverlap = keywordOverlap;
            this.boardBonus = boardBonus;
            this.penalty = penalty;
            this.total = total;
        }
    }

    private static final class ScoredRelatedCandidate {
        private final RelatedPostCandidate candidate;
        private final RelatedScoreBreakdown breakdown;

        private ScoredRelatedCandidate(RelatedPostCandidate candidate, RelatedScoreBreakdown breakdown) {
            this.candidate = candidate;
            this.breakdown = breakdown;
        }
    }

    private static final class RagRetrieval {
        private final List<AssistantRagSearchService.Match> matches;

        private RagRetrieval(List<AssistantRagSearchService.Match> matches) {
            this.matches = matches;
        }
    }

    private static final class RagEvidence {
        private final String boardTitle;
        private final int postNum;
        private final String title;
        private final Date regDate;
        private final String url;
        private final String text;
        private final double score;

        private RagEvidence(String boardTitle, int postNum, String title, Date regDate, String url, String text, double score) {
            this.boardTitle = boardTitle;
            this.postNum = postNum;
            this.title = title;
            this.regDate = regDate;
            this.url = url;
            this.text = text;
            this.score = score;
        }
    }

    private static final class LlmMatchupIntent {
        private String intent;
        private String playerRace;
        private String opponentRace;
        private Double confidence;
    }

    private static final class LlmRelatedPostsSelection {
        private String evidencePostId;
        private List<String> supportingPostIds = new ArrayList<>();
        private Map<String, String> reasons = Collections.emptyMap();
    }

    private static final class SimpleRateLimiter {
        private static final long WINDOW_MILLIS = 60_000L;
        private final ArrayDeque<Long> calls = new ArrayDeque<>();

        private synchronized boolean tryAcquire(int maxPerMinute) {
            if (maxPerMinute <= 0) {
                return false;
            }
            long now = System.currentTimeMillis();
            long cutoff = now - WINDOW_MILLIS;
            while (!calls.isEmpty()) {
                Long ts = calls.peekFirst();
                if (ts == null || ts > cutoff) {
                    break;
                }
                calls.pollFirst();
            }
            if (calls.size() >= maxPerMinute) {
                return false;
            }
            calls.addLast(now);
            return true;
        }
    }

    private static final class ExpiringCache<V> {
        private static final int MAX_ENTRIES = 2000;
        private final ConcurrentHashMap<String, Entry<V>> map = new ConcurrentHashMap<>();

        private V get(String key, int cacheSeconds) {
            if (cacheSeconds <= 0 || !StringUtils.hasText(key)) {
                return null;
            }
            Entry<V> entry = map.get(key);
            if (entry == null) {
                return null;
            }
            long now = System.currentTimeMillis();
            if (entry.expiresAtMillis <= now) {
                map.remove(key, entry);
                return null;
            }
            return entry.value;
        }

        private void put(String key, V value, int cacheSeconds) {
            if (cacheSeconds <= 0 || value == null || !StringUtils.hasText(key)) {
                return;
            }
            long now = System.currentTimeMillis();
            long expiresAt = now + (cacheSeconds * 1000L);
            map.put(key, new Entry<>(value, expiresAt));
            if (map.size() > MAX_ENTRIES) {
                prune(now);
            }
        }

        private void prune(long now) {
            for (Map.Entry<String, Entry<V>> entry : map.entrySet()) {
                if (entry == null || entry.getValue() == null) {
                    continue;
                }
                if (entry.getValue().expiresAtMillis <= now) {
                    map.remove(entry.getKey(), entry.getValue());
                }
            }
            if (map.size() <= MAX_ENTRIES) {
                return;
            }
            int toRemove = map.size() - MAX_ENTRIES;
            Iterator<String> it = map.keySet().iterator();
            while (toRemove > 0 && it.hasNext()) {
                String key = it.next();
                map.remove(key);
                toRemove -= 1;
            }
        }

        private static final class Entry<V> {
            private final V value;
            private final long expiresAtMillis;

            private Entry(V value, long expiresAtMillis) {
                this.value = value;
                this.expiresAtMillis = expiresAtMillis;
            }
        }
    }
}
