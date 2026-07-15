package com.sc1hub.common.interceptor;

import com.sc1hub.visitor.service.VisitorCountService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class VisitorCountInterceptor implements HandlerInterceptor {

    private final VisitorCountService visitorCountService;

    public VisitorCountInterceptor(VisitorCountService visitorCountService) {
        this.visitorCountService = visitorCountService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (isCountableGet(request)) {
            visitorCountService.processVisitor(request, response);
        }
        request.setAttribute("todayCount", visitorCountService.getTodayCount());
        request.setAttribute("totalCount", visitorCountService.getTotalCount());
        return true;
    }

    private boolean isCountableGet(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }

        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = contextPath.isEmpty() ? requestUri : requestUri.substring(contextPath.length());

        return !path.startsWith("/adminPage")
                && !path.equals("/myPage")
                && !path.equals("/modifyMyInfo")
                && !path.contains("/writePost")
                && !path.contains("/modifyPost");
    }
}
