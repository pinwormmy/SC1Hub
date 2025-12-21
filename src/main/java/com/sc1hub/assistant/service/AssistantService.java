package com.sc1hub.assistant.service;

import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.assistant.config.AssistantRagProperties;
import com.sc1hub.assistant.dto.AssistantChatResponseDTO;
import com.sc1hub.assistant.dto.AssistantRelatedPostDTO;
import com.sc1hub.assistant.gemini.GeminiClient;
import com.sc1hub.assistant.gemini.GeminiException;
import com.sc1hub.assistant.rag.AssistantRagChunk;
import com.sc1hub.assistant.rag.AssistantRagSearchService;
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

    private final BoardMapper boardMapper;
    private final GeminiClient geminiClient;
    private final AssistantProperties assistantProperties;
    private final AssistantRagSearchService ragSearchService;
    private final AssistantRagProperties ragProperties;

    public AssistantService(BoardMapper boardMapper,
                            GeminiClient geminiClient,
                            AssistantProperties assistantProperties,
                            AssistantRagSearchService ragSearchService,
                            AssistantRagProperties ragProperties) {
        this.boardMapper = boardMapper;
        this.geminiClient = geminiClient;
        this.assistantProperties = assistantProperties;
        this.ragSearchService = ragSearchService;
        this.ragProperties = ragProperties;
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

        List<String> keywords = extractKeywords(normalizedMessage);
        List<CandidatePost> candidates = keywords.isEmpty() ? Collections.emptyList() : findCandidates(keywords);
        if (!candidates.isEmpty()) {
            candidates.sort(candidateComparator(keywords));
        }

        String prompt;
        RagRetrieval ragRetrieval = tryRetrieveWithRag(normalizedMessage);
        if (ragRetrieval != null) {
            if (candidates.isEmpty()) {
                response.setRelatedPosts(ragRetrieval.relatedPosts);
                prompt = buildRagPrompt(normalizedMessage, ragRetrieval.matches);
            } else {
                response.setRelatedPosts(mergeRelatedPosts(ragRetrieval.relatedPosts, candidates, assistantProperties.getMaxRelatedPosts()));
                prompt = buildHybridPrompt(normalizedMessage, ragRetrieval.matches, candidates);
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

    private RagRetrieval tryRetrieveWithRag(String normalizedMessage) {
        if (ragSearchService == null || ragProperties == null || !ragSearchService.isEnabled()) {
            return null;
        }
        try {
            List<AssistantRagSearchService.Match> matches = ragSearchService.search(normalizedMessage, ragProperties.getSearchTopChunks());
            if (matches == null || matches.isEmpty()) {
                return null;
            }
            List<AssistantRelatedPostDTO> relatedPosts = buildRelatedPostsFromMatches(matches, assistantProperties.getMaxRelatedPosts());
            return new RagRetrieval(matches, relatedPosts);
        } catch (Exception e) {
            log.warn("RAG 검색 실패. 키워드 검색으로 fallback 합니다.", e);
            return null;
        }
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

    private List<CandidatePost> findCandidates(List<String> keywords) {
        List<BoardListDTO> boards = boardMapper.getBoardList();
        if (boards == null || boards.isEmpty()) {
            return Collections.emptyList();
        }

        List<CandidatePost> results = new ArrayList<>();
        for (BoardListDTO board : boards) {
            String boardTitle = normalizeBoardTitle(board == null ? null : board.getBoardTitle());
            if (!StringUtils.hasText(boardTitle) || !SAFE_BOARD_TITLE.matcher(boardTitle).matches()) {
                continue;
            }
            try {
                List<BoardDTO> posts = boardMapper.searchPostsByKeywords(boardTitle, keywords, assistantProperties.getPerBoardLimit());
                if (posts == null || posts.isEmpty()) {
                    continue;
                }
                for (BoardDTO post : posts) {
                    if (post == null) {
                        continue;
                    }
                    results.add(new CandidatePost(boardTitle, post));
                }
            } catch (Exception ignored) {
                // Skip broken boards without failing the whole request
            }
        }
        return results;
    }

    private Comparator<CandidatePost> candidateComparator(List<String> keywords) {
        return (a, b) -> {
            int scoreA = scoreCandidate(a, keywords);
            int scoreB = scoreCandidate(b, keywords);
            if (scoreA != scoreB) {
                return Integer.compare(scoreB, scoreA);
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
        };
    }

    private int scoreCandidate(CandidatePost candidate, List<String> keywords) {
        if (candidate == null || candidate.post == null || keywords == null || keywords.isEmpty()) {
            return 0;
        }
        String title = safeLower(candidate.post.getTitle());
        String content = safeLower(stripHtmlToText(candidate.post.getContent()));
        int score = 0;
        for (String keyword : keywords) {
            if (!StringUtils.hasText(keyword)) {
                continue;
            }
            String kw = keyword.toLowerCase(Locale.ROOT);
            if (title.contains(kw)) {
                score += 3;
            } else if (content.contains(kw)) {
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
            if (token.length() < 2) {
                continue;
            }
            unique.add(token);
        }

        List<String> keywords = new ArrayList<>(unique);
        keywords.sort((a, b) -> Integer.compare(b.length(), a.length()));
        if (keywords.size() > 6) {
            return keywords.subList(0, 6);
        }
        return keywords;
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

    private static final class CandidatePost {
        private final String boardTitle;
        private final BoardDTO post;

        private CandidatePost(String boardTitle, BoardDTO post) {
            this.boardTitle = boardTitle;
            this.post = post;
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
