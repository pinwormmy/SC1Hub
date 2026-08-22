package com.sc1hub.visitor.service;

import com.sc1hub.visitor.mapper.VisitorCountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    void incrementVisitorCount_upsertsDailyAndTotalCounts() {
        visitorCountService.incrementVisitorCount();

        verify(visitorCountMapper).upsertDailyCount(LocalDate.of(2026, 3, 9));
        verify(visitorCountMapper).incrementTotalCount();
    }

    @Test
    void processVisitor_addsVisitorCookieUntilMidnight_whenCookieMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 Test Browser");
        request.setRemoteAddr("203.0.113.10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(visitorCountMapper.insertDailyVisitor(eq(LocalDate.of(2026, 3, 9)), any(String.class))).thenReturn(1);

        visitorCountService.processVisitor(request, response);

        Cookie cookie = response.getCookie("visitor");
        assertNotNull(cookie);
        assertEquals("true", cookie.getValue());
        assertEquals("/", cookie.getPath());
        assertEquals(14 * 60 * 60, cookie.getMaxAge());
        assertTrue(cookie.isHttpOnly());
        verify(visitorCountMapper).upsertDailyCount(LocalDate.of(2026, 3, 9));
        verify(visitorCountMapper).incrementTotalCount();
        verify(visitorCountMapper).deleteDailyVisitorsBefore(LocalDate.of(2026, 3, 9));
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

    @Test
    void processVisitor_skipsKnownBot() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 (compatible; GoogleOther)");
        MockHttpServletResponse response = new MockHttpServletResponse();

        visitorCountService.processVisitor(request, response);

        verifyNoInteractions(visitorCountMapper);
        assertEquals(0, response.getCookies().length);
    }

    @Test
    void processVisitor_doesNotIncrementWhenAnonymousIdentityAlreadyExists() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 Test Browser");
        request.addHeader("X-Forwarded-For", "203.0.113.20, 10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(visitorCountMapper.insertDailyVisitor(eq(LocalDate.of(2026, 3, 9)), any(String.class))).thenReturn(0);

        visitorCountService.processVisitor(request, response);

        assertNotNull(response.getCookie("visitor"));
        verify(visitorCountMapper, never()).upsertDailyCount(any(LocalDate.class));
        verify(visitorCountMapper, never()).incrementTotalCount();
        verify(visitorCountMapper, never()).deleteDailyVisitorsBefore(any(LocalDate.class));
    }

    @Test
    void processVisitor_ignoresRawForwardedForWhenBuildingIdentity() {
        when(visitorCountMapper.insertDailyVisitor(eq(LocalDate.of(2026, 3, 9)), any(String.class))).thenReturn(0);

        MockHttpServletRequest firstRequest = browserRequest("203.0.113.30");
        firstRequest.addHeader("X-Forwarded-For", "198.51.100.10");
        MockHttpServletRequest secondRequest = browserRequest("203.0.113.30");
        secondRequest.addHeader("X-Forwarded-For", "198.51.100.99");

        visitorCountService.processVisitor(firstRequest, new MockHttpServletResponse());
        visitorCountService.processVisitor(secondRequest, new MockHttpServletResponse());

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(visitorCountMapper, times(2))
                .insertDailyVisitor(eq(LocalDate.of(2026, 3, 9)), hashCaptor.capture());
        assertEquals(hashCaptor.getAllValues().get(0), hashCaptor.getAllValues().get(1));
    }

    @Test
    void processVisitor_cleansOldIdentitiesOnlyOncePerDay() {
        when(visitorCountMapper.insertDailyVisitor(eq(LocalDate.of(2026, 3, 9)), any(String.class))).thenReturn(1);

        visitorCountService.processVisitor(browserRequest("203.0.113.40"), new MockHttpServletResponse());
        visitorCountService.processVisitor(browserRequest("203.0.113.41"), new MockHttpServletResponse());

        verify(visitorCountMapper, times(2)).upsertDailyCount(LocalDate.of(2026, 3, 9));
        verify(visitorCountMapper, times(2)).incrementTotalCount();
        verify(visitorCountMapper).deleteDailyVisitorsBefore(LocalDate.of(2026, 3, 9));
    }

    @Test
    void processVisitor_skipsRequestWithoutUserAgent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.50");

        visitorCountService.processVisitor(request, new MockHttpServletResponse());

        verifyNoInteractions(visitorCountMapper);
    }

    private MockHttpServletRequest browserRequest(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 Test Browser");
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
