package com.sc1hub.assistant.rag;

import com.sc1hub.assistant.config.AssistantRagProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantRagScheduledUpdaterTest {

    @Mock
    private AssistantRagIndexService ragIndexService;

    private AssistantRagProperties ragProperties;
    private AssistantRagScheduledUpdater updater;

    @BeforeEach
    void setUp() {
        ragProperties = new AssistantRagProperties();
        ragProperties.getAutoUpdate().setEnabled(true);
        updater = new AssistantRagScheduledUpdater(ragIndexService, ragProperties);
    }

    @Test
    void autoUpdate_skipsEverythingWhenAutoUpdateDisabled() {
        ragProperties.getAutoUpdate().setEnabled(false);

        updater.autoUpdate();

        verifyNoInteractions(ragIndexService);
    }

    @Test
    void autoUpdate_runsRagUpdateWhenEnabled() throws IOException {
        when(ragIndexService.update()).thenReturn(AssistantRagIndexService.UpdateResult.disabled("disabled"));

        updater.autoUpdate();

        verify(ragIndexService).update();
    }

    @Test
    void autoUpdate_swallowsRagUpdateException() throws IOException {
        when(ragIndexService.update()).thenThrow(new IOException("boom"));

        assertDoesNotThrow(() -> updater.autoUpdate());

        verify(ragIndexService).update();
    }
}
