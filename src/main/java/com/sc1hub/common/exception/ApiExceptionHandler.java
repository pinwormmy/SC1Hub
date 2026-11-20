package com.sc1hub.common.exception;

import com.sc1hub.board.support.InvalidBoardException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.nio.file.AccessDeniedException;
import java.util.Collections;
import java.util.Map;

/**
 * {@link RestController} 기반 JSON API에서 발생한 예외가 HTML {@code alert.jsp}로 렌더링되지 않도록
 * 상태 코드에 맞는 JSON 오류 응답으로 통일한다.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ResourceNotFoundException e) {
        log.warn("API ResourceNotFoundException: {}", e.getMessage());
        return error(HttpStatus.NOT_FOUND, e.getMessage(), "요청한 리소스를 찾을 수 없습니다.");
    }

    @ExceptionHandler(InvalidBoardException.class)
    public ResponseEntity<Map<String, String>> handleInvalidBoard(InvalidBoardException e) {
        log.warn("API InvalidBoardException: {}", e.getMessage());
        return error(HttpStatus.NOT_FOUND, e.getMessage(), "올바르지 않은 게시판 주소입니다.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException e) {
        log.warn("API AccessDeniedException: {}", e.getMessage());
        return error(HttpStatus.FORBIDDEN, e.getMessage(), "접근 권한이 없습니다.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("API IllegalArgumentException: {}", e.getMessage());
        return error(HttpStatus.BAD_REQUEST, e.getMessage(), "요청이 올바르지 않습니다.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        HttpHeaders headers = new HttpHeaders();
        String[] supported = e.getSupportedMethods();
        if (supported != null && supported.length > 0) {
            headers.set(HttpHeaders.ALLOW, String.join(", ", supported));
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Collections.singletonMap("error", "지원하지 않는 요청 방식입니다."));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            BindException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<Map<String, String>> handleBadRequest(Exception e) {
        log.warn("API bad request: {}", e.getClass().getSimpleName());
        return error(HttpStatus.BAD_REQUEST, null, "요청 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handlePayloadTooLarge(MaxUploadSizeExceededException e) {
        log.warn("API payload too large: {}", e.getMessage());
        return error(HttpStatus.PAYLOAD_TOO_LARGE, null, "업로드 용량 제한을 초과했습니다.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception e) {
        log.error("API unexpected exception", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, null, "요청 처리 중 오류가 발생했습니다.");
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message, String fallback) {
        String bodyMessage = (message != null && !message.isBlank()) ? message : fallback;
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Collections.singletonMap("error", bodyMessage));
    }
}
