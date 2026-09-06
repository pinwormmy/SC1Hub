package com.sc1hub.common.util;

import jakarta.servlet.http.HttpServletRequest;

public final class IpService {
    private IpService() {
    }

    public static String getRemoteIP(HttpServletRequest request) {
        // Tomcat RemoteIpValve (server.forward-headers-strategy=native) resolves
        // trusted proxy chains. Never trust arbitrary client-supplied IP headers here.
        return request == null ? null : request.getRemoteAddr();
    }
}
