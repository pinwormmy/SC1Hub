package com.sc1hub.visitor.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface VisitorCountService {
    void incrementVisitorCount();

    int getTotalCount();

    int getTodayCount();

    void processVisitor(HttpServletRequest request, HttpServletResponse response);

    // 스케줄 작업. JDK 프록시(spring.aop.proxy-target-class=false)가 호출할 수 있도록 인터페이스에 선언한다.
    void cleanupOldIdentities();
}
