package com.sc1hub.visitor.service;

public interface VisitorCountService {
    void incrementVisitorCount();

    int getTotalCount();

    int getTodayCount();

    void processVisitor(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response);
}
