package com.sc1hub.visitor.service;

import com.sc1hub.visitor.dto.VisitorCountDTO;
import com.sc1hub.visitor.mapper.VisitorCountMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Slf4j
@Service
public class VisitorCountServiceImpl implements VisitorCountService {
    private static final String VISITOR_COOKIE_NAME = "visitor";
    private static final String VISITOR_COOKIE_VALUE = "true";
    private static final String ROOT_PATH = "/";

    private final VisitorCountMapper visitorCountMapper;
    private final Clock clock;

    public VisitorCountServiceImpl(VisitorCountMapper visitorCountMapper) {
        this(visitorCountMapper, Clock.systemDefaultZone());
    }

    VisitorCountServiceImpl(VisitorCountMapper visitorCountMapper, Clock clock) {
        this.visitorCountMapper = visitorCountMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void incrementVisitorCount() {
        LocalDate today = LocalDate.now(clock);
        VisitorCountDTO visitorCount = visitorCountMapper.findByDate(today);

        int totalCount = nullToZero(visitorCountMapper.getTotalCount());

        if (visitorCount != null) {
            visitorCountMapper.incrementDailyCount(today);
        } else {
            visitorCountMapper.insertNewRecord(today, totalCount);
        }

        visitorCountMapper.incrementTotalCount();
    }

    @Override
    public int getTotalCount() {
        return nullToZero(visitorCountMapper.getTotalCount());
    }

    @Override
    public int getTodayCount() {
        LocalDate today = LocalDate.now(clock);
        return nullToZero(visitorCountMapper.getTodayCount(today));
    }

    @Override
    public void processVisitor(HttpServletRequest request, HttpServletResponse response) {
        if (!hasVisitorCookie(request.getCookies())) {
            createVisitorCookie(response);
            incrementVisitorCount();
        }
    }

    private boolean hasVisitorCookie(Cookie[] cookies) {
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (VISITOR_COOKIE_NAME.equals(cookie.getName())) {
                return true;
            }
        }
        return false;
    }

    private void createVisitorCookie(HttpServletResponse response) {
        Cookie visitorCookie = new Cookie(VISITOR_COOKIE_NAME, VISITOR_COOKIE_VALUE);

        visitorCookie.setMaxAge(secondsUntilTomorrow());
        visitorCookie.setPath(ROOT_PATH);
        response.addCookie(visitorCookie);
    }

    private int secondsUntilTomorrow() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime midnight = LocalDateTime.of(LocalDate.now(clock).plusDays(1), LocalTime.MIDNIGHT);
        return (int) Duration.between(now, midnight).getSeconds();
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
