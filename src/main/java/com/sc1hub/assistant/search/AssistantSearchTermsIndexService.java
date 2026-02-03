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
import java.util.Date;
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

    private volatile boolean reindexRunning = false;
    private volatile Date lastReindexStartedAt;
    private volatile Date lastReindexFinishedAt;
    private volatile String lastReindexError;
    private volatile ReindexResult lastReindexResult;

    public AssistantSearchTermsIndexService(BoardMapper boardMapper, AssistantSearchTermsService searchTermsService) {
        this.boardMapper = boardMapper;
        this.searchTermsService = searchTermsService;
    }

    public Status getStatus() {
        return Status.of(reindexRunning, lastReindexStartedAt, lastReindexFinishedAt, lastReindexResult, lastReindexError);
    }

    public ReindexResult reindexAll(int batchSize) {
        int resolvedBatchSize = Math.max(1, batchSize);
        reindexRunning = true;
        lastReindexStartedAt = new Date();
        lastReindexFinishedAt = null;
        lastReindexError = null;

        try {
            ReindexResult result = doReindex(resolvedBatchSize);
            lastReindexResult = result;
            return result;
        } catch (Exception e) {
            lastReindexError = e.getMessage() != null ? e.getMessage() : e.toString();
            throw e;
        } finally {
            lastReindexFinishedAt = new Date();
            reindexRunning = false;
        }
    }

    public ReindexResult reindexAllDefault() {
        return reindexAll(DEFAULT_BATCH_SIZE);
    }

    private ReindexResult doReindex(int batchSize) {
        List<BoardListDTO> boards = loadBoards();
        if (boards.isEmpty()) {
            return new ReindexResult(0, 0, 0, batchSize, Collections.emptyList());
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
                    List<BoardDTO> posts = boardMapper.selectPostsForSearchTerms(boardTitle, lastPostNum, batchSize);
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

        return new ReindexResult(boardCount, scannedPosts, updatedPosts, batchSize, failedBoards);
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

    @Getter
    public static final class Status {
        private final boolean running;
        private final Date startedAt;
        private final Date finishedAt;
        private final ReindexResult lastResult;
        private final String lastError;

        private Status(boolean running, Date startedAt, Date finishedAt, ReindexResult lastResult, String lastError) {
            this.running = running;
            this.startedAt = startedAt;
            this.finishedAt = finishedAt;
            this.lastResult = lastResult;
            this.lastError = lastError;
        }

        private static Status of(boolean running, Date startedAt, Date finishedAt, ReindexResult lastResult, String lastError) {
            return new Status(running, startedAt, finishedAt, lastResult, lastError);
        }
    }
}
