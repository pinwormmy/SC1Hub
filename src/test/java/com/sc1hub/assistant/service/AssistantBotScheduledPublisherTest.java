package com.sc1hub.assistant.service;

import com.sc1hub.assistant.config.AssistantBotProperties;
import com.sc1hub.common.monitoring.MetaspaceUsageLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantBotScheduledPublisherTest {

    @Mock
    private AssistantBotService assistantBotService;

    @Mock
    private MetaspaceUsageLogger metaspaceUsageLogger;

    private AssistantBotProperties botProperties;
    private AssistantBotScheduledPublisher publisher;

    @BeforeEach
    void setUp() {
        botProperties = new AssistantBotProperties();
        botProperties.setEnabled(true);
        botProperties.setAutoPublishEnabled(true);
        publisher = new AssistantBotScheduledPublisher(
                assistantBotService,
                botProperties,
                metaspaceUsageLogger
        );
    }

    @Test
    void autoPublish_skipsBeforeBotWorkWhenMetaspaceHeadroomIsLow() {
        when(metaspaceUsageLogger.shouldPauseAiWork()).thenReturn(true);

        publisher.autoPublish();

        verifyNoInteractions(assistantBotService);
    }
}
