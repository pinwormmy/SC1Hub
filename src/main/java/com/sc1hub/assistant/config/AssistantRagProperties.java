package com.sc1hub.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sc1hub.assistant.rag")
public class AssistantRagProperties {
    private boolean enabled = false;
    private String indexPath = "data/assistant/rag-index.json";

    private int maxPostsPerBoard = 1000;

    private int chunkSizeChars = 900;
    private int chunkOverlapChars = 150;

    private int searchTopChunks = 12;

    private AutoUpdateProperties autoUpdate = new AutoUpdateProperties();

    @Data
    public static class AutoUpdateProperties {
        private boolean enabled = false;
        private String cron = "0 0 5 * * *";
        private String zone = "";
    }
}
