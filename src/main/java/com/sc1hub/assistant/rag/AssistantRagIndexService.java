package com.sc1hub.assistant.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.config.AssistantProperties;
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
    private final AssistantProperties assistantProperties;
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
                                   AssistantProperties assistantProperties,
                                   ObjectMapper objectMapper,
                                   @Qualifier("ragIndexExecutor") TaskExecutor ragIndexExecutor) {
        this.boardMapper = boardMapper;
        this.embeddingClient = embeddingClient;
        this.geminiProperties = geminiProperties;
        this.ragProperties = ragProperties;
        this.assistantProperties = assistantProperties;
        this.objectMapper = objectMapper;
        this.ragIndexExecutor = ragIndexExecutor;
    }

    public ReindexJobStatus requestReindex() {
        if (!ragProperties.isEnabled()) {
            return ReindexJobStatus.disabled();
        }
        ReindexJobStatus cooldownStatus = checkReindexCooldown();
        if (cooldownStatus != null) {
            return cooldownStatus;
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

    private ReindexJobStatus checkReindexCooldown() {
        int cooldownMinutes = Math.max(0, ragProperties.getMinReindexIntervalMinutes());
        if (cooldownMinutes <= 0 || lastReindexFinishedAt == null) {
            return null;
        }
        long elapsedMillis = System.currentTimeMillis() - lastReindexFinishedAt.getTime();
        long cooldownMillis = cooldownMinutes * 60L * 1000L;
        if (elapsedMillis >= cooldownMillis) {
            return null;
        }
        long remainingMinutes = Math.max(1L, (cooldownMillis - elapsedMillis + 59999L) / 60000L);
        return ReindexJobStatus.throttled(
                lastReindexStartedAt,
                lastReindexFinishedAt,
                lastReindexResult,
                "RAG 재인덱싱 쿨다운 중입니다. 약 " + remainingMinutes + "분 후 다시 실행할 수 있습니다."
        );
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

        String embeddingModel = requireEmbeddingModel();

        AssistantRagIndex index = new AssistantRagIndex();
        index.setEmbeddingModel(embeddingModel);
        index.setCreatedAt(new Date());
        index.setUpdatedAt(null);

        AssistantRagIndex reusableIndex = loadReusableIndex(embeddingModel);
        if (reusableIndex != null && reusableIndex.getDimension() > 0) {
            index.setDimension(reusableIndex.getDimension());
        }
        ReusableEmbeddingStore reusableEmbeddings = ReusableEmbeddingStore.from(reusableIndex);
        IndexingContext indexingContext = new IndexingContext(index);
        int indexedPosts = 0;
        int indexedChunks = 0;
        int chunkSize = ragProperties.getChunkSizeChars();
        int overlap = ragProperties.getChunkOverlapChars();
        EmbeddingBudget embeddingBudget = EmbeddingBudget.limited(
                ragProperties.getMaxEmbeddingCallsPerReindex(),
                "RAG 재인덱싱"
        );

        List<BoardListDTO> boards = loadIndexableBoards();
        if (boards == null || boards.isEmpty()) {
            finalizeIndex(index, boards);
            return new ReindexResult(true, 0, 0, indexingContext.getDimension(), ragProperties.getIndexPath(),
                    embeddingBudget.getEmbeddingCalls(), embeddingBudget.getReusedChunks());
        }

        for (BoardListDTO board : boards) {
            String boardTitle = normalizeBoardTitle(board == null ? null : board.getBoardTitle());
            if (!isIndexableBoardTitle(boardTitle)) {
                continue;
            }

            List<BoardDTO> posts = loadPostsForReindex(boardTitle);

            if (posts == null || posts.isEmpty()) {
                continue;
            }

            for (BoardDTO post : posts) {
                PostChunkResult result = buildChunksForPost(boardTitle, post, indexingContext, chunkSize, overlap, embeddingBudget, reusableEmbeddings);
                if (!result.hasSourceChunks()) {
                    continue;
                }
                indexedPosts += 1;
                if (!result.getChunks().isEmpty()) {
                    index.getChunks().addAll(result.getChunks());
                    indexedChunks += result.getChunks().size();
                }
            }
        }

        finalizeIndex(index, boards);
        return new ReindexResult(true, indexedPosts, indexedChunks, indexingContext.getDimension(), ragProperties.getIndexPath(),
                embeddingBudget.getEmbeddingCalls(), embeddingBudget.getReusedChunks());
    }

    public synchronized UpdateResult update() throws IOException {
        if (!ragProperties.isEnabled()) {
            return UpdateResult.disabled(ragProperties.getIndexPath());
        }

        Path indexPath = Paths.get(ragProperties.getIndexPath());
        if (!Files.exists(indexPath)) {
            return UpdateResult.notReady(ragProperties.getIndexPath());
        }

        String embeddingModel = requireEmbeddingModel();

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
        validateEmbeddingModel(index, embeddingModel);

        if (index.getChunks() == null) {
            index.setChunks(new ArrayList<>());
        }
        ReusableEmbeddingStore reusableEmbeddings = ReusableEmbeddingStore.from(index);

        Map<String, Integer> maxPostNumByBoard = buildMaxPostNumByBoard(index.getChunks());
        Map<String, Date> maxRegDateByBoard = buildMaxRegDateByBoard(index.getChunks());
        Map<String, Date> existingRegDateByPost = buildRegDateByPost(index.getChunks());

        IndexingContext indexingContext = new IndexingContext(index);
        int updatedPosts = 0;
        int updatedChunks = 0;
        int chunkSize = ragProperties.getChunkSizeChars();
        int overlap = ragProperties.getChunkOverlapChars();
        EmbeddingBudget embeddingBudget = EmbeddingBudget.limited(
                ragProperties.getMaxEmbeddingCallsPerUpdate(),
                "RAG 업데이트"
        );

        List<BoardListDTO> boards = loadIndexableBoards();
        Set<String> allowedBoards = buildIndexableBoardSet(boards);
        removeChunksForMissingBoards(index, allowedBoards);

        if (boards == null || boards.isEmpty()) {
            finalizeIndex(index, boards);
            return new UpdateResult(true, true, 0, 0, indexingContext.getDimension(), ragProperties.getIndexPath(),
                    embeddingBudget.getEmbeddingCalls(), embeddingBudget.getReusedChunks());
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

            Map<Integer, BoardDTO> postsByPostNum = loadCandidatePostsForUpdate(boardTitle, sincePostNum, sinceRegDate);

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

                PostChunkResult result = buildChunksForPost(boardTitle, post, indexingContext, chunkSize, overlap, embeddingBudget, reusableEmbeddings);

                if (!result.getChunks().isEmpty()) {
                    removeChunksForPost(index, boardTitle, postNum);
                    index.getChunks().addAll(result.getChunks());
                    updatedPosts += 1;
                    updatedChunks += result.getChunks().size();
                }
            }
        }

        finalizeIndex(index, boards);
        return new UpdateResult(true, true, updatedPosts, updatedChunks, indexingContext.getDimension(), ragProperties.getIndexPath(),
                embeddingBudget.getEmbeddingCalls(), embeddingBudget.getReusedChunks());
    }

    private AssistantRagIndex loadReusableIndex(String embeddingModel) {
        Path indexPath = Paths.get(ragProperties.getIndexPath());
        if (!Files.exists(indexPath)) {
            return null;
        }
        try {
            AssistantRagIndex existing = objectMapper.readValue(indexPath.toFile(), AssistantRagIndex.class);
            if (existing == null || existing.getChunks() == null || existing.getChunks().isEmpty()) {
                return null;
            }
            if (!StringUtils.hasText(existing.getEmbeddingModel()) || !existing.getEmbeddingModel().equals(embeddingModel)) {
                log.info("기존 RAG 인덱스 임베딩 모델이 달라 벡터를 재사용하지 않습니다. path={}", indexPath);
                return null;
            }
            return existing;
        } catch (Exception e) {
            log.warn("기존 RAG 인덱스 로드 실패. 벡터 재사용 없이 재인덱싱합니다. path={}", indexPath, e);
            return null;
        }
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

    private String requireEmbeddingModel() {
        String embeddingModel = geminiProperties.getEmbeddingModel();
        if (!StringUtils.hasText(embeddingModel)) {
            throw new IllegalStateException("Gemini embeddingModel is not configured.");
        }
        return embeddingModel;
    }

    private void validateEmbeddingModel(AssistantRagIndex index, String embeddingModel) {
        String existing = index.getEmbeddingModel();
        if (StringUtils.hasText(existing) && !embeddingModel.equals(existing)) {
            throw new IllegalStateException("Embedding model has changed. Please reindex.");
        }
        index.setEmbeddingModel(embeddingModel);
    }

    private List<BoardListDTO> loadIndexableBoards() {
        return filterIndexableBoards(boardMapper.getBoardList());
    }

    private void finalizeIndex(AssistantRagIndex index, List<BoardListDTO> boards) throws IOException {
        index.setBoardSnapshots(buildBoardSnapshots(boards));
        index.setUpdatedAt(new Date());
        saveIndex(index);
    }

    private List<BoardDTO> loadPostsForReindex(String boardTitle) {
        try {
            return boardMapper.selectPostsForRag(boardTitle, ragProperties.getMaxPostsPerBoard());
        } catch (Exception e) {
            log.warn("RAG 인덱싱 중 게시판 로드 실패. boardTitle={}", boardTitle, e);
            return new ArrayList<>();
        }
    }

    private Map<Integer, BoardDTO> loadCandidatePostsForUpdate(String boardTitle, int sincePostNum, Date sinceRegDate) {
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
        }
        return postsByPostNum;
    }

    private PostChunkResult buildChunksForPost(String boardTitle,
                                               BoardDTO post,
                                               IndexingContext context,
                                               int chunkSize,
                                               int overlap,
                                               EmbeddingBudget embeddingBudget,
                                               ReusableEmbeddingStore reusableEmbeddings) {
        if (post == null) {
            return PostChunkResult.empty();
        }
        String postText = buildPostText(post);
        if (!StringUtils.hasText(postText)) {
            return PostChunkResult.empty();
        }
        List<String> chunks = chunkText(postText, chunkSize, overlap);
        if (chunks.isEmpty()) {
            return PostChunkResult.empty();
        }

        List<AssistantRagChunk> newChunks = new ArrayList<>();
        int chunkIndex = 0;
        for (String chunk : chunks) {
            AssistantRagChunk reusableChunk = reusableEmbeddings == null
                    ? null
                    : reusableEmbeddings.findExact(boardTitle, post.getPostNum(), chunkIndex, chunk, context.getDimension());
            float[] vector = reusableChunk == null ? null : reusableChunk.getVector();
            if (vector == null && reusableEmbeddings != null) {
                vector = reusableEmbeddings.findByText(chunk, context.getDimension());
            }
            if (vector != null) {
                if (!context.acceptVector(vector)) {
                    continue;
                }
                if (embeddingBudget != null) {
                    embeddingBudget.markReused();
                }
                newChunks.add(buildChunk(boardTitle, post, chunk, chunkIndex, vector,
                        reusableChunk == null ? null : reusableChunk.getId()));
                chunkIndex += 1;
                continue;
            }
            if (embeddingBudget != null) {
                embeddingBudget.beforeEmbeddingCall();
            }
            vector = embeddingClient.embedText(chunk);
            if (!context.acceptVector(vector)) {
                continue;
            }
            if (reusableEmbeddings != null) {
                reusableEmbeddings.rememberTextVector(chunk, vector);
            }
            newChunks.add(buildChunk(boardTitle, post, chunk, chunkIndex, vector, null));
            chunkIndex += 1;
        }

        return new PostChunkResult(true, newChunks);
    }

    private static AssistantRagChunk buildChunk(String boardTitle,
                                                BoardDTO post,
                                                String chunk,
                                                int chunkIndex,
                                                float[] vector,
                                                String reusableId) {
        AssistantRagChunk chunkDto = new AssistantRagChunk();
        chunkDto.setId(StringUtils.hasText(reusableId) ? reusableId : generateChunkId(boardTitle, post.getPostNum(), chunkIndex));
        chunkDto.setBoardTitle(boardTitle);
        chunkDto.setPostNum(post.getPostNum());
        chunkDto.setTitle(post.getTitle());
        chunkDto.setRegDate(post.getRegDate());
        chunkDto.setUrl(buildPostUrl(boardTitle, post.getPostNum()));
        chunkDto.setChunkIndex(chunkIndex);
        chunkDto.setText(chunk);
        chunkDto.setVector(vector);
        return chunkDto;
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

    private List<BoardListDTO> filterIndexableBoards(List<BoardListDTO> boards) {
        if (boards == null || boards.isEmpty()) {
            return boards;
        }
        List<BoardListDTO> filtered = new ArrayList<>();
        for (BoardListDTO board : boards) {
            String boardTitle = normalizeBoardTitle(board == null ? null : board.getBoardTitle());
            if (!isIndexableBoardTitle(boardTitle)) {
                continue;
            }
            if (isExcludedBoardTitle(boardTitle)) {
                continue;
            }
            filtered.add(board);
        }
        return filtered;
    }

    private Set<String> buildIndexableBoardSet(List<BoardListDTO> boards) {
        Set<String> allowed = new HashSet<>();
        if (boards == null || boards.isEmpty()) {
            return allowed;
        }
        for (BoardListDTO board : boards) {
            String boardTitle = normalizeBoardTitle(board == null ? null : board.getBoardTitle());
            if (isIndexableBoardTitle(boardTitle) && !isExcludedBoardTitle(boardTitle)) {
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
            if (isExcludedBoardTitle(boardTitle)) {
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

    private boolean isExcludedBoardTitle(String boardTitle) {
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

    private static final class IndexingContext {
        private final AssistantRagIndex index;
        private int dimension;

        private IndexingContext(AssistantRagIndex index) {
            this.index = index;
            this.dimension = Math.max(0, index.getDimension());
        }

        private boolean acceptVector(float[] vector) {
            if (vector == null || vector.length == 0) {
                return false;
            }
            if (dimension == 0) {
                dimension = vector.length;
                index.setDimension(dimension);
            }
            return vector.length == dimension;
        }

        private int getDimension() {
            return dimension;
        }
    }

    private static final class EmbeddingBudget {
        private final int maxCalls;
        private final String jobName;
        private int usedCalls;
        private int reusedChunks;

        private EmbeddingBudget(int maxCalls, String jobName) {
            this.maxCalls = maxCalls;
            this.jobName = jobName;
        }

        private static EmbeddingBudget limited(int maxCalls, String jobName) {
            return new EmbeddingBudget(Math.max(0, maxCalls), jobName);
        }

        private void beforeEmbeddingCall() {
            if (maxCalls <= 0) {
                usedCalls += 1;
                return;
            }
            if (usedCalls >= maxCalls) {
                throw new IllegalStateException(jobName + " 임베딩 호출 상한(" + maxCalls + "회)을 초과했습니다.");
            }
            usedCalls += 1;
        }

        private void markReused() {
            reusedChunks += 1;
        }

        private int getEmbeddingCalls() {
            return usedCalls;
        }

        private int getReusedChunks() {
            return reusedChunks;
        }
    }

    private static final class ReusableEmbeddingStore {
        private final Map<String, AssistantRagChunk> exactChunks = new HashMap<>();
        private final Map<String, float[]> vectorsByText = new HashMap<>();

        private static ReusableEmbeddingStore from(AssistantRagIndex index) {
            ReusableEmbeddingStore store = new ReusableEmbeddingStore();
            if (index == null || index.getChunks() == null || index.getChunks().isEmpty()) {
                return store;
            }
            for (AssistantRagChunk chunk : index.getChunks()) {
                if (chunk == null || !StringUtils.hasText(chunk.getText()) || !hasVector(chunk.getVector())) {
                    continue;
                }
                String text = chunk.getText();
                store.exactChunks.put(exactChunkKey(chunk.getBoardTitle(), chunk.getPostNum(), chunk.getChunkIndex(), text), chunk);
                store.vectorsByText.putIfAbsent(text, chunk.getVector());
            }
            return store;
        }

        private AssistantRagChunk findExact(String boardTitle, int postNum, int chunkIndex, String text, int expectedDimension) {
            AssistantRagChunk chunk = exactChunks.get(exactChunkKey(boardTitle, postNum, chunkIndex, text));
            if (chunk == null || !isReusableVector(chunk.getVector(), expectedDimension)) {
                return null;
            }
            return chunk;
        }

        private float[] findByText(String text, int expectedDimension) {
            float[] vector = vectorsByText.get(text);
            return isReusableVector(vector, expectedDimension) ? vector : null;
        }

        private void rememberTextVector(String text, float[] vector) {
            if (!StringUtils.hasText(text) || !hasVector(vector)) {
                return;
            }
            vectorsByText.putIfAbsent(text, vector);
        }

        private static String exactChunkKey(String boardTitle, int postNum, int chunkIndex, String text) {
            String normalizedText = text == null ? "" : text;
            return postKey(boardTitle, postNum) + ":" + chunkIndex + ":" + normalizedText.length() + ":" + normalizedText;
        }

        private static boolean isReusableVector(float[] vector, int expectedDimension) {
            return hasVector(vector) && (expectedDimension <= 0 || vector.length == expectedDimension);
        }

        private static boolean hasVector(float[] vector) {
            return vector != null && vector.length > 0;
        }
    }

    private static final class PostChunkResult {
        private final boolean hasSourceChunks;
        private final List<AssistantRagChunk> chunks;

        private PostChunkResult(boolean hasSourceChunks, List<AssistantRagChunk> chunks) {
            this.hasSourceChunks = hasSourceChunks;
            this.chunks = chunks;
        }

        private static PostChunkResult empty() {
            return new PostChunkResult(false, new ArrayList<>());
        }

        private boolean hasSourceChunks() {
            return hasSourceChunks;
        }

        private List<AssistantRagChunk> getChunks() {
            return chunks;
        }
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

        public static ReindexJobStatus throttled(Date startedAt, Date finishedAt, ReindexResult lastResult, String message) {
            return new ReindexJobStatus(true, false, false, startedAt, finishedAt, lastResult, message);
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
        private final int embeddingCalls;
        private final int reusedChunks;

        private ReindexResult(boolean enabled, int indexedPosts, int indexedChunks, int dimension, String indexPath,
                              int embeddingCalls, int reusedChunks) {
            this.enabled = enabled;
            this.indexedPosts = indexedPosts;
            this.indexedChunks = indexedChunks;
            this.dimension = dimension;
            this.indexPath = indexPath;
            this.embeddingCalls = embeddingCalls;
            this.reusedChunks = reusedChunks;
        }

        public static ReindexResult disabled() {
            return new ReindexResult(false, 0, 0, 0, null, 0, 0);
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
        private final int embeddingCalls;
        private final int reusedChunks;

        private UpdateResult(boolean enabled, boolean ready, int updatedPosts, int updatedChunks, int dimension, String indexPath,
                             int embeddingCalls, int reusedChunks) {
            this.enabled = enabled;
            this.ready = ready;
            this.updatedPosts = updatedPosts;
            this.updatedChunks = updatedChunks;
            this.dimension = dimension;
            this.indexPath = indexPath;
            this.embeddingCalls = embeddingCalls;
            this.reusedChunks = reusedChunks;
        }

        public static UpdateResult disabled(String indexPath) {
            return new UpdateResult(false, false, 0, 0, 0, indexPath, 0, 0);
        }

        public static UpdateResult notReady(String indexPath) {
            return new UpdateResult(true, false, 0, 0, 0, indexPath, 0, 0);
        }
    }
}
