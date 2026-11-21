package com.sc1hub.buildtimer.mapper;

import com.sc1hub.buildtimer.dto.BuildTimerFeedbackDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BuildTimerFeedbackMapper {

    int insertFeedback(BuildTimerFeedbackDTO feedback);

    List<BuildTimerFeedbackDTO> selectRecentFeedback(@Param("status") String status, @Param("limit") int limit);

    int countFeedback(@Param("status") String status);

    int updateStatus(@Param("id") long id, @Param("status") String status);

    int deleteFeedback(@Param("id") long id);
}
