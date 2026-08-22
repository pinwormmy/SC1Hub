package com.sc1hub.common.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

public class CookieUtils {

    public static Cookie getPostViewCookie(HttpServletRequest request, int postNum) {
        String cookieName = "postView_" + postNum;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(cookieName)) {
                    return cookie;
                }
            }
        }
        return null;
    }

    public static Cookie createPostViewCookie(int postNum) {
        String cookieName = "postView_" + postNum;
        Cookie cookie = new Cookie(cookieName, "viewed");
        cookie.setMaxAge(5 * 60); // 5분
        cookie.setPath("/");
        return cookie;
    }
}
