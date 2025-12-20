package com.sc1hub.assistant.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.config.AssistantRagProperties;
import com.sc1hub.assistant.config.GeminiProperties;
import com.sc1hub.assistant.gemini.GeminiEmbeddingClient;
import com.sc1hub.board.dto.BoardListDTO;
import com.sc1hub.board.mapper.BoardMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AssistantRagSearchService {

    private static final Pattern SAFE_BOARD_TITLE = Pattern.compile("^[a-z0-9_]+$");
    private static final int SIGNATURE_SAMPLE_LIMIT = 5;

    private final AssistantRagProperties ragProperties;
    private final GeminiProperties geminiProperties;
    private final GeminiEmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;
    private final BoardMapper boardMapper;

    private volatile LoadedIndex loadedIndex;

    public AssistantRagSearchService(AssistantRagProperties ragProperties,
                                    GeminiProperties geminiProperties,
                                    GeminiEmbeddingClient embeddingClient,
                                    ObjectMapper objectMapper,
                                    BoardMapper boardMapper) {
        this.ragProperties = ragProperties;
        this.geminiProperties = geminiProperties;
        this.embeddingClient = embeddingClient;
        this.objectMapper = objectMapper;
        this.boardMapper = boardMapper;
    }

    public boolean isEnabled() {
        return ragProperties.isEnabled();
    }

    public Status getStatus() {
        if (!ragProperties.isEnabled()) {
            return Status.disabled(ragProperties.getIndexPath());
        }

        LoadedIndex index = getOrLoadIndex();
        if (index == null) {
            return Status.notReady(ragProperties.getIndexPath());
        }
        return Status.ready(
                ragProperties.getIndexPath(),
                index.index.getEmbeddingModel(),
                index.index.getCreatedAt(),
                index.index.getUpdatedAt(),
                index.index.getChunks().size(),
                index.index.getDimension(),
                index.signatureCheck
        );
    }

    public List<Match> search(String query, int topK) {
        if (!ragProperties.isEnabled()) {
            return new ArrayList<>();
        }
        if (!StringUtils.hasText(query)) {
            return new ArrayList<>();
        }

        LoadedIndex index = getOrLoadIndex();
        if (index == null || index.index.getChunks() == null || index.index.getChunks().isEmpty()) {
            return new ArrayList<>();
        }

        float[] queryVector = embeddingClient.embedText(query);
        if (queryVector.length == 0) {
            return new ArrayList<>();
        }

        if (index.index.getDimension() > 0 && queryVector.length != index.index.getDimension()) {
            return new ArrayList<>();
        }

        double queryNorm = norm(queryVector);
        if (queryNorm == 0.0) {
            return new ArrayList<>();
        }

        int k = Math.max(1, topK);
        PriorityQueue<Match> heap = new PriorityQueue<>(Comparator.comparingDouble(Match::getScore));

        List<AssistantRagChunk> chunks = index.index.getChunks();
        for (int i = 0; i < chunks.size(); i += 1) {
            AssistantRagChunk chunk = chunks.get(i);
            if (chunk == null || chunk.getVector() == null) {
                continue;
            }
            float[] vector = chunk.getVector();
            if (vector.length == 0 || vector.length != queryVector.length) {
                continue;
            }
            double denom = queryNorm * index.norms[i];
            if (denom == 0.0) {
                continue;
            }
            double score = dot(queryVector, vector) / denom;
            if (heap.size() < k) {
                heap.add(new Match(chunk, score));
                continue;
            }
            if (score > heap.peek().score) {
                heap.poll();
                heap.add(new Match(chunk, score));
            }
        }

        List<Match> results = new ArrayList<>(heap);
        results.sort((a, b) -> Double.compare(b.score, a.score));
        return results;
    }

    private LoadedIndex getOrLoadIndex() {
        LoadedIndex current = loadedIndex;
        Path indexPath = Paths.get(ragProperties.getIndexPath());

        if (!Files.exists(indexPath)) {
            return null;
        }

        long lastModified;
        try {
            lastModified = Files.getLastModifiedTime(indexPath).toMillis();
        } catch (IOException e) {
            return null;
        }

        if (current != null && current.lastModifiedMillis == lastModified) {
            return current;
        }

        synchronized (this) {
            current = loadedIndex;
            if (current != null && current.lastModifiedMillis == lastModified) {
                return current;
            }
            try {
                AssistantRagIndex index = objectMapper.readValue(indexPath.toFile(), AssistantRagIndex.class);
                if (index == null || index.getChunks() == null) {
                    loadedIndex = null;
                    return null;
                }

                String expectedEmbeddingModel = geminiProperties.getEmbeddingModel();
                if (StringUtils.hasText(expectedEmbeddingModel)
                        && StringUtils.hasText(index.getEmbeddingModel())
                        && !expectedEmbeddingModel.equals(index.getEmbeddingModel())) {
                    log.warn("RAG 인덱스의 embeddingModel이 현재 설정과 다릅니다. index={}, current={}",
                            index.getEmbeddingModel(), expectedEmbeddingModel);
                }

                double[] norms = new double[index.getChunks().size()];
                for (int i = 0; i < index.getChunks().size(); i += 1) {
                    AssistantRagChunk chunk = index.getChunks().get(i);
                    norms[i] = chunk == null || chunk.getVector() == null ? 0.0 : norm(chunk.getVector());
                }

                SignatureCheck signatureCheck = validateSignature(index);
                if (signatureCheck.available && signatureCheck.mismatch) {
                    log.warn("RAG 인덱스와 DB 스냅샷 불일치. mismatchCount={}, sampleBoards={}",
                            signatureCheck.mismatchCount, signatureCheck.mismatchBoards);
                }

                LoadedIndex loaded = new LoadedIndex(index, norms, lastModified, signatureCheck);
                loadedIndex = loaded;
                return loaded;
            } catch (Exception e) {
                log.error("RAG 인덱스 로드 실패. path={}", indexPath, e);
                loadedIndex = null;
                return null;
            }
        }
    }

    private static double dot(float[] a, float[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i += 1) {
            sum += (double) a[i] * (double) b[i];
        }
        return sum;
    }

    private static double norm(float[] vector) {
        if (vector == null || vector.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (float v : vector) {
            sum += (double) v * (double) v;
        }
        return Math.sqrt(sum);
    }

    private SignatureCheck validateSignature(AssistantRagIndex index) {
        if (index == null) {
            return SignatureCheck.unavailable();
        }

        List<AssistantRagBoardSnapshot> snapshots = index.getBoardSnapshots();
        if (snapshots == null || snapshots.isEmpty()) {
            log.info("RAG 인덱스 스냅샷이 없습니다. reindex를 권장합니다.");
            return SignatureCheck.unavailable();
        }

        Map<String, AssistantRagBoardSnapshot> expectedByBoard = new HashMap<>();
        for (AssistantRagBoardSnapshot snapshot : snapshots) {
            if (snapshot == null) {
                continue;
            }
            String boardTitle = normalizeBoardTitle(snapshot.getBoardTitle());
            if (isIndexableBoardTitle(boardTitle)) {
                expectedByBoard.put(boardTitle, snapshot);
            }
        }

        if (expectedByBoard.isEmpty()) {
            log.info("RAG 인덱스 스냅샷에 유효한 보드 정보가 없습니다. reindex를 권장합니다.");
            return SignatureCheck.unavailable();
        }

        List<BoardListDTO> boards;
        try {
            boards = boardMapper.getBoardList();
        } catch (Exception e) {
            log.warn("RAG 스냅샷 검증 실패: board_list 로드 오류", e);
            return SignatureCheck.unavailable();
        }

        if (boards == null || boards.isEmpty()) {
            List<String> sample = new ArrayList<>();
            int mismatchCount = 0;
            for (String boardTitle : expectedByBoard.keySet()) {
                mismatchCount = appendMismatch(sample, mismatchCount, boardTitle);
            }
            if (mismatchCount > 0) {
                return SignatureCheck.mismatch(mismatchCount, sample);
            }
            return SignatureCheck.ok();
        }

        Set<String> remainingExpected = new HashSet<>(expectedByBoard.keySet());
        List<String> sample = new ArrayList<>();
        int mismatchCount = 0;

        for (BoardListDTO board : boards) {
            String boardTitle = normalizeBoardTitle(board == null ? null : board.getBoardTitle());
            if (!isIndexableBoardTitle(boardTitle)) {
                continue;
            }
            remainingExpected.remove(boardTitle);

            AssistantRagBoardSnapshot expected = expectedByBoard.get(boardTitle);
            AssistantRagBoardSnapshot current;
            try {
                current = boardMapper.selectBoardRagStats(boardTitle);
            } catch (Exception e) {
                log.warn("RAG 스냅샷 검증 실패. boardTitle={}", boardTitle, e);
                mismatchCount = appendMismatch(sample, mismatchCount, boardTitle);
                continue;
            }

            if (!snapshotEquals(expected, current)) {
                mismatchCount = appendMismatch(sample, mismatchCount, boardTitle);
            }
        }

        if (!remainingExpected.isEmpty()) {
            for (String boardTitle : remainingExpected) {
                mismatchCount = appendMismatch(sample, mismatchCount, boardTitle);
            }
        }

        if (mismatchCount > 0) {
            return SignatureCheck.mismatch(mismatchCount, sample);
        }
        return SignatureCheck.ok();
    }

    private static int appendMismatch(List<String> sample, int mismatchCount, String boardTitle) {
        int nextCount = mismatchCount + 1;
        if (sample.size() < SIGNATURE_SAMPLE_LIMIT) {
            sample.add(boardTitle);
        }
        return nextCount;
    }

    private static boolean snapshotEquals(AssistantRagBoardSnapshot expected, AssistantRagBoardSnapshot current) {
        if (expected == null || current == null) {
            return false;
        }
        if (expected.getPostCount() != current.getPostCount()) {
            return false;
        }
        if (expected.getMaxPostNum() != current.getMaxPostNum()) {
            return false;
        }
        return sameDate(expected.getMaxRegDate(), current.getMaxRegDate());
    }

    private static boolean sameDate(Date a, Date b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.getTime() == b.getTime();
    }

    private static String normalizeBoardTitle(String boardTitle) {
        if (boardTitle == null) {
            return "";
        }
        return boardTitle.trim().toLowerCase(Locale.ROOT);
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

    private static final class LoadedIndex {
        private final AssistantRagIndex index;
        private final double[] norms;
        private final long lastModifiedMillis;
        private final SignatureCheck signatureCheck;

        private LoadedIndex(AssistantRagIndex index, double[] norms, long lastModifiedMillis, SignatureCheck signatureCheck) {
            this.index = index;
            this.norms = norms;
            this.lastModifiedMillis = lastModifiedMillis;
            this.signatureCheck = signatureCheck;
        }
    }

    private static final class SignatureCheck {
        private final boolean available;
        private final boolean mismatch;
        private final int mismatchCount;
        private final List<String> mismatchBoards;
        private final Date checkedAt;

        private SignatureCheck(boolean available, boolean mismatch, int mismatchCount, List<String> mismatchBoards, Date checkedAt) {
            this.available = available;
            this.mismatch = mismatch;
            this.mismatchCount = mismatchCount;
            this.mismatchBoards = mismatchBoards == null ? new ArrayList<>() : new ArrayList<>(mismatchBoards);
            this.checkedAt = checkedAt;
        }

        private static SignatureCheck unavailable() {
            return new SignatureCheck(false, false, 0, new ArrayList<>(), null);
        }

        private static SignatureCheck ok() {
            return new SignatureCheck(true, false, 0, new ArrayList<>(), new Date());
        }

        private static SignatureCheck mismatch(int mismatchCount, List<String> mismatchBoards) {
            return new SignatureCheck(true, true, mismatchCount, mismatchBoards, new Date());
        }
    }

    @Getter
    public static final class Match {
        private final AssistantRagChunk chunk;
        private final double score;

        private Match(AssistantRagChunk chunk, double score) {
            this.chunk = chunk;
            this.score = score;
        }

        public static Match of(AssistantRagChunk chunk, double score) {
            return new Match(chunk, score);
        }
    }

    @Getter
    public static final class Status {
        private final boolean enabled;
        private final boolean ready;
        private final String indexPath;
        private final String embeddingModel;
        private final Date createdAt;
        private final Date updatedAt;
        private final int chunkCount;
        private final int dimension;
        private final boolean signatureAvailable;
        private final boolean signatureMismatch;
        private final int signatureMismatchCount;
        private final List<String> signatureMismatchBoards;
        private final Date signatureCheckedAt;

        private Status(boolean enabled, boolean ready, String indexPath, String embeddingModel, Date createdAt, Date updatedAt,
                       int chunkCount, int dimension, boolean signatureAvailable, boolean signatureMismatch,
                       int signatureMismatchCount, List<String> signatureMismatchBoards, Date signatureCheckedAt) {
            this.enabled = enabled;
            this.ready = ready;
            this.indexPath = indexPath;
            this.embeddingModel = embeddingModel;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.chunkCount = chunkCount;
            this.dimension = dimension;
            this.signatureAvailable = signatureAvailable;
            this.signatureMismatch = signatureMismatch;
            this.signatureMismatchCount = signatureMismatchCount;
            this.signatureMismatchBoards = signatureMismatchBoards == null ? new ArrayList<>() : new ArrayList<>(signatureMismatchBoards);
            this.signatureCheckedAt = signatureCheckedAt;
        }

        public static Status disabled(String indexPath) {
            return new Status(false, false, indexPath, null, null, null, 0, 0, false, false, 0, new ArrayList<>(), null);
        }

        public static Status notReady(String indexPath) {
            return new Status(true, false, indexPath, null, null, null, 0, 0, false, false, 0, new ArrayList<>(), null);
        }

        private static Status ready(String indexPath, String embeddingModel, Date createdAt, Date updatedAt, int chunkCount,
                                    int dimension, SignatureCheck signatureCheck) {
            boolean signatureAvailable = signatureCheck != null && signatureCheck.available;
            boolean signatureMismatch = signatureCheck != null && signatureCheck.mismatch;
            int signatureMismatchCount = signatureCheck == null ? 0 : signatureCheck.mismatchCount;
            List<String> signatureMismatchBoards = signatureCheck == null ? new ArrayList<>() : signatureCheck.mismatchBoards;
            Date signatureCheckedAt = signatureCheck == null ? null : signatureCheck.checkedAt;
            return new Status(true, true, indexPath, embeddingModel, createdAt, updatedAt, chunkCount, dimension,
                    signatureAvailable, signatureMismatch, signatureMismatchCount, signatureMismatchBoards, signatureCheckedAt);
        }
    }
}
