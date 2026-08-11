package com.sc1hub.assistant.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.assistant.config.AssistantRagProperties;
import com.sc1hub.assistant.config.GeminiProperties;
import com.sc1hub.assistant.gemini.GeminiEmbeddingClient;
import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.dto.BoardListDTO;
import com.sc1hub.board.mapper.BoardMapper;
import com.sc1hub.strategytip.dto.StrategyTipDTO;
import com.sc1hub.strategytip.mapper.StrategyTipMapper;
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
    private StrategyTipMapper strategyTipMapper;

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
                strategyTipMapper,
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
        board.setBoardTitle("FreeBoard");
        BoardDTO post = new BoardDTO();
        post.setPostNum(1);
        post.setTitle("same");
        post.setContent("body");
        post.setRegDate(regDate);

        when(boardMapper.getBoardList()).thenReturn(Collections.singletonList(board));
        when(boardMapper.selectPostsForRag("freeboard", ragProperties.getMaxPostsPerBoard()))
                .thenReturn(Collections.singletonList(post));

        AssistantRagIndexService.ReindexResult result = indexService.reindex();

        assertEquals(1, result.getIndexedPosts());
        assertEquals(1, result.getIndexedChunks());
        assertEquals(0, result.getEmbeddingCalls());
        assertEquals(1, result.getReusedChunks());
        verify(embeddingClient, never()).embedText(anyString());
    }

    @Test
    void reindex_includesPublishedStrategyTipsAsRagChunks() throws Exception {
        StrategyTipDTO tip = strategyTip(7, "t_vs_z", "테저전",
                "정찰 후 상대 진출 경로에 맞춰 수비 위치를 조정하세요.");
        when(boardMapper.getBoardList()).thenReturn(Collections.emptyList());
        when(strategyTipMapper.selectTipsForRag()).thenReturn(Collections.singletonList(tip));
        when(embeddingClient.embedText(anyString())).thenReturn(new float[]{0.3f, 0.4f});

        AssistantRagIndexService.ReindexResult result = indexService.reindex();

        assertEquals(1, result.getIndexedPosts());
        assertEquals(1, result.getIndexedChunks());
        AssistantRagIndex saved = readIndex();
        assertEquals(1, saved.getChunks().size());
        AssistantRagChunk chunk = saved.getChunks().get(0);
        assertEquals(AssistantRagSources.STRATEGY_TIP_BOARD, chunk.getBoardTitle());
        assertEquals(7, chunk.getPostNum());
        assertEquals("한줄 공략 · 테저전", chunk.getTitle());
        assertEquals(AssistantRagSources.STRATEGY_TIP_URL, chunk.getUrl());
        assertTrue(chunk.getText().contains(tip.getContent()));
    }

    @Test
    void update_reconcilesChangedNewAndDeletedPublishedStrategyTips() throws Exception {
        writeStrategyTipIndex();
        StrategyTipDTO changed = strategyTip(1, "t_vs_z", "테저전",
                "상대 병력 구성을 확인하고 수비 병력의 위치를 다시 조정하세요.");
        StrategyTipDTO added = strategyTip(3, "p_vs_t", "프테전",
                "진출 전에 관측선의 이동 경로와 중앙 시야를 먼저 확인하세요.");
        when(boardMapper.getBoardList()).thenReturn(Collections.emptyList());
        when(strategyTipMapper.selectTipsForRag()).thenReturn(Arrays.asList(changed, added));
        when(embeddingClient.embedText(anyString())).thenReturn(new float[]{0.5f, 0.6f});

        AssistantRagIndexService.UpdateResult result = indexService.update();

        assertEquals(3, result.getUpdatedPosts());
        assertEquals(2, result.getUpdatedChunks());
        AssistantRagIndex saved = readIndex();
        List<AssistantRagChunk> chunks = saved.getChunks();
        assertEquals(2, chunks.size());
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.getPostNum() == 1));
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.getPostNum() == 3));
        assertFalse(chunks.stream().anyMatch(chunk -> chunk.getPostNum() == 2));
        assertTrue(chunks.stream().allMatch(chunk ->
                AssistantRagSources.STRATEGY_TIP_BOARD.equals(chunk.getBoardTitle())));
    }

    private void writeExistingIndex(Date regDate) throws Exception {
        AssistantRagIndex index = new AssistantRagIndex();
        index.setEmbeddingModel("test-embedding-model");
        index.setDimension(2);
        index.setCreatedAt(regDate);
        index.setUpdatedAt(regDate);

        AssistantRagChunk chunk = new AssistantRagChunk();
        chunk.setId("freeboard:1:0:existing");
        chunk.setBoardTitle("freeboard");
        chunk.setPostNum(1);
        chunk.setTitle("same");
        chunk.setRegDate(regDate);
        chunk.setUrl("/boards/freeboard/readPost?postNum=1");
        chunk.setChunkIndex(0);
        chunk.setText("same body");
        chunk.setVector(new float[]{0.1f, 0.2f});
        index.getChunks().add(chunk);

        objectMapper.writeValue(tempDir.resolve("rag-index.json").toFile(), index);
    }

    private void writeStrategyTipIndex() throws Exception {
        AssistantRagIndex index = new AssistantRagIndex();
        index.setEmbeddingModel("test-embedding-model");
        index.setDimension(2);
        index.setCreatedAt(new Date(1_700_000_000_000L));
        index.setUpdatedAt(new Date(1_700_000_000_000L));
        index.getChunks().add(strategyTipChunk(1, "한줄 공략 · 테저전 기존 문장"));
        index.getChunks().add(strategyTipChunk(2, "한줄 공략 · 저테전 삭제될 문장"));
        objectMapper.writeValue(tempDir.resolve("rag-index.json").toFile(), index);
    }

    private AssistantRagChunk strategyTipChunk(int tipNum, String text) {
        AssistantRagChunk chunk = new AssistantRagChunk();
        chunk.setId(AssistantRagSources.STRATEGY_TIP_BOARD + ":" + tipNum + ":0:existing");
        chunk.setBoardTitle(AssistantRagSources.STRATEGY_TIP_BOARD);
        chunk.setPostNum(tipNum);
        chunk.setTitle("기존 한줄 공략");
        chunk.setRegDate(new Date(1_700_000_000_000L));
        chunk.setUrl(AssistantRagSources.STRATEGY_TIP_URL);
        chunk.setChunkIndex(0);
        chunk.setText(text);
        chunk.setVector(new float[]{0.1f, 0.2f});
        return chunk;
    }

    private StrategyTipDTO strategyTip(int tipNum, String category, String categoryName,
                                       String content) {
        StrategyTipDTO tip = new StrategyTipDTO();
        tip.setTipNum(tipNum);
        tip.setCategory(category);
        tip.setCategoryName(categoryName);
        tip.setContent(content);
        tip.setWriter("SC1Hub");
        tip.setRegDate(new Date(1_700_000_000_000L + tipNum));
        return tip;
    }

    private AssistantRagIndex readIndex() throws Exception {
        return objectMapper.readValue(
                tempDir.resolve("rag-index.json").toFile(), AssistantRagIndex.class);
    }
}
