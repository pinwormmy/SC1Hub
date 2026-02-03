package com.sc1hub.assistant.rag;

import com.sc1hub.assistant.config.AssistantRagProperties;
import com.sc1hub.assistant.search.AssistantSearchTermsIndexService;
import com.sc1hub.assistant.search.AssistantSearchTermsService;
import com.sc1hub.board.mapper.BoardMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Collections;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantRagScheduledUpdaterTest {

    @Mock
    private AssistantRagIndexService ragIndexService;

    @Mock
    private BoardMapper boardMapper;

    @Mock
    private AssistantSearchTermsService searchTermsService;

    private AssistantSearchTermsIndexService searchTermsIndexService;
    private AssistantRagProperties ragProperties;
    private AssistantRagScheduledUpdater updater;

    @BeforeEach
    void setUp() {
        searchTermsIndexService = Mockito.spy(new AssistantSearchTermsIndexService(boardMapper, searchTermsService));
        ragProperties = new AssistantRagProperties();
        ragProperties.getAutoUpdate().setEnabled(true);
        updater = new AssistantRagScheduledUpdater(ragIndexService, searchTermsIndexService, ragProperties);
    }

    @Test
    void autoUpdate_skipsEverythingWhenAutoUpdateDisabled() {
        ragProperties.getAutoUpdate().setEnabled(false);

        updater.autoUpdate();

        verifyNoInteractions(ragIndexService);
        verifyNoInteractions(boardMapper);
    }

    @Test
    void autoUpdate_runsSearchTermsEvenWhenRagDisabled() throws IOException {
        when(ragIndexService.update()).thenReturn(AssistantRagIndexService.UpdateResult.disabled("disabled"));
        when(boardMapper.getBoardList()).thenReturn(Collections.emptyList());

        updater.autoUpdate();

        verify(ragIndexService).update();
        verify(searchTermsIndexService).reindexAllDefault();
    }

    @Test
    void autoUpdate_stillRunsSearchTermsWhenRagUpdateThrows() throws IOException {
        when(ragIndexService.update()).thenThrow(new IOException("boom"));
        when(boardMapper.getBoardList()).thenReturn(Collections.emptyList());

        updater.autoUpdate();

        verify(searchTermsIndexService).reindexAllDefault();
    }
}

