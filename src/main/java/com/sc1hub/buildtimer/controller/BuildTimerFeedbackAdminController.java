package com.sc1hub.buildtimer.controller;

import com.sc1hub.buildtimer.dto.BuildTimerFeedbackDTO;
import com.sc1hub.buildtimer.service.BuildTimerFeedbackService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자 전용 빌드 타이머 (beta) 피드백 조회·상태 변경·삭제 API.
 * {@code /api/admin/**} 는 AdminInterceptor 가 보호한다.
 */
@RestController
@RequestMapping("/api/admin/build-timer/feedback")
public class BuildTimerFeedbackAdminController {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final BuildTimerFeedbackService feedbackService;

    public BuildTimerFeedbackAdminController(BuildTimerFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> listFeedback(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {
        try {
            List<BuildTimerFeedbackDTO> rows = feedbackService.getRecentFeedback(status, limit);
            List<Map<String, Object>> items = new ArrayList<>(rows.size());
            for (BuildTimerFeedbackDTO row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", row.getId());
                item.put("buildId", row.getBuildId());
                item.put("buildLabel", row.getBuildLabel());
                item.put("category", row.getCategory());
                item.put("message", row.getMessage());
                item.put("contact", row.getContact());
                item.put("memberId", row.getMemberId());
                item.put("nickname", row.getNickname());
                item.put("status", row.getStatus());
                item.put("regDate", row.getRegDate() == null ? null : DATE_TIME_FORMAT.format(row.getRegDate()));
                item.put("userAgent", row.getUserAgent());
                items.add(item);
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("items", items);
            response.put("newCount", feedbackService.countFeedback(BuildTimerFeedbackService.STATUS_NEW));
            response.put("totalCount", feedbackService.countFeedback(null));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping(value = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable("id") long id,
            @RequestBody(required = false) Map<String, Object> body) {
        String status = body == null || body.get("status") == null ? null : String.valueOf(body.get("status"));
        try {
            boolean updated = feedbackService.updateStatus(id, status);
            if (!updated) {
                return error(HttpStatus.NOT_FOUND, "해당 피드백을 찾을 수 없습니다.");
            }
            String normalizedStatus = BuildTimerFeedbackService.requireValidStatus(status);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("id", id);
            response.put("status", normalizedStatus);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> deleteFeedback(@PathVariable("id") long id) {
        try {
            boolean deleted = feedbackService.deleteFeedback(id);
            if (!deleted) {
                return error(HttpStatus.NOT_FOUND, "해당 피드백을 찾을 수 없습니다.");
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("id", id);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }
}
