package com.sc1hub.common.interceptor;

import com.sc1hub.visitor.service.VisitorCountService;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class VisitorCountInterceptor implements HandlerInterceptor {

    private final VisitorCountService visitorCountService;

    public VisitorCountInterceptor(VisitorCountService visitorCountService) {
        this.visitorCountService = visitorCountService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute("todayCount", visitorCountService.getTodayCount());
        request.setAttribute("totalCount", visitorCountService.getTotalCount());
        return true;
    }
}
