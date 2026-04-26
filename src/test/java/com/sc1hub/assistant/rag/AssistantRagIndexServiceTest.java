package com.sc1hub.assistant.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.assistant.config.AssistantRagProperties;
import com.sc1hub.assistant.config.GeminiProperties;
import com.sc1hub.assistant.gemini.GeminiEmbeddingClient;
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
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
