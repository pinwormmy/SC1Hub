package com.sc1hub.visitor.service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface VisitorCountService {
    void incrementVisitorCount();

    int getTotalCount();

    int getTodayCount();

    void processVisitor(HttpServletRequest request, HttpServletResponse response);
}
