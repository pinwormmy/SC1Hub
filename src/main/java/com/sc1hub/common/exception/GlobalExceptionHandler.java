package com.sc1hub.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.nio.file.AccessDeniedException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDeniedException(AccessDeniedException e, Model model) {
        log.error("AccessDeniedException: {}", e.getMessage());
        model.addAttribute("msg", e.getMessage() != null ? e.getMessage() : "접근 권한이 없습니다.");
        model.addAttribute("url", "/");
        return "alert";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgumentException(IllegalArgumentException e, Model model) {
        log.error("IllegalArgumentException: {}", e.getMessage());
        model.addAttribute("msg", e.getMessage());
        model.addAttribute("url", "/");
        return "alert";
    }

    @ExceptionHandler(Exception.class)
    public String handleException(Exception e, Model model) {
        log.error("Exception occurred: ", e);
        model.addAttribute("msg", "알 수 없는 오류가 발생했습니다.");
        model.addAttribute("url", "/");
        return "alert";
    }
}
