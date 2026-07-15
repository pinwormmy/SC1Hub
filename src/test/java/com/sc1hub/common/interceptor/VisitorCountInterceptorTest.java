package com.sc1hub.common.interceptor;

import com.sc1hub.visitor.service.VisitorCountService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VisitorCountInterceptorTest {

    @Test
    void preHandle_countsGetVisitBeforeExposingTotals() {
        VisitorCountService service = mock(VisitorCountService.class);
        when(service.getTodayCount()).thenReturn(7);
        when(service.getTotalCount()).thenReturn(70);
        VisitorCountInterceptor interceptor = new VisitorCountInterceptor(service);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        verify(service).processVisitor(request, response);
        assertEquals(7, request.getAttribute("todayCount"));
        assertEquals(70, request.getAttribute("totalCount"));
    }

    @Test
    void preHandle_doesNotCountNonGetRequest() {
        VisitorCountService service = mock(VisitorCountService.class);
        VisitorCountInterceptor interceptor = new VisitorCountInterceptor(service);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/strategy-tips");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        verify(service, never()).processVisitor(request, response);
    }

    @Test
    void preHandle_doesNotCountPrivateOrEditingPages() {
        VisitorCountService service = mock(VisitorCountService.class);
        VisitorCountInterceptor interceptor = new VisitorCountInterceptor(service);

        MockHttpServletRequest adminRequest = new MockHttpServletRequest("GET", "/adminPage/dashboard");
        interceptor.preHandle(adminRequest, new MockHttpServletResponse(), new Object());

        MockHttpServletRequest writeRequest = new MockHttpServletRequest("GET", "/boards/tipBoard/writePost");
        interceptor.preHandle(writeRequest, new MockHttpServletResponse(), new Object());

        verify(service, never()).processVisitor(any(), any());
    }
}
