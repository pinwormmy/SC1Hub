package com.sc1hub.assistant.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.assistant.config.AssistantRagProperties;
import com.sc1hub.assistant.config.GeminiProperties;
import com.sc1hub.assistant.dto.AssistantRelatedPostDTO;
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
import static org.junit.jupiter.api.Assertions.assertSame;
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

    @Test
    void findSimilarPosts_ranksByCosineSimilarityFiltersExcludedAndCaches() throws Exception {
        Date baseDate = new Date(1_700_000_000_000L);
        Path indexPath = tempDir.resolve("similar-rag-index.json");

        AssistantRagIndex index = new AssistantRagIndex();
        index.setEmbeddingModel("test-embedding-model");
        index.setDimension(2);
        index.setCreatedAt(baseDate);
        index.setUpdatedAt(baseDate);

        // 기준 글 (tipboard #11): 두 청크 평균 방향 (1.0, 0.0)
        index.getChunks().add(chunk("tipboard", 11, 0, "장비 추천", baseDate, new float[]{1.0f, 0.2f}));
        index.getChunks().add(chunk("tipboard", 11, 1, "장비 추천", baseDate, new float[]{1.0f, -0.2f}));
        // 최고 유사도 후보 (tipboard #92)
        index.getChunks().add(chunk("tipboard", 92, 0, "키보드 키 씹힘 해결", baseDate, new float[]{0.99f, 0.1f}));
        // 두 번째 후보 (tvspboard #11)
        index.getChunks().add(chunk("tvspboard", 11, 0, "팩더블 운영", baseDate, new float[]{0.8f, 0.6f}));
        // 임계치(0.5) 미만 후보
        index.getChunks().add(chunk("zvspboard", 2, 0, "973 빌드", baseDate, new float[]{0.2f, 0.98f}));
        // 제외 게시판 후보 (funboard)
        index.getChunks().add(chunk("funboard", 5, 0, "자유 잡담", baseDate, new float[]{1.0f, 0.0f}));
        new ObjectMapper().writeValue(indexPath.toFile(), index);

        AssistantRagProperties ragProperties = new AssistantRagProperties();
        ragProperties.setEnabled(true);
        ragProperties.setIndexPath(indexPath.toString());
        ragProperties.setMinScore(0.5);

        AssistantProperties assistantProperties = new AssistantProperties();
        assistantProperties.setExcludedBoards(List.of("funboard", "noticeboard"));

        AssistantRagSearchService service = new AssistantRagSearchService(
                ragProperties, new GeminiProperties(), embeddingClient, new ObjectMapper(),
                boardMapper, assistantProperties);

        List<AssistantRelatedPostDTO> first = service.findSimilarPosts("tipBoard", 11, 4);
        List<AssistantRelatedPostDTO> second = service.findSimilarPosts("tipboard", 11, 4);

        assertEquals(2, first.size());
        assertEquals("tipboard", first.get(0).getBoardTitle());
        assertEquals(92, first.get(0).getPostNum());
        assertEquals("/boards/tipboard/readPost?postNum=92", first.get(0).getUrl());
        assertEquals("tvspboard", first.get(1).getBoardTitle());
        assertEquals(11, first.get(1).getPostNum());
        assertSame(first, second);
        assertTrue(service.findSimilarPosts("funboard", 5, 4).isEmpty());

        ragProperties.setEnabled(false);
        assertTrue(service.findSimilarPosts("tipboard", 11, 4).isEmpty());
    }

    @Test
    void findSimilarPosts_returnsEmptyWhenIndexMissing() {
        AssistantRagProperties ragProperties = new AssistantRagProperties();
        ragProperties.setEnabled(true);
        ragProperties.setIndexPath(tempDir.resolve("missing-index.json").toString());

        AssistantRagSearchService service = new AssistantRagSearchService(
                ragProperties, new GeminiProperties(), embeddingClient, new ObjectMapper(),
                boardMapper, new AssistantProperties());

        assertTrue(service.findSimilarPosts("tipboard", 11, 4).isEmpty());
    }

    private AssistantRagChunk chunk(String boardTitle, int postNum, int chunkIndex,
                                    String title, Date regDate, float[] vector) {
        AssistantRagChunk chunk = new AssistantRagChunk();
        chunk.setId(boardTitle + ":" + postNum + ":" + chunkIndex + ":test");
        chunk.setBoardTitle(boardTitle);
        chunk.setPostNum(postNum);
        chunk.setTitle(title);
        chunk.setRegDate(regDate);
        chunk.setUrl("/boards/" + boardTitle + "/readPost?postNum=" + postNum);
        chunk.setChunkIndex(chunkIndex);
        chunk.setText(title);
        chunk.setVector(vector);
        return chunk;
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
