package com.sc1hub.visitorCount;

import com.sc1hub.mapper.VisitorCountMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Service
public class VisitorCountServiceImpl implements VisitorCountService {
    @Autowired
    private VisitorCountMapper visitorCountMapper;

    @Transactional
    public void incrementVisitorCount() {
        LocalDate today = LocalDate.now();
        VisitorCountDTO visitorCount = visitorCountMapper.findByDate(today);

        Integer totalCount = visitorCountMapper.getTotalCount();
        if (totalCount == null) {
            totalCount = 0;
        }

        if (visitorCount != null) {
            visitorCountMapper.incrementDailyCount(today);
        } else {
            visitorCountMapper.insertNewRecord(today, totalCount);
        }

        visitorCountMapper.incrementTotalCount();
    }

    public int getTotalCount() {
        Integer totalCount = visitorCountMapper.getTotalCount();
        return totalCount != null ? totalCount : 0; // null 체크
    }

    public int getTodayCount() {
        LocalDate today = LocalDate.now();
        Integer todayCount = visitorCountMapper.getTodayCount(today);
        return todayCount != null ? todayCount : 0; // null 체크
    }

    public void processVisitor(javax.servlet.http.HttpServletRequest request,
            javax.servlet.http.HttpServletResponse response) {
        javax.servlet.http.Cookie[] cookies = request.getCookies();
        boolean isVisitor = false;

        if (cookies != null) {
            for (javax.servlet.http.Cookie cookie : cookies) {
                if ("visitor".equals(cookie.getName())) {
                    isVisitor = true;
                    break;
                }
            }
        }

        if (!isVisitor) {
            createVisitorCookie(response);
            incrementVisitorCount();
        }
    }

    private void createVisitorCookie(javax.servlet.http.HttpServletResponse response) {
        javax.servlet.http.Cookie visitorCookie = new javax.servlet.http.Cookie("visitor", "true");

        // 오늘의 자정까지의 시간을 초 단위로 계산
        java.time.LocalDateTime midnight = java.time.LocalDateTime.of(LocalDate.now().plusDays(1),
                java.time.LocalTime.MIDNIGHT);
        int secondsUntilMidnight = (int) java.time.Duration.between(java.time.LocalDateTime.now(), midnight)
                .getSeconds();

        visitorCookie.setMaxAge(secondsUntilMidnight); // 자정까지
        visitorCookie.setPath("/"); // 전체 도메인에서 유효
        response.addCookie(visitorCookie);
    }
}
