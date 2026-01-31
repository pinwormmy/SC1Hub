package com.sc1hub.assistant.search;

import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.dto.BoardListDTO;
import com.sc1hub.board.mapper.BoardMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AssistantSearchTermsIndexService {

    private static final Pattern SAFE_BOARD_TITLE = Pattern.compile("^[a-z0-9_]+$");
    private static final int DEFAULT_BATCH_SIZE = 200;

    private final BoardMapper boardMapper;
    private final AssistantSearchTermsService searchTermsService;

    public AssistantSearchTermsIndexService(BoardMapper boardMapper, AssistantSearchTermsService searchTermsService) {
        this.boardMapper = boardMapper;
        this.searchTermsService = searchTermsService;
    }

    public ReindexResult reindexAll(int batchSize) {
        int resolvedBatchSize = Math.max(1, batchSize);
        List<BoardListDTO> boards = loadBoards();
        if (boards.isEmpty()) {
            return new ReindexResult(0, 0, 0, resolvedBatchSize, Collections.emptyList());
        }

        int boardCount = 0;
        int scannedPosts = 0;
        int updatedPosts = 0;
        List<String> failedBoards = new ArrayList<>();

        for (BoardListDTO board : boards) {
            String boardTitle = normalizeBoardTitle(board == null ? null : board.getBoardTitle());
            if (!isValidBoardTitle(boardTitle)) {
                continue;
            }
            boardCount += 1;
            try {
                int lastPostNum = 0;
                while (true) {
                    List<BoardDTO> posts = boardMapper.selectPostsForSearchTerms(boardTitle, lastPostNum, resolvedBatchSize);
                    if (posts == null || posts.isEmpty()) {
                        break;
                    }
                    for (BoardDTO post : posts) {
                        if (post == null) {
                            continue;
                        }
                        scannedPosts += 1;
                        String newTerms = searchTermsService.buildSearchTerms(post.getTitle(), post.getContent());
                        String existingTerms = post.getSearchTerms();
                        if (StringUtils.hasText(newTerms) && !newTerms.equals(existingTerms)) {
                            boardMapper.updateSearchTerms(boardTitle, post.getPostNum(), newTerms);
                            updatedPosts += 1;
                        }
                        lastPostNum = Math.max(lastPostNum, post.getPostNum());
                    }
                }
            } catch (Exception e) {
                log.warn("search_terms 재인덱싱 실패. boardTitle={}", boardTitle, e);
                failedBoards.add(boardTitle);
            }
        }

        return new ReindexResult(boardCount, scannedPosts, updatedPosts, resolvedBatchSize, failedBoards);
    }

    public ReindexResult reindexAllDefault() {
        return reindexAll(DEFAULT_BATCH_SIZE);
    }

    private List<BoardListDTO> loadBoards() {
        try {
            List<BoardListDTO> boards = boardMapper.getBoardList();
            return boards == null ? Collections.emptyList() : boards;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static boolean isValidBoardTitle(String boardTitle) {
        return StringUtils.hasText(boardTitle) && SAFE_BOARD_TITLE.matcher(boardTitle).matches();
    }

    private static String normalizeBoardTitle(String boardTitle) {
        return boardTitle == null ? "" : boardTitle.trim().toLowerCase(Locale.ROOT);
    }

    @Getter
    public static final class ReindexResult {
        private final int boardCount;
        private final int scannedPosts;
        private final int updatedPosts;
        private final int batchSize;
        private final List<String> failedBoards;

        private ReindexResult(int boardCount,
                              int scannedPosts,
                              int updatedPosts,
                              int batchSize,
                              List<String> failedBoards) {
            this.boardCount = boardCount;
            this.scannedPosts = scannedPosts;
            this.updatedPosts = updatedPosts;
            this.batchSize = batchSize;
            this.failedBoards = failedBoards == null ? Collections.emptyList() : failedBoards;
        }
    }
}
