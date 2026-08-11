package com.sc1hub.assistant.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.assistant.config.AssistantRagProperties;
import com.sc1hub.assistant.config.GeminiProperties;
import com.sc1hub.assistant.gemini.GeminiEmbeddingClient;
import com.sc1hub.board.mapper.BoardMapper;
import com.sc1hub.strategytip.mapper.StrategyTipMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantRagSearchServiceTest {

    @Mock
    private GeminiEmbeddingClient embeddingClient;

    @Mock
    private BoardMapper boardMapper;

    @Mock
    private StrategyTipMapper strategyTipMapper;

    @TempDir
    private Path tempDir;

    @Test
    void search_returnsPublishedStrategyTipAndValidatesItsSnapshot() throws Exception {
        Date regDate = new Date(1_700_000_000_000L);
        Path indexPath = tempDir.resolve("rag-index.json");
        writeStrategyTipIndex(indexPath, regDate);

        AssistantRagProperties ragProperties = new AssistantRagProperties();
        ragProperties.setEnabled(true);
        ragProperties.setIndexPath(indexPath.toString());
        ragProperties.setMinScore(0.1);
        ragProperties.setMinScoreRatio(0.1);
        GeminiProperties geminiProperties = new GeminiProperties();
        geminiProperties.setEmbeddingModel("test-embedding-model");
        when(boardMapper.getBoardList()).thenReturn(Collections.emptyList());
        when(strategyTipMapper.selectRagStats()).thenReturn(strategyTipSnapshot(regDate));
        when(embeddingClient.embedText("테저전 수비 위치"))
                .thenReturn(new float[]{1.0f, 0.0f});

        AssistantRagSearchService service = new AssistantRagSearchService(
                ragProperties, geminiProperties, embeddingClient, new ObjectMapper(),
                boardMapper, strategyTipMapper, new AssistantProperties());

        List<AssistantRagSearchService.Match> matches =
                service.search("테저전 수비 위치", 3);
        AssistantRagSearchService.Status status = service.getStatus();

        assertEquals(1, matches.size());
        assertEquals(AssistantRagSources.STRATEGY_TIP_BOARD,
                matches.get(0).getChunk().getBoardTitle());
        assertEquals(7, matches.get(0).getChunk().getPostNum());
        assertEquals(AssistantRagSources.STRATEGY_TIP_URL,
                matches.get(0).getChunk().getUrl());
        assertTrue(status.isSignatureAvailable());
        assertFalse(status.isSignatureMismatch());
    }

    private void writeStrategyTipIndex(Path indexPath, Date regDate) throws Exception {
        AssistantRagIndex index = new AssistantRagIndex();
        index.setEmbeddingModel("test-embedding-model");
        index.setDimension(2);
        index.setCreatedAt(regDate);
        index.setUpdatedAt(regDate);

        AssistantRagChunk chunk = new AssistantRagChunk();
        chunk.setId(AssistantRagSources.STRATEGY_TIP_BOARD + ":7:0:test");
        chunk.setBoardTitle(AssistantRagSources.STRATEGY_TIP_BOARD);
        chunk.setPostNum(7);
        chunk.setTitle("한줄 공략 · 테저전");
        chunk.setRegDate(regDate);
        chunk.setUrl(AssistantRagSources.STRATEGY_TIP_URL);
        chunk.setChunkIndex(0);
        chunk.setText("한줄 공략 · 테저전 정찰 후 상대 진출에 맞춰 수비 위치를 조정하세요.");
        chunk.setVector(new float[]{1.0f, 0.0f});
        index.getChunks().add(chunk);
        index.getBoardSnapshots().add(strategyTipSnapshot(regDate));
        new ObjectMapper().writeValue(indexPath.toFile(), index);
    }

    private AssistantRagBoardSnapshot strategyTipSnapshot(Date regDate) {
        AssistantRagBoardSnapshot snapshot = new AssistantRagBoardSnapshot();
        snapshot.setBoardTitle(AssistantRagSources.STRATEGY_TIP_BOARD);
        snapshot.setMaxPostNum(7);
        snapshot.setMaxRegDate(regDate);
        snapshot.setPostCount(1);
        return snapshot;
    }
}
