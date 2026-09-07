package com.sc1hub.assistant.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.assistant.config.AssistantRagProperties;
import com.sc1hub.assistant.config.GeminiProperties;
import com.sc1hub.assistant.gemini.GeminiEmbeddingClient;
import com.sc1hub.board.dto.BoardListDTO;
import com.sc1hub.board.mapper.BoardMapper;
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

    private static final String BOARD = "promotionboard";

    @Mock
    private GeminiEmbeddingClient embeddingClient;

    @Mock
    private BoardMapper boardMapper;

    @TempDir
    private Path tempDir;

    @Test
    void search_returnsBoardChunkAndValidatesItsSnapshot() throws Exception {
        Date regDate = new Date(1_700_000_000_000L);
        Path indexPath = tempDir.resolve("rag-index.json");
        writeBoardIndex(indexPath, regDate);

        AssistantRagProperties ragProperties = new AssistantRagProperties();
        ragProperties.setEnabled(true);
        ragProperties.setIndexPath(indexPath.toString());
        ragProperties.setMinScore(0.1);
        ragProperties.setMinScoreRatio(0.1);
        GeminiProperties geminiProperties = new GeminiProperties();
        geminiProperties.setEmbeddingModel("test-embedding-model");
        BoardListDTO board = new BoardListDTO();
        board.setBoardTitle("PromotionBoard");
        when(boardMapper.getBoardList()).thenReturn(Collections.singletonList(board));
        when(boardMapper.selectBoardRagStats(BOARD)).thenReturn(boardSnapshot(regDate));
        when(embeddingClient.embedText("테저전 수비 위치"))
                .thenReturn(new float[]{1.0f, 0.0f});

        AssistantRagSearchService service = new AssistantRagSearchService(
                ragProperties, geminiProperties, embeddingClient, new ObjectMapper(),
                boardMapper, new AssistantProperties());

        List<AssistantRagSearchService.Match> matches =
                service.search("테저전 수비 위치", 3);
        AssistantRagSearchService.Status status = service.getStatus();

        assertEquals(1, matches.size());
        assertEquals(BOARD, matches.get(0).getChunk().getBoardTitle());
        assertEquals(7, matches.get(0).getChunk().getPostNum());
        assertEquals("/boards/" + BOARD + "/readPost?postNum=7", matches.get(0).getChunk().getUrl());
        assertTrue(status.isSignatureAvailable());
        assertFalse(status.isSignatureMismatch());
        // incomplete 필드가 없는 구버전 인덱스 파일은 완성본으로 본다.
        assertTrue(status.isComplete());
    }

    private void writeBoardIndex(Path indexPath, Date regDate) throws Exception {
        AssistantRagIndex index = new AssistantRagIndex();
        index.setEmbeddingModel("test-embedding-model");
        index.setDimension(2);
        index.setCreatedAt(regDate);
        index.setUpdatedAt(regDate);

        AssistantRagChunk chunk = new AssistantRagChunk();
        chunk.setId(BOARD + ":7:0:test");
        chunk.setBoardTitle(BOARD);
        chunk.setPostNum(7);
        chunk.setTitle("테저전 수비 위치");
        chunk.setRegDate(regDate);
        chunk.setUrl("/boards/" + BOARD + "/readPost?postNum=7");
        chunk.setChunkIndex(0);
        chunk.setText("테저전 정찰 후 상대 진출에 맞춰 수비 위치를 조정하세요.");
        chunk.setVector(new float[]{1.0f, 0.0f});
        index.getChunks().add(chunk);
        index.getBoardSnapshots().add(boardSnapshot(regDate));
        new ObjectMapper().writeValue(indexPath.toFile(), index);
    }

    private AssistantRagBoardSnapshot boardSnapshot(Date regDate) {
        AssistantRagBoardSnapshot snapshot = new AssistantRagBoardSnapshot();
        snapshot.setBoardTitle(BOARD);
        snapshot.setMaxPostNum(7);
        snapshot.setMaxRegDate(regDate);
        snapshot.setPostCount(1);
        return snapshot;
    }
}
