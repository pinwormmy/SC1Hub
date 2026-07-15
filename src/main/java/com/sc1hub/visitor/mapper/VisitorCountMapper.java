package com.sc1hub.visitor.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface VisitorCountMapper {
    void upsertDailyCount(@Param("date") LocalDate date);
    void incrementTotalCount();
    Integer getTotalCount();
    Integer getTodayCount(@Param("date") LocalDate date);
    int insertDailyVisitor(@Param("date") LocalDate date, @Param("visitorHash") String visitorHash);
    void deleteDailyVisitorsBefore(@Param("date") LocalDate date);
}
