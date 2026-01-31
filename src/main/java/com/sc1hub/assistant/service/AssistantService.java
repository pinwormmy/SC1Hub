package com.sc1hub.assistant.service;

import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.assistant.config.AssistantRagProperties;
import com.sc1hub.assistant.dto.AssistantChatResponseDTO;
import com.sc1hub.assistant.dto.AssistantRelatedPostDTO;
import com.sc1hub.assistant.gemini.GeminiClient;
import com.sc1hub.assistant.gemini.GeminiException;
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
import java.util.regex.Pattern;

@Service
@Slf4j
public class AssistantService {

    private static final Pattern SAFE_BOARD_TITLE = Pattern.compile("^[a-z0-9_]+$");
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
        RagRetrieval ragRetrieval = tryRetrieveWithRag(normalizedMessage, expandedTerms, boardWeights, factQuery);

        List<CandidatePost> candidates = Collections.emptyList();
        if (shouldLoadKeywordCandidates(expandedTerms, ragRetrieval)) {
            candidates = findCandidates(expandedTerms, boardWeights, resolveCandidateLimit(), factQuery);
        }

        String prompt;
        if (ragRetrieval != null) {
            response.setRelatedPosts(ragRetrieval.relatedPosts);
            if (!candidates.isEmpty()) {
                List<AssistantRelatedPostDTO> merged = mergeRelatedPosts(ragRetrieval.relatedPosts, candidates, assistantProperties.getMaxRelatedPosts());
                if (!merged.isEmpty()) {
                    response.setRelatedPosts(merged);
                }
                prompt = buildHybridPrompt(normalizedMessage, ragRetrieval.matches, candidates);
            } else {
                prompt = buildRagPrompt(normalizedMessage, ragRetrieval.matches);
            }
        } else {
            List<CandidatePost> topRelated = candidates.subList(0, Math.min(assistantProperties.getMaxRelatedPosts(), candidates.size()));
            response.setRelatedPosts(toRelatedPostDtos(topRelated));

            List<CandidatePost> contextPosts = topRelated.subList(0, Math.min(assistantProperties.getContextPosts(), topRelated.size()));
            prompt = buildPrompt(normalizedMessage, contextPosts);
        }

