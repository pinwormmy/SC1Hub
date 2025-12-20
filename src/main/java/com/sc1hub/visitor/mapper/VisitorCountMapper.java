package com.sc1hub.visitor.mapper;

import com.sc1hub.visitor.dto.VisitorCountDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface VisitorCountMapper {
    VisitorCountDTO findByDate(@Param("date") LocalDate date);
    void incrementDailyCount(@Param("date") LocalDate date);
    void incrementTotalCount();
    void insertNewRecord(@Param("date") LocalDate date, @Param("totalCount") int totalCount);
    Integer getTotalCount();
    Integer getTodayCount(@Param("date") LocalDate date);
}

