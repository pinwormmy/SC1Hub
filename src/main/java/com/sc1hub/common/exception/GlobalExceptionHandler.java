package com.sc1hub.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.nio.file.AccessDeniedException;
import javax.servlet.http.HttpServletResponse;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public String handleResourceNotFoundException(ResourceNotFoundException e, Model model,
                                                  HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        log.warn("ResourceNotFoundException: {}", e.getMessage());
        prepareErrorModel(model, e.getMessage(), "/");
        return "alert";
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDeniedException(AccessDeniedException e, Model model,
                                              HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        log.error("AccessDeniedException: {}", e.getMessage());
        prepareErrorModel(model, e.getMessage() != null ? e.getMessage() : "접근 권한이 없습니다.", "/");
        return "alert";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgumentException(IllegalArgumentException e, Model model,
                                                 HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        log.error("IllegalArgumentException: {}", e.getMessage());
        prepareErrorModel(model, e.getMessage(), "/");
        return "alert";
    }

    @ExceptionHandler(Exception.class)
    public String handleException(Exception e, Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        log.error("Exception occurred: ", e);
        prepareErrorModel(model, "알 수 없는 오류가 발생했습니다.", "/");
        return "alert";
    }

    private void prepareErrorModel(Model model, String message, String url) {
        model.addAttribute("msg", message);
        model.addAttribute("url", url);
        model.addAttribute("robots", "noindex,nofollow,noarchive");
        model.addAttribute("pageTitle", "안내 - SC1Hub");
        model.addAttribute("metaDescription", "요청을 처리할 수 없습니다.");
    }
}
