package com.sc1hub.assistant.mapper;

import com.sc1hub.assistant.dto.AssistantSearchLogDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AssistantSearchLogMapper {
    void insertLog(@Param("message") String message);

    void deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);

    List<AssistantSearchLogDTO> selectLatest(@Param("limit") int limit);
}
