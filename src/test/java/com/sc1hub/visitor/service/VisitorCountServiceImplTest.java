package com.sc1hub.visitor.service;

import com.sc1hub.visitor.dto.VisitorCountDTO;
import com.sc1hub.visitor.mapper.VisitorCountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisitorCountServiceImplTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-03-09T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    @Mock
    private VisitorCountMapper visitorCountMapper;

    private VisitorCountServiceImpl visitorCountService;

    @BeforeEach
    void setUp() {
        visitorCountService = new VisitorCountServiceImpl(visitorCountMapper, FIXED_CLOCK);
    }

    @Test
    void incrementVisitorCount_incrementsExistingDailyRecord() {
        when(visitorCountMapper.findByDate(any(LocalDate.class))).thenReturn(new VisitorCountDTO());
        when(visitorCountMapper.getTotalCount()).thenReturn(10);

        visitorCountService.incrementVisitorCount();

        verify(visitorCountMapper).incrementDailyCount(any(LocalDate.class));
        verify(visitorCountMapper, never()).insertNewRecord(any(LocalDate.class), anyInt());
        verify(visitorCountMapper).incrementTotalCount();
    }

    @Test
    void incrementVisitorCount_insertsNewRecord_whenMissing() {
        when(visitorCountMapper.findByDate(any(LocalDate.class))).thenReturn(null);
        when(visitorCountMapper.getTotalCount()).thenReturn(null);

        visitorCountService.incrementVisitorCount();

        verify(visitorCountMapper).insertNewRecord(any(LocalDate.class), eq(0));
        verify(visitorCountMapper, never()).incrementDailyCount(any(LocalDate.class));
        verify(visitorCountMapper).incrementTotalCount();
    }

    @Test
    void processVisitor_addsVisitorCookieUntilMidnight_whenCookieMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(visitorCountMapper.findByDate(LocalDate.of(2026, 3, 9))).thenReturn(new VisitorCountDTO());
        when(visitorCountMapper.getTotalCount()).thenReturn(10);

        visitorCountService.processVisitor(request, response);

        Cookie cookie = response.getCookie("visitor");
        assertNotNull(cookie);
        assertEquals("true", cookie.getValue());
        assertEquals("/", cookie.getPath());
        assertEquals(14 * 60 * 60, cookie.getMaxAge());
        verify(visitorCountMapper).incrementDailyCount(LocalDate.of(2026, 3, 9));
        verify(visitorCountMapper).incrementTotalCount();
    }

    @Test
    void processVisitor_skipsCount_whenVisitorCookieExists() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("visitor", "true"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        visitorCountService.processVisitor(request, response);

        verifyNoInteractions(visitorCountMapper);
        assertEquals(0, response.getCookies().length);
    }
}
