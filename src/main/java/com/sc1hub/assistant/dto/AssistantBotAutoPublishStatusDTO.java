package com.sc1hub.assistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AssistantBotAutoPublishStatusDTO {
    private boolean enabled;
    private boolean autoPublishEnabled;
    private boolean autoPublishCatchUpEnabled;
    private String autoPublishCron;
    private String autoPublishZone;
    private int postDailyLimit;
    private int commentDailyLimit;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime serverNow;
    private List<PersonaStatusDTO> personas = new ArrayList<>();

    @Data
    public static class PersonaStatusDTO {
        private String personaName;
        private String boardTitle;
        private String model;
        private int handledPostToday;
        private int handledCommentToday;
        private int handledPostThisMinute;
        private int handledCommentThisMinute;
        private int handledPostRecoveryCooldown;
        private List<String> postSlots = new ArrayList<>();
        private List<String> commentSlots = new ArrayList<>();
        private String nextPostSlot;
        private String nextCommentSlot;
        private List<String> dueModes = new ArrayList<>();
        private String waitingDetail;
    }
}
