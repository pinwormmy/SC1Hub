package com.sc1hub.assistant.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.assistant.config.AssistantRagProperties;
import com.sc1hub.assistant.config.GeminiProperties;
import com.sc1hub.assistant.gemini.GeminiEmbeddingClient;
import com.sc1hub.assistant.gemini.GeminiException;
import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.dto.BoardListDTO;
import com.sc1hub.board.mapper.BoardMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantRagIndexServiceTest {

    @Mock
    private BoardMapper boardMapper;

    @Mock
    private GeminiEmbeddingClient embeddingClient;

    @TempDir
    private Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AssistantRagProperties ragProperties;
    private GeminiProperties geminiProperties;
    private AssistantRagIndexService indexService;

    @BeforeEach
    void setUp() {
        ragProperties = new AssistantRagProperties();
        ragProperties.setEnabled(true);
        ragProperties.setIndexPath(tempDir.resolve("rag-index.json").toString());

        geminiProperties = new GeminiProperties();
        geminiProperties.setEmbeddingModel("test-embedding-model");

        TaskExecutor directExecutor = Runnable::run;
        indexService = new AssistantRagIndexService(
                boardMapper,
                embeddingClient,
                geminiProperties,
                ragProperties,
                new AssistantProperties(),
                objectMapper,
                directExecutor
        );
    }

    @Test
    void reindex_reusesExistingVectorForUnchangedChunk() throws Exception {
        Date regDate = new Date(1_700_000_000_000L);
        writeExistingIndex(regDate);

        BoardListDTO board = new BoardListDTO();
        board.setBoardTitle("PromotionBoard");
        BoardDTO post = new BoardDTO();
        post.setPostNum(1);
        post.setTitle("same");
        post.setContent("body");
        post.setRegDate(regDate);

        when(boardMapper.getBoardList()).thenReturn(Collections.singletonList(board));
        when(boardMapper.selectPostsForRag("promotionboard", ragProperties.getMaxPostsPerBoard()))
                .thenReturn(Collections.singletonList(post));

        AssistantRagIndexService.ReindexResult result = indexService.reindex();

        assertEquals(1, result.getIndexedPosts());
        assertEquals(1, result.getIndexedChunks());
        assertEquals(0, result.getEmbeddingCalls());
        assertEquals(1, result.getReusedChunks());
        verify(embeddingClient, never()).embedText(anyString());
    }

    @Test
    void reindex_persistsPartialIndexWhenEmbeddingBudgetRunsOut() throws Exception {
        ragProperties.setMaxEmbeddingCallsPerReindex(1);
        stubTwoPostBoard();
        when(embeddingClient.embedText(anyString())).thenReturn(new float[]{0.3f, 0.4f});

        AssistantRagIndexService.ReindexResult result = indexService.reindex();

        assertFalse(result.isComplete());
        assertEquals(1, result.getIndexedPosts());
        assertEquals(1, result.getIncompletePosts());
        assertEquals(1, result.getEmbeddingCalls());
        assertTrue(result.getMessage().contains("상한(1회)"));
        AssistantRagIndex saved = readIndex();
        assertTrue(saved.isIncomplete());
        assertEquals(1, saved.getChunks().size());
        verify(embeddingClient).embedText(anyString());
    }

    @Test
    void reindex_resumesFromPartialIndexOnNextRun() throws Exception {
        ragProperties.setMaxEmbeddingCallsPerReindex(1);
        stubTwoPostBoard();
        when(embeddingClient.embedText(anyString())).thenReturn(new float[]{0.3f, 0.4f});
        indexService.reindex();

        AssistantRagIndexService.ReindexResult second = indexService.reindex();

        assertTrue(second.isComplete());
        assertEquals(2, second.getIndexedPosts());
        assertEquals(0, second.getIncompletePosts());
        assertEquals(1, second.getEmbeddingCalls());
        assertEquals(1, second.getReusedChunks());
        assertNull(second.getMessage());
        AssistantRagIndex saved = readIndex();
        assertFalse(saved.isIncomplete());
        assertEquals(2, saved.getChunks().size());
    }

    @Test
    void reindex_persistsProgressWhenEmbeddingCallFails() throws Exception {
        stubTwoPostBoard();
        when(embeddingClient.embedText(anyString()))
                .thenReturn(new float[]{0.3f, 0.4f})
                .thenThrow(new GeminiException("Gemini Embedding API request failed: 429"));

        AssistantRagIndexService.ReindexResult result = indexService.reindex();

        assertFalse(result.isComplete());
        assertEquals(1, result.getIndexedPosts());
        assertEquals(1, result.getIncompletePosts());
        assertTrue(result.getMessage().contains("임베딩 호출 실패"));
        assertTrue(result.getMessage().contains("429"));
        assertEquals(1, readIndex().getChunks().size());
    }

    @Test
    void requestReindex_skipsCooldownWhileIndexIsIncomplete() throws Exception {
        ragProperties.setMinReindexIntervalMinutes(360);
        ragProperties.setMaxEmbeddingCallsPerReindex(1);
        stubTwoPostBoard();
        when(embeddingClient.embedText(anyString())).thenReturn(new float[]{0.3f, 0.4f});

        assertTrue(indexService.requestReindex().isAccepted());
        // 미완성으로 끝났으니 바로 이어서 돌릴 수 있어야 한다.
        AssistantRagIndexService.ReindexJobStatus resumed = indexService.requestReindex();
        assertTrue(resumed.isAccepted());
        assertTrue(resumed.getLastResult().isComplete());

        // 완성된 뒤의 재실행은 쿨다운에 걸린다.
        AssistantRagIndexService.ReindexJobStatus throttled = indexService.requestReindex();
        assertFalse(throttled.isAccepted());
        assertTrue(throttled.getLastError().contains("쿨다운"));
    }

    @Test
    void update_refusesToRunOnIncompleteIndex() throws Exception {
        writeExistingIndex(new Date(1_700_000_000_000L));
        AssistantRagIndex index = readIndex();
        index.setIncomplete(true);
        objectMapper.writeValue(tempDir.resolve("rag-index.json").toFile(), index);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> indexService.update());

        assertTrue(error.getMessage().contains("reindex"));
        verify(embeddingClient, never()).embedText(anyString());
    }

    /** 서로 다른 본문을 가진 글 2개짜리 게시판. 임베딩 호출이 글당 1회 필요하다. */
    private void stubTwoPostBoard() throws Exception {
        BoardListDTO board = new BoardListDTO();
        board.setBoardTitle("PromotionBoard");
        BoardDTO first = new BoardDTO();
        first.setPostNum(1);
        first.setTitle("first");
        first.setContent("first body");
        first.setRegDate(new Date(1_700_000_000_000L));
        BoardDTO second = new BoardDTO();
        second.setPostNum(2);
        second.setTitle("second");
        second.setContent("second body");
        second.setRegDate(new Date(1_700_000_001_000L));
        when(boardMapper.getBoardList()).thenReturn(Collections.singletonList(board));
        when(boardMapper.selectPostsForRag("promotionboard", ragProperties.getMaxPostsPerBoard()))
                .thenReturn(Arrays.asList(first, second));
    }

    private void writeExistingIndex(Date regDate) throws Exception {
        AssistantRagIndex index = new AssistantRagIndex();
        index.setEmbeddingModel("test-embedding-model");
        index.setDimension(2);
        index.setCreatedAt(regDate);
        index.setUpdatedAt(regDate);

        AssistantRagChunk chunk = new AssistantRagChunk();
        chunk.setId("promotionboard:1:0:existing");
        chunk.setBoardTitle("promotionboard");
        chunk.setPostNum(1);
        chunk.setTitle("same");
        chunk.setRegDate(regDate);
        chunk.setUrl("/boards/promotionboard/readPost?postNum=1");
        chunk.setChunkIndex(0);
        chunk.setText("same body");
        chunk.setVector(new float[]{0.1f, 0.2f});
        index.getChunks().add(chunk);

        objectMapper.writeValue(tempDir.resolve("rag-index.json").toFile(), index);
    }

    private AssistantRagIndex readIndex() throws Exception {
        return objectMapper.readValue(
                tempDir.resolve("rag-index.json").toFile(), AssistantRagIndex.class);
    }
}
