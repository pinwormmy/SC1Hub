package com.sc1hub.assistant.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.config.AssistantRagProperties;
import com.sc1hub.assistant.config.GeminiProperties;
import com.sc1hub.assistant.gemini.GeminiEmbeddingClient;
import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.dto.BoardListDTO;
import com.sc1hub.board.mapper.BoardMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AssistantRagIndexService {

    private static final Pattern SAFE_BOARD_TITLE = Pattern.compile("^[a-z0-9_]+$");

    private final BoardMapper boardMapper;
    private final GeminiEmbeddingClient embeddingClient;
    private final GeminiProperties geminiProperties;
    private final AssistantRagProperties ragProperties;
    private final ObjectMapper objectMapper;
    private final TaskExecutor ragIndexExecutor;

    private final AtomicBoolean reindexRunning = new AtomicBoolean(false);
    private volatile Date lastReindexStartedAt;
    private volatile Date lastReindexFinishedAt;
    private volatile String lastReindexError;
    private volatile ReindexResult lastReindexResult;

    public AssistantRagIndexService(BoardMapper boardMapper,
                                   GeminiEmbeddingClient embeddingClient,
                                   GeminiProperties geminiProperties,
                                   AssistantRagProperties ragProperties,
                                   ObjectMapper objectMapper,
                                   @Qualifier("ragIndexExecutor") TaskExecutor ragIndexExecutor) {
        this.boardMapper = boardMapper;
        this.embeddingClient = embeddingClient;
        this.geminiProperties = geminiProperties;
        this.ragProperties = ragProperties;
        this.objectMapper = objectMapper;
        this.ragIndexExecutor = ragIndexExecutor;
    }

    public ReindexJobStatus requestReindex() {
        if (!ragProperties.isEnabled()) {
            return ReindexJobStatus.disabled();
        }
        if (!reindexRunning.compareAndSet(false, true)) {
            return ReindexJobStatus.running(lastReindexStartedAt, lastReindexFinishedAt, lastReindexResult, lastReindexError);
        }

        lastReindexStartedAt = new Date();
        lastReindexFinishedAt = null;
        lastReindexError = null;

        ragIndexExecutor.execute(() -> {
            try {
                lastReindexResult = reindex();
            } catch (Exception e) {
                log.error("RAG 인덱스 비동기 생성 실패", e);
                lastReindexError = e.getMessage() != null ? e.getMessage() : e.toString();
            } finally {
                lastReindexFinishedAt = new Date();
                reindexRunning.set(false);
            }
        });

        return ReindexJobStatus.accepted(lastReindexStartedAt, lastReindexResult);
    }

    @SuppressWarnings("unused")
    public ReindexJobStatus getReindexJobStatus() {
        if (!ragProperties.isEnabled()) {
            return ReindexJobStatus.disabled();
        }
        if (reindexRunning.get()) {
            return ReindexJobStatus.running(lastReindexStartedAt, lastReindexFinishedAt, lastReindexResult, lastReindexError);
        }
        return ReindexJobStatus.idle(lastReindexStartedAt, lastReindexFinishedAt, lastReindexResult, lastReindexError);
    }

    public synchronized ReindexResult reindex() throws IOException {
        if (!ragProperties.isEnabled()) {
            return ReindexResult.disabled();
        }

        String embeddingModel = geminiProperties.getEmbeddingModel();
        if (!StringUtils.hasText(embeddingModel)) {
            throw new IllegalStateException("Gemini embeddingModel is not configured.");
        }

        AssistantRagIndex index = new AssistantRagIndex();
        index.setEmbeddingModel(embeddingModel);
        index.setCreatedAt(new Date());
        index.setUpdatedAt(null);

        int indexedPosts = 0;
        int indexedChunks = 0;
        int dimension = 0;

        List<BoardListDTO> boards = filterIndexableBoards(boardMapper.getBoardList());
        if (boards == null || boards.isEmpty()) {
            index.setBoardSnapshots(buildBoardSnapshots(boards));
            index.setUpdatedAt(new Date());
            saveIndex(index);
            return new ReindexResult(true, 0, 0, 0, ragProperties.getIndexPath());
        }

        for (BoardListDTO board : boards) {
            String boardTitle = normalizeBoardTitle(board == null ? null : board.getBoardTitle());
            if (!isIndexableBoardTitle(boardTitle)) {
                continue;
            }

            List<BoardDTO> posts;
            try {
                posts = boardMapper.selectPostsForRag(boardTitle, ragProperties.getMaxPostsPerBoard());
            } catch (Exception e) {
                log.warn("RAG 인덱싱 중 게시판 로드 실패. boardTitle={}", boardTitle, e);
                continue;
            }

            if (posts == null || posts.isEmpty()) {
                continue;
            }

            for (BoardDTO post : posts) {
                if (post == null) {
                    continue;
                }

                String postText = buildPostText(post);
                if (!StringUtils.hasText(postText)) {
                    continue;
                }

                List<String> chunks = chunkText(postText, ragProperties.getChunkSizeChars(), ragProperties.getChunkOverlapChars());
                if (chunks.isEmpty()) {
                    continue;
                }

                indexedPosts += 1;
                int chunkIndex = 0;
                for (String chunk : chunks) {
                    float[] vector = embeddingClient.embedText(chunk);
                    if (vector.length == 0) {
                        continue;
                    }
                    if (dimension == 0) {
                        dimension = vector.length;
                        index.setDimension(dimension);
                    }
                    if (vector.length != dimension) {
                        continue;
                    }

                    AssistantRagChunk chunkDto = new AssistantRagChunk();
                    chunkDto.setId(generateChunkId(boardTitle, post.getPostNum(), chunkIndex));
                    chunkDto.setBoardTitle(boardTitle);
                    chunkDto.setPostNum(post.getPostNum());
                    chunkDto.setTitle(post.getTitle());
                    chunkDto.setRegDate(post.getRegDate());
                    chunkDto.setUrl(buildPostUrl(boardTitle, post.getPostNum()));
                    chunkDto.setChunkIndex(chunkIndex);
                    chunkDto.setText(chunk);
                    chunkDto.setVector(vector);
                    index.getChunks().add(chunkDto);
                    indexedChunks += 1;
                    chunkIndex += 1;
                }
            }
        }

        index.setBoardSnapshots(buildBoardSnapshots(boards));
        index.setUpdatedAt(new Date());
        saveIndex(index);
        return new ReindexResult(true, indexedPosts, indexedChunks, dimension, ragProperties.getIndexPath());
    }

    public synchronized UpdateResult update() throws IOException {
        if (!ragProperties.isEnabled()) {
            return UpdateResult.disabled(ragProperties.getIndexPath());
        }

        Path indexPath = Paths.get(ragProperties.getIndexPath());
        if (!Files.exists(indexPath)) {
            return UpdateResult.notReady(ragProperties.getIndexPath());
        }

        String embeddingModel = geminiProperties.getEmbeddingModel();
        if (!StringUtils.hasText(embeddingModel)) {
            throw new IllegalStateException("Gemini embeddingModel is not configured.");
        }

        AssistantRagIndex index;
        try {
            index = objectMapper.readValue(indexPath.toFile(), AssistantRagIndex.class);
        } catch (IOException e) {
            log.error("RAG 인덱스 로드 실패. path={}", indexPath, e);
            throw e;
        }

        if (index == null) {
            throw new IllegalStateException("RAG index is empty.");
        }
        if (!StringUtils.hasText(index.getEmbeddingModel())) {
            index.setEmbeddingModel(embeddingModel);
        }
        if (StringUtils.hasText(index.getEmbeddingModel()) && !embeddingModel.equals(index.getEmbeddingModel())) {
            throw new IllegalStateException("Embedding model has changed. Please reindex.");
        }
        index.setEmbeddingModel(embeddingModel);

        if (index.getChunks() == null) {
            index.setChunks(new ArrayList<>());
        }

        Map<String, Integer> maxPostNumByBoard = buildMaxPostNumByBoard(index.getChunks());
        Map<String, Date> maxRegDateByBoard = buildMaxRegDateByBoard(index.getChunks());
        Map<String, Date> existingRegDateByPost = buildRegDateByPost(index.getChunks());

        int updatedPosts = 0;
        int updatedChunks = 0;
        int dimension = index.getDimension();

        List<BoardListDTO> boards = filterIndexableBoards(boardMapper.getBoardList());
        Set<String> allowedBoards = buildIndexableBoardSet(boards);
        removeChunksForMissingBoards(index, allowedBoards);

        if (boards == null || boards.isEmpty()) {
            index.setBoardSnapshots(buildBoardSnapshots(boards));
            index.setUpdatedAt(new Date());
            saveIndex(index);
            return new UpdateResult(true, true, 0, 0, dimension, ragProperties.getIndexPath());
        }

        for (BoardListDTO board : boards) {
            String boardTitle = normalizeBoardTitle(board == null ? null : board.getBoardTitle());
            if (!isIndexableBoardTitle(boardTitle)) {
                continue;
            }

            int sincePostNum = maxPostNumByBoard.getOrDefault(boardTitle, 0);
            Date sinceRegDate = maxRegDateByBoard.get(boardTitle);
            if (sinceRegDate == null) {
                sinceRegDate = new Date(0);
            }

            Map<Integer, BoardDTO> postsByPostNum = new HashMap<>();
            try {
                List<BoardDTO> newPosts = boardMapper.selectNewPostsForRag(boardTitle, sincePostNum, ragProperties.getMaxPostsPerBoard());
                if (newPosts != null) {
                    for (BoardDTO post : newPosts) {
                        if (post != null) {
                            postsByPostNum.put(post.getPostNum(), post);
                        }
                    }
                }

                List<BoardDTO> updatedPostsByRegDate = boardMapper.selectUpdatedPostsForRag(boardTitle, sinceRegDate, ragProperties.getMaxPostsPerBoard());
                if (updatedPostsByRegDate != null) {
                    for (BoardDTO post : updatedPostsByRegDate) {
                        if (post != null) {
                            postsByPostNum.put(post.getPostNum(), post);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("RAG 업데이트 중 게시물 로드 실패. boardTitle={}", boardTitle, e);
                continue;
            }

            if (postsByPostNum.isEmpty()) {
                continue;
            }

            for (BoardDTO post : postsByPostNum.values()) {
                int postNum = post.getPostNum();

                if (post.getNotice() != 0) {
                    removeChunksForPost(index, boardTitle, postNum);
                    continue;
                }

                if (!shouldReindex(existingRegDateByPost, boardTitle, postNum, post.getRegDate())) {
                    continue;
                }

                String postText = buildPostText(post);
                if (!StringUtils.hasText(postText)) {
                    continue;
                }

                List<String> chunks = chunkText(postText, ragProperties.getChunkSizeChars(), ragProperties.getChunkOverlapChars());
                if (chunks.isEmpty()) {
                    continue;
                }

                List<AssistantRagChunk> newChunks = new ArrayList<>();
                int chunkIndex = 0;
                for (String chunk : chunks) {
                    float[] vector = embeddingClient.embedText(chunk);
                    if (vector.length == 0) {
                        continue;
                    }

                    if (dimension == 0) {
                        dimension = vector.length;
                        index.setDimension(dimension);
                    }
                    if (vector.length != dimension) {
                        continue;
                    }

                    AssistantRagChunk chunkDto = new AssistantRagChunk();
                    chunkDto.setId(generateChunkId(boardTitle, post.getPostNum(), chunkIndex));
                    chunkDto.setBoardTitle(boardTitle);
                    chunkDto.setPostNum(post.getPostNum());
                    chunkDto.setTitle(post.getTitle());
                    chunkDto.setRegDate(post.getRegDate());
                    chunkDto.setUrl(buildPostUrl(boardTitle, post.getPostNum()));
                    chunkDto.setChunkIndex(chunkIndex);
                    chunkDto.setText(chunk);
                    chunkDto.setVector(vector);
                    newChunks.add(chunkDto);
                    chunkIndex += 1;
                }

                if (!newChunks.isEmpty()) {
                    removeChunksForPost(index, boardTitle, postNum);
                    index.getChunks().addAll(newChunks);
                    updatedPosts += 1;
                    updatedChunks += newChunks.size();
                }
            }
        }

        index.setBoardSnapshots(buildBoardSnapshots(boards));
        index.setUpdatedAt(new Date());
        saveIndex(index);
        return new UpdateResult(true, true, updatedPosts, updatedChunks, dimension, ragProperties.getIndexPath());
    }

    private void saveIndex(AssistantRagIndex index) throws IOException {
        Path indexPath = Paths.get(ragProperties.getIndexPath());
        Path dir = indexPath.getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }

        Path tempFile = Files.createTempFile(dir == null ? Paths.get(".") : dir, "rag-index-", ".tmp");
        try {
            objectMapper.writeValue(tempFile.toFile(), index);
            try {
                Files.move(tempFile, indexPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                Files.move(tempFile, indexPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (Exception ignored) {
            }
        }
    }

    private static String normalizeBoardTitle(String boardTitle) {
        if (boardTitle == null) {
            return "";
        }
        return boardTitle.trim().toLowerCase(Locale.ROOT);
    }

    private static String buildPostText(BoardDTO post) {
        String title = post.getTitle() == null ? "" : post.getTitle().trim();
        String content = stripHtmlToText(post.getContent());
        String combined = (title + "\n" + content).trim();
        return combined.replaceAll("\\s+", " ");
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

    private static String buildPostUrl(String boardTitle, int postNum) {
        return "/boards/" + boardTitle + "/readPost?postNum=" + postNum;
    }

    private static String generateChunkId(String boardTitle, int postNum, int chunkIndex) {
        return boardTitle + ":" + postNum + ":" + chunkIndex + ":" + UUID.randomUUID();
    }

    static List<String> chunkText(String text, int chunkSize, int overlap) {
        if (!StringUtils.hasText(text)) {
            return new ArrayList<>();
        }
        int size = Math.max(100, chunkSize);
        int ov = Math.max(0, overlap);
        int step = Math.max(1, size - ov);

        List<String> chunks = new ArrayList<>();
        String normalized = text.trim();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + size);
            String chunk = normalized.substring(start, end).trim();
            if (StringUtils.hasText(chunk)) {
                chunks.add(chunk);
            }
            if (end >= normalized.length()) {
                break;
            }
            start += step;
        }
        return chunks;
    }

    private static boolean isIndexableBoardTitle(String boardTitle) {
        if (!StringUtils.hasText(boardTitle)) {
            return false;
        }
        String normalized = normalizeBoardTitle(boardTitle);
        if (!StringUtils.hasText(normalized) || !SAFE_BOARD_TITLE.matcher(normalized).matches()) {
            return false;
        }
        return normalized.endsWith("board");
    }

    private static List<BoardListDTO> filterIndexableBoards(List<BoardListDTO> boards) {
        if (boards == null || boards.isEmpty()) {
            return boards;
        }
        List<BoardListDTO> filtered = new ArrayList<>();
        for (BoardListDTO board : boards) {
            String boardTitle = normalizeBoardTitle(board == null ? null : board.getBoardTitle());
            if (!isIndexableBoardTitle(boardTitle)) {
                continue;
            }
            filtered.add(board);
        }
        return filtered;
    }

    private static Set<String> buildIndexableBoardSet(List<BoardListDTO> boards) {
        Set<String> allowed = new HashSet<>();
        if (boards == null || boards.isEmpty()) {
            return allowed;
        }
        for (BoardListDTO board : boards) {
            String boardTitle = normalizeBoardTitle(board == null ? null : board.getBoardTitle());
            if (isIndexableBoardTitle(boardTitle)) {
                allowed.add(boardTitle);
            }
        }
        return allowed;
    }

    private List<AssistantRagBoardSnapshot> buildBoardSnapshots(List<BoardListDTO> boards) {
        List<AssistantRagBoardSnapshot> snapshots = new ArrayList<>();
        if (boards == null || boards.isEmpty()) {
            return snapshots;
        }

        for (BoardListDTO board : boards) {
            String boardTitle = normalizeBoardTitle(board == null ? null : board.getBoardTitle());
            if (!isIndexableBoardTitle(boardTitle)) {
                continue;
            }
            try {
                AssistantRagBoardSnapshot snapshot = boardMapper.selectBoardRagStats(boardTitle);
                if (snapshot == null) {
                    snapshot = new AssistantRagBoardSnapshot();
                }
                snapshot.setBoardTitle(boardTitle);
                snapshots.add(snapshot);
            } catch (Exception e) {
                log.warn("RAG 스냅샷 로드 실패. boardTitle={}", boardTitle, e);
            }
        }

        return snapshots;
    }

    private static void removeChunksForMissingBoards(AssistantRagIndex index, Set<String> allowedBoards) {
        if (index == null || index.getChunks() == null || index.getChunks().isEmpty()) {
            return;
        }
        if (allowedBoards == null || allowedBoards.isEmpty()) {
            index.getChunks().clear();
            return;
        }
        index.getChunks().removeIf(chunk -> {
            if (chunk == null) {
                return false;
            }
            String boardTitle = normalizeBoardTitle(chunk.getBoardTitle());
            return !allowedBoards.contains(boardTitle);
        });
    }

    @Getter
    public static final class ReindexJobStatus {
        private final boolean enabled;
        private final boolean accepted;
        private final boolean running;
        private final Date startedAt;
        private final Date finishedAt;
        private final ReindexResult lastResult;
        private final String lastError;

        private ReindexJobStatus(boolean enabled, boolean accepted, boolean running, Date startedAt, Date finishedAt,
                                 ReindexResult lastResult, String lastError) {
            this.enabled = enabled;
            this.accepted = accepted;
            this.running = running;
            this.startedAt = startedAt;
            this.finishedAt = finishedAt;
            this.lastResult = lastResult;
            this.lastError = lastError;
        }

        public static ReindexJobStatus disabled() {
            return new ReindexJobStatus(false, false, false, null, null, null, null);
        }

        public static ReindexJobStatus accepted(Date startedAt, ReindexResult lastResult) {
            return new ReindexJobStatus(true, true, true, startedAt, null, lastResult, null);
        }

        public static ReindexJobStatus running(Date startedAt, Date finishedAt, ReindexResult lastResult, String lastError) {
            return new ReindexJobStatus(true, false, true, startedAt, finishedAt, lastResult, lastError);
        }

        public static ReindexJobStatus idle(Date startedAt, Date finishedAt, ReindexResult lastResult, String lastError) {
            return new ReindexJobStatus(true, false, false, startedAt, finishedAt, lastResult, lastError);
        }
    }


    private static Map<String, Integer> buildMaxPostNumByBoard(List<AssistantRagChunk> chunks) {
        Map<String, Integer> maxByBoard = new HashMap<>();
        if (chunks == null || chunks.isEmpty()) {
            return maxByBoard;
        }
        for (AssistantRagChunk chunk : chunks) {
            if (chunk == null) {
                continue;
            }
            String boardTitle = normalizeBoardTitle(chunk.getBoardTitle());
            if (!StringUtils.hasText(boardTitle)) {
                continue;
            }
            int postNum = chunk.getPostNum();
            Integer current = maxByBoard.get(boardTitle);
            if (current == null || postNum > current) {
                maxByBoard.put(boardTitle, postNum);
            }
        }
        return maxByBoard;
    }

    private static Map<String, Date> buildMaxRegDateByBoard(List<AssistantRagChunk> chunks) {
        Map<String, Date> maxByBoard = new HashMap<>();
        if (chunks == null || chunks.isEmpty()) {
            return maxByBoard;
        }
        for (AssistantRagChunk chunk : chunks) {
            if (chunk == null) {
                continue;
            }
            String boardTitle = normalizeBoardTitle(chunk.getBoardTitle());
            if (!StringUtils.hasText(boardTitle)) {
                continue;
            }
            Date regDate = chunk.getRegDate();
            if (regDate == null) {
                continue;
            }
            Date current = maxByBoard.get(boardTitle);
            if (current == null || regDate.after(current)) {
                maxByBoard.put(boardTitle, regDate);
            }
        }
        return maxByBoard;
    }

    private static Map<String, Date> buildRegDateByPost(List<AssistantRagChunk> chunks) {
        Map<String, Date> maxByPost = new HashMap<>();
        if (chunks == null || chunks.isEmpty()) {
            return maxByPost;
        }

        for (AssistantRagChunk chunk : chunks) {
            if (chunk == null) {
                continue;
            }
            String boardTitle = normalizeBoardTitle(chunk.getBoardTitle());
            if (!StringUtils.hasText(boardTitle)) {
                continue;
            }
            int postNum = chunk.getPostNum();
            Date regDate = chunk.getRegDate();
            if (regDate == null) {
                continue;
            }
            String key = postKey(boardTitle, postNum);
            Date current = maxByPost.get(key);
            if (current == null || regDate.after(current)) {
                maxByPost.put(key, regDate);
            }
        }

        return maxByPost;
    }

    private static boolean shouldReindex(Map<String, Date> existingRegDateByPost, String boardTitle, int postNum, Date currentRegDate) {
        if (existingRegDateByPost == null) {
            return true;
        }
        Date existing = existingRegDateByPost.get(postKey(boardTitle, postNum));
        if (existing == null) {
            return true;
        }
        if (currentRegDate == null) {
            return false;
        }
        return currentRegDate.after(existing);
    }

    private static String postKey(String boardTitle, int postNum) {
        return normalizeBoardTitle(boardTitle) + ":" + postNum;
    }

    private static void removeChunksForPost(AssistantRagIndex index, String boardTitle, int postNum) {
        if (index == null || index.getChunks() == null || index.getChunks().isEmpty()) {
            return;
        }
        String normalizedBoardTitle = normalizeBoardTitle(boardTitle);
        index.getChunks().removeIf(chunk -> chunk != null
                && postNum == chunk.getPostNum()
                && normalizedBoardTitle.equals(normalizeBoardTitle(chunk.getBoardTitle())));
    }

    @Getter
    public static final class ReindexResult {
        private final boolean enabled;
        private final int indexedPosts;
        private final int indexedChunks;
        private final int dimension;
        private final String indexPath;

        private ReindexResult(boolean enabled, int indexedPosts, int indexedChunks, int dimension, String indexPath) {
            this.enabled = enabled;
            this.indexedPosts = indexedPosts;
            this.indexedChunks = indexedChunks;
            this.dimension = dimension;
            this.indexPath = indexPath;
        }

        public static ReindexResult disabled() {
            return new ReindexResult(false, 0, 0, 0, null);
        }
    }

    @Getter
    public static final class UpdateResult {
        private final boolean enabled;
        private final boolean ready;
        private final int updatedPosts;
        private final int updatedChunks;
        private final int dimension;
        private final String indexPath;

        private UpdateResult(boolean enabled, boolean ready, int updatedPosts, int updatedChunks, int dimension, String indexPath) {
            this.enabled = enabled;
            this.ready = ready;
            this.updatedPosts = updatedPosts;
            this.updatedChunks = updatedChunks;
            this.dimension = dimension;
            this.indexPath = indexPath;
        }

        public static UpdateResult disabled(String indexPath) {
            return new UpdateResult(false, false, 0, 0, 0, indexPath);
        }

        public static UpdateResult notReady(String indexPath) {
            return new UpdateResult(true, false, 0, 0, 0, indexPath);
        }
    }
}
