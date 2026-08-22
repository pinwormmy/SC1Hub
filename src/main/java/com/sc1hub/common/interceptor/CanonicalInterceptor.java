package com.sc1hub.common.interceptor;

import com.sc1hub.seo.SeoUrlPolicy;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class CanonicalInterceptor implements HandlerInterceptor {

    private final SeoUrlPolicy seoUrlPolicy;

    public CanonicalInterceptor(SeoUrlPolicy seoUrlPolicy) {
        this.seoUrlPolicy = seoUrlPolicy;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        SeoUrlPolicy.ResolvedUrl resolvedUrl = seoUrlPolicy.resolve(request);
        request.setAttribute("canonical", resolvedUrl.getCanonical());
        request.setAttribute("robots", resolvedUrl.getRobots());

        if (resolvedUrl.getRobots().startsWith("noindex")) {
            response.setHeader("X-Robots-Tag", resolvedUrl.getRobots());
        }

        if (resolvedUrl.getRedirectLocation() != null && isSafeRedirectMethod(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
            response.setHeader("Location", resolvedUrl.getRedirectLocation());
            response.setHeader("Cache-Control", "public, max-age=86400");
            return false;
        }
        return true;
    }

    private boolean isSafeRedirectMethod(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }
}
