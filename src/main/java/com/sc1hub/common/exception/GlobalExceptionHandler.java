package com.sc1hub.common.exception;

import com.sc1hub.board.support.InvalidBoardException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.nio.file.AccessDeniedException;
import jakarta.servlet.http.HttpServletResponse;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final String ERROR_ROBOTS = "noindex,nofollow,noarchive";

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

    @ExceptionHandler(InvalidBoardException.class)
    public String handleInvalidBoardException(InvalidBoardException e, Model model,
                                              HttpServletResponse response) {
        // 삭제된 게시판 주소를 크롤러가 계속 두드리는 일상적 요청이라 404 + 한 줄 WARN 으로 처리한다.
        // (IllegalArgumentException 의 하위 타입이라 아래 400 핸들러보다 이 핸들러가 먼저 선택된다.)
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setHeader("X-Robots-Tag", ERROR_ROBOTS);
        log.warn("InvalidBoardException: {}", e.getMessage());
        prepareErrorModel(model, e.getMessage(), "/");
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

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public String handleMethodNotSupported(HttpRequestMethodNotSupportedException e, Model model,
                                           HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        String[] supportedMethods = e.getSupportedMethods();
        if (supportedMethods != null && supportedMethods.length > 0) {
            response.setHeader("Allow", String.join(", ", supportedMethods));
        }
        response.setHeader("X-Robots-Tag", ERROR_ROBOTS);
        log.warn("HttpRequestMethodNotSupportedException: method={}, supported={}",
                e.getMethod(), supportedMethods);
        prepareErrorModel(model, "지원하지 않는 요청 방식입니다.", "/");
        return "alert";
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public String handleMissingParameter(MissingServletRequestParameterException e, Model model,
                                         HttpServletResponse response) {
        // 크롤러가 postNum 등 필수 파라미터 없이 URL을 두드리는 일상적 요청이라
        // 스택트레이스 있는 ERROR가 아니라 한 줄 WARN + 400으로 처리한다.
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setHeader("X-Robots-Tag", ERROR_ROBOTS);
        log.warn("MissingServletRequestParameterException: {}", e.getMessage());
        prepareErrorModel(model, "필수 정보가 빠진 요청입니다.", "/");
        return "alert";
    }

    @ExceptionHandler({BindException.class, MethodArgumentTypeMismatchException.class})
    public String handleInvalidRequestValue(Exception e, Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setHeader("X-Robots-Tag", ERROR_ROBOTS);
        // 바인딩 예외 메시지에는 원본 입력값이 포함되므로 로그와 응답에 노출하지 않는다.
        log.warn("Invalid request value: {}", e.getClass().getSimpleName());
        prepareErrorModel(model, "요청 값의 형식이 올바르지 않습니다.", "/");
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
        model.addAttribute("robots", ERROR_ROBOTS);
        model.addAttribute("pageTitle", "안내 - SC1Hub");
        model.addAttribute("metaDescription", "요청을 처리할 수 없습니다.");
    }
}