        try {
            String answer = geminiClient.generateAnswer(prompt).trim();
            if (!StringUtils.hasText(answer)) {
                answer = "답변을 생성하지 못했습니다.";
            }
            response.setAnswer(answer);
        } catch (GeminiException e) {
            log.error("Gemini API 호출 실패", e);
            response.setError("AI 설정 또는 API 호출에 실패했습니다. 관리자에게 문의해주세요.");
        } catch (Exception e) {
            log.error("AI 응답 생성 중 오류 발생", e);
            response.setError("AI 응답 생성 중 오류가 발생했습니다.");
        }
        return response;
    }

    private RagRetrieval tryRetrieveWithRag(String normalizedMessage,
                                            List<String> expandedTerms,
                                            Map<String, Double> boardWeights,
                                            boolean preferFactBoards) {
        if (ragSearchService == null || ragProperties == null || !ragSearchService.isEnabled()) {
            return null;
        }
        try {
            List<AssistantRagSearchService.Match> matches = ragSearchService.search(normalizedMessage, ragProperties.getSearchTopChunks());
            if (matches == null || matches.isEmpty()) {
                return null;
            }
            List<AssistantRagSearchService.Match> filtered = filterRagMatches(matches, expandedTerms);
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
            List<AssistantRelatedPostDTO> relatedPosts = buildRelatedPostsFromMatches(weightedMatches, assistantProperties.getMaxRelatedPosts());
            return new RagRetrieval(weightedMatches, relatedPosts);
        } catch (Exception e) {
            log.warn("RAG 검색 실패. 키워드 검색으로 fallback 합니다.", e);
            return null;
        }
    }

    private List<AssistantRagSearchService.Match> filterRagMatches(List<AssistantRagSearchService.Match> matches,
                                                                   List<String> keywords) {
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
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private List<AssistantRelatedPostDTO> buildRelatedPostsFromMatches(List<AssistantRagSearchService.Match> matches, int limit) {
        if (matches == null || matches.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, RagPostAggregate> aggregates = new HashMap<>();
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
            String key = boardTitle + ":" + postNum;

            RagPostAggregate current = aggregates.get(key);
            if (current == null) {
                current = new RagPostAggregate(boardTitle, postNum);
                aggregates.put(key, current);
            }
            current.title = chunk.getTitle();
            current.regDate = chunk.getRegDate();
            current.url = StringUtils.hasText(chunk.getUrl()) ? chunk.getUrl() : buildPostUrl(boardTitle, postNum);
            current.bestScore = Math.max(current.bestScore, match.getScore());
        }

        List<RagPostAggregate> sorted = new ArrayList<>(aggregates.values());
        sorted.sort((a, b) -> {
            int scoreCompare = Double.compare(b.bestScore, a.bestScore);
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

        int max = Math.max(0, limit);
        List<AssistantRelatedPostDTO> result = new ArrayList<>();
        for (int i = 0; i < sorted.size() && i < max; i += 1) {
            RagPostAggregate post = sorted.get(i);
            AssistantRelatedPostDTO dto = new AssistantRelatedPostDTO();
            dto.setBoardTitle(post.boardTitle);
            dto.setPostNum(post.postNum);
            dto.setTitle(post.title);
            dto.setRegDate(post.regDate);
            dto.setUrl(post.url);
            result.add(dto);
        }
        return result;
    }

    private String buildRagPrompt(String message, List<AssistantRagSearchService.Match> matches) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the SC1Hub assistant.\n");
        sb.append("Answer in Korean.\n");
        sb.append("Use only the information provided in 'Site snippets' as factual ground.\n");
        sb.append("If the snippets are insufficient, say you cannot find enough information and suggest checking related posts.\n");
        sb.append("Keep the answer concise (max 5 sentences).\n");
        sb.append("Do not output raw HTML.\n\n");

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

                String snippet = "[" + index + "] "
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
                index += 1;
            }
        }

        String prompt = sb.toString();
        if (prompt.length() <= assistantProperties.getMaxPromptChars()) {
            return prompt;
        }
        return prompt.substring(0, assistantProperties.getMaxPromptChars());
    }

    private String buildHybridPrompt(String message, List<AssistantRagSearchService.Match> matches, List<CandidatePost> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the SC1Hub assistant.\n");
        sb.append("Answer in Korean.\n");
        sb.append("Use only the information provided in 'Site snippets' and 'Site posts' as factual ground.\n");
        sb.append("If the snippets are insufficient, say you cannot find enough information and suggest checking related posts.\n");
        sb.append("Keep the answer concise (max 5 sentences).\n");
        sb.append("Do not output raw HTML.\n\n");

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
                String snippet = "[" + index + "] "
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

    private List<AssistantRelatedPostDTO> mergeRelatedPosts(List<AssistantRelatedPostDTO> ragPosts, List<CandidatePost> candidates, int limit) {
        Map<String, AssistantRelatedPostDTO> merged = new LinkedHashMap<>();
        if (ragPosts != null) {
            for (AssistantRelatedPostDTO dto : ragPosts) {
                if (dto == null) {
                    continue;
                }
                String boardTitle = normalizeBoardTitle(dto.getBoardTitle());
                if (!StringUtils.hasText(boardTitle) || !SAFE_BOARD_TITLE.matcher(boardTitle).matches()) {
                    continue;
                }
                merged.putIfAbsent(postKey(boardTitle, dto.getPostNum()), dto);
            }
        }

        if (candidates != null) {
            for (CandidatePost post : candidates) {
                if (post == null || post.post == null) {
                    continue;
                }
                String boardTitle = normalizeBoardTitle(post.boardTitle);
                if (!StringUtils.hasText(boardTitle) || !SAFE_BOARD_TITLE.matcher(boardTitle).matches()) {
                    continue;
                }
                String key = postKey(boardTitle, post.post.getPostNum());
                if (merged.containsKey(key)) {
                    continue;
                }
                AssistantRelatedPostDTO dto = new AssistantRelatedPostDTO();
                dto.setBoardTitle(boardTitle);
                dto.setPostNum(post.post.getPostNum());
                dto.setTitle(post.post.getTitle());
                dto.setRegDate(post.post.getRegDate());
                dto.setUrl(buildPostUrl(boardTitle, post.post.getPostNum()));
                merged.put(key, dto);
            }
        }

        int max = Math.max(0, limit);
        List<AssistantRelatedPostDTO> result = new ArrayList<>();
        for (AssistantRelatedPostDTO dto : merged.values()) {
            if (result.size() >= max) {
                break;
            }
            result.add(dto);
        }
        return result;
    }

    private boolean shouldLoadKeywordCandidates(List<String> expandedTerms, RagRetrieval ragRetrieval) {
        if (expandedTerms == null || expandedTerms.isEmpty()) {
            return false;
        }
        int candidateLimit = resolveCandidateLimit();
        if (candidateLimit <= 0) {
            return false;
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
                    double score = scoreCandidate(titleLower, contentLower, normalizedKeywords);
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

    private static double scoreCandidate(String titleLower, String contentLower, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return 0;
        }
        double score = 0;
        for (String keyword : keywords) {
            if (!StringUtils.hasText(keyword)) {
                continue;
            }
            if (titleLower.contains(keyword)) {
                score += 3;
            } else if (contentLower.contains(keyword)) {
                score += 1;
            }
        }
        return score;
    }

    private List<AssistantRelatedPostDTO> toRelatedPostDtos(List<CandidatePost> posts) {
        List<AssistantRelatedPostDTO> result = new ArrayList<>();
        if (posts == null) {
            return result;
        }
        for (CandidatePost post : posts) {
            if (post == null || post.post == null) {
                continue;
            }
            AssistantRelatedPostDTO dto = new AssistantRelatedPostDTO();
            dto.setBoardTitle(post.boardTitle);
            dto.setPostNum(post.post.getPostNum());
            dto.setTitle(post.post.getTitle());
            dto.setRegDate(post.post.getRegDate());
            dto.setUrl(buildPostUrl(post.boardTitle, post.post.getPostNum()));
            result.add(dto);
        }
        return result;
    }

    private String buildPrompt(String message, List<CandidatePost> contextPosts) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the SC1Hub assistant.\n");
        sb.append("Answer in Korean.\n");
        sb.append("Use only the information provided in 'Site posts' as factual ground.\n");
        sb.append("If the posts are insufficient, say you cannot find enough information and suggest checking related posts.\n");
        sb.append("Keep the answer concise (max 5 sentences).\n");
        sb.append("Do not output raw HTML.\n\n");

        sb.append("User question: ").append(message).append("\n\n");
        sb.append("Site posts:\n");

        if (contextPosts == null || contextPosts.isEmpty()) {
            sb.append("- (no related posts found)\n");
        } else {
            int index = 1;
            for (CandidatePost post : contextPosts) {
                if (post == null || post.post == null) {
                    continue;
                }
                String title = safeText(post.post.getTitle());
                String excerpt = safeText(stripHtmlToText(post.post.getContent()));
                excerpt = truncate(excerpt, assistantProperties.getMaxPostSnippetChars());
                sb.append("[").append(index).append("] ");
                sb.append("board=").append(post.boardTitle).append(", ");
                sb.append("postNum=").append(post.post.getPostNum()).append("\n");
                sb.append("title=").append(title).append("\n");
                sb.append("excerpt=").append(excerpt).append("\n");
                sb.append("url=").append(buildPostUrl(post.boardTitle, post.post.getPostNum())).append("\n\n");
                index++;
            }
        }

        String prompt = sb.toString();
        if (prompt.length() <= assistantProperties.getMaxPromptChars()) {
            return prompt;
        }
        return prompt.substring(0, assistantProperties.getMaxPromptChars());
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

    private void logParserResult(AssistantQueryParseResult parseResult, List<String> expandedTerms) {
        if (parseResult == null) {
            return;
        }
        try {
            String json = objectMapper == null ? parseResult.toString() : objectMapper.writeValueAsString(parseResult);
            log.info("검색 파서 결과={}", json);
        } catch (Exception e) {
            log.info("검색 파서 결과 로깅 실패: {}", parseResult.toString());
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

    private static final class RagRetrieval {
        private final List<AssistantRagSearchService.Match> matches;
        private final List<AssistantRelatedPostDTO> relatedPosts;

        private RagRetrieval(List<AssistantRagSearchService.Match> matches, List<AssistantRelatedPostDTO> relatedPosts) {
            this.matches = matches;
            this.relatedPosts = relatedPosts;
        }
    }

    private static final class RagPostAggregate {
        private final String boardTitle;
        private final int postNum;
        private String title;
        private Date regDate;
        private String url;
        private double bestScore;

        private RagPostAggregate(String boardTitle, int postNum) {
            this.boardTitle = boardTitle;
            this.postNum = postNum;
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
}
