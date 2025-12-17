package com.sc1hub.common.util;

import javax.servlet.http.HttpServletRequest;

// 차후 접속자 로그 기능에 활용..깃허브 리셋위해 다시

public class IpService {
    // 밥먹고 처잔다고 한게 없네;; 알바뛰고 나서 시간관리 잘 안되고 있다..
    public static String getRemoteIP(HttpServletRequest request) {
        String ip = request.getHeader("X-FORWARDED-FOR");
        if (ip == null || ip.length() == 0) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        } // 웹로직 서버
        if (ip == null || ip.length() == 0) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
