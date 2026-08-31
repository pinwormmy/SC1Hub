package com.sc1hub.common.interceptor;

import com.sc1hub.visitor.service.VisitorCountService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Locale;

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
        if (isSpeculativeLoad(request)) {
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

    /** 프리페치/프리렌더 요청은 사용자가 실제로 열지 않을 수 있어 방문으로 세지 않는다. */
    private boolean isSpeculativeLoad(HttpServletRequest request) {
        String secPurpose = request.getHeader("Sec-Purpose");
        if (secPurpose != null && secPurpose.toLowerCase(Locale.ROOT).contains("prefetch")) {
            return true;
        }
        return "prefetch".equalsIgnoreCase(request.getHeader("Purpose"));
    }
}
