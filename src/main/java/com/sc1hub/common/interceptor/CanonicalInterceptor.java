package com.sc1hub.common.interceptor;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class CanonicalInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        StringBuilder canonicalUrl = new StringBuilder("https://sc1hub.com");
        canonicalUrl.append(request.getRequestURI());

        // 쿼리 스트링 추가
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            canonicalUrl.append('?').append(queryString);
        }

        request.setAttribute("canonical", canonicalUrl.toString());
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
        // 필요한 경우 후처리 로직
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 요청 처리 완료 후 호출
    }
}
