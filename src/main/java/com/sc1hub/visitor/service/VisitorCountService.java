package com.sc1hub.visitor.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface VisitorCountService {
    void incrementVisitorCount();

    int getTotalCount();

    int getTodayCount();

    void processVisitor(HttpServletRequest request, HttpServletResponse response);
}
