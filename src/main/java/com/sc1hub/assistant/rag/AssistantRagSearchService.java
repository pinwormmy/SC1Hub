package com.sc1hub.assistant.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.config.AssistantRagProperties;
import com.sc1hub.assistant.config.GeminiProperties;
import com.sc1hub.assistant.gemini.GeminiEmbeddingClient;
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
import java.util.List;
import java.util.PriorityQueue;

@Service
@Slf4j
public class AssistantRagSearchService {

    private final AssistantRagProperties ragProperties;
    private final GeminiProperties geminiProperties;
    private final GeminiEmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;

    private volatile LoadedIndex loadedIndex;

    public AssistantRagSearchService(AssistantRagProperties ragProperties,
                                    GeminiProperties geminiProperties,
                                    GeminiEmbeddingClient embeddingClient,
                                    ObjectMapper objectMapper) {
        this.ragProperties = ragProperties;
        this.geminiProperties = geminiProperties;
        this.embeddingClient = embeddingClient;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return ragProperties.isEnabled();
    }

    public Status getStatus() {
        if (!ragProperties.isEnabled()) {
            return Status.disabled(ragProperties.getIndexPath());
        }

        LoadedIndex index = getOrLoadIndex(false);
        if (index == null) {
            return Status.notReady(ragProperties.getIndexPath());
        }
        return Status.ready(ragProperties.getIndexPath(), index.index.getEmbeddingModel(), index.index.getCreatedAt(), index.index.getUpdatedAt(), index.index.getChunks().size(), index.index.getDimension());
    }

    public List<Match> search(String query, int topK) {
        if (!ragProperties.isEnabled()) {
            return new ArrayList<>();
        }
        if (!StringUtils.hasText(query)) {
            return new ArrayList<>();
        }

        LoadedIndex index = getOrLoadIndex(true);
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

    private LoadedIndex getOrLoadIndex(boolean reloadIfModified) {
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

        if (current != null && (!reloadIfModified || current.lastModifiedMillis == lastModified)) {
            return current;
        }

        synchronized (this) {
            current = loadedIndex;
            if (current != null && (!reloadIfModified || current.lastModifiedMillis == lastModified)) {
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

                LoadedIndex loaded = new LoadedIndex(index, norms, lastModified);
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

    private static final class LoadedIndex {
        private final AssistantRagIndex index;
        private final double[] norms;
        private final long lastModifiedMillis;

        private LoadedIndex(AssistantRagIndex index, double[] norms, long lastModifiedMillis) {
            this.index = index;
            this.norms = norms;
            this.lastModifiedMillis = lastModifiedMillis;
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

        private Status(boolean enabled, boolean ready, String indexPath, String embeddingModel, Date createdAt, Date updatedAt, int chunkCount, int dimension) {
            this.enabled = enabled;
            this.ready = ready;
            this.indexPath = indexPath;
            this.embeddingModel = embeddingModel;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.chunkCount = chunkCount;
            this.dimension = dimension;
        }

        public static Status disabled(String indexPath) {
            return new Status(false, false, indexPath, null, null, null, 0, 0);
        }

        public static Status notReady(String indexPath) {
            return new Status(true, false, indexPath, null, null, null, 0, 0);
        }

        public static Status ready(String indexPath, String embeddingModel, Date createdAt, Date updatedAt, int chunkCount, int dimension) {
            return new Status(true, true, indexPath, embeddingModel, createdAt, updatedAt, chunkCount, dimension);
        }
    }
}
