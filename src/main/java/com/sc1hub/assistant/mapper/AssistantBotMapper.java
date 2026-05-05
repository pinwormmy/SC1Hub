package com.sc1hub.assistant.mapper;

import com.sc1hub.assistant.dto.AssistantBotHistoryDTO;
import com.sc1hub.assistant.dto.AssistantBotHistorySummaryDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AssistantBotMapper {
    void insertHistory(@Param("history") AssistantBotHistoryDTO history);

    List<AssistantBotHistoryDTO> selectRecentHistory(@Param("personaName") String personaName,
                                                     @Param("boardTitle") String boardTitle,
                                                     @Param("limit") int limit);

    AssistantBotHistoryDTO selectHistoryById(@Param("id") long id);

    List<AssistantBotHistoryDTO> selectRecentHistorySince(@Param("since") LocalDateTime since,
                                                          @Param("limit") int limit);

    List<AssistantBotHistorySummaryDTO> selectHistorySummarySince(@Param("since") LocalDateTime since);

    void updateStatus(@Param("id") long id,
                      @Param("status") String status,
                      @Param("publishedPostNum") Integer publishedPostNum);

    int countPublishedSinceByMode(@Param("personaName") String personaName,
                                  @Param("boardTitle") String boardTitle,
                                  @Param("generationMode") String generationMode,
                                  @Param("since") LocalDateTime since);

    int countGeneratedSinceByMode(@Param("personaName") String personaName,
                                  @Param("boardTitle") String boardTitle,
                                  @Param("generationMode") String generationMode,
                                  @Param("since") LocalDateTime since);
}
