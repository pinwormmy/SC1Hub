package com.sc1hub.assistant;

import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.assistant.dto.AssistantChatResponseDTO;
import com.sc1hub.assistant.dto.AssistantRelatedPostDTO;
import com.sc1hub.assistant.gemini.GeminiClient;
import com.sc1hub.assistant.gemini.GeminiException;
import com.sc1hub.board.BoardDTO;
import com.sc1hub.board.BoardListDTO;
import com.sc1hub.mapper.BoardMapper;
import com.sc1hub.member.MemberDTO;
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

    public AssistantService(BoardMapper boardMapper, GeminiClient geminiClient, AssistantProperties assistantProperties) {
        this.boardMapper = boardMapper;
        this.geminiClient = geminiClient;
        this.assistantProperties = assistantProperties;
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
        candidates.sort(candidateComparator(keywords));

        List<CandidatePost> topRelated = candidates.subList(0, Math.min(assistantProperties.getMaxRelatedPosts(), candidates.size()));
        response.setRelatedPosts(toRelatedPostDtos(topRelated));

        List<CandidatePost> contextPosts = topRelated.subList(0, Math.min(assistantProperties.getContextPosts(), topRelated.size()));
        String prompt = buildPrompt(normalizedMessage, contextPosts);

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
}
