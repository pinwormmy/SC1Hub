package com.sc1hub.assistant.controller;

import com.sc1hub.assistant.dto.AliasDictionaryDTO;
import com.sc1hub.assistant.dto.AliasDictionaryFormDTO;
import com.sc1hub.assistant.service.AliasDictionaryAdminService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/alias-dictionary")
@Slf4j
public class AliasDictionaryAdminApiController {

    private final AliasDictionaryAdminService aliasDictionaryAdminService;

    public AliasDictionaryAdminApiController(AliasDictionaryAdminService aliasDictionaryAdminService) {
        this.aliasDictionaryAdminService = aliasDictionaryAdminService;
    }

    @GetMapping
    public List<AliasDictionaryDTO> list(@RequestParam(required = false) String keyword) {
        return aliasDictionaryAdminService.list(keyword);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AliasDictionaryDTO> get(@PathVariable("id") long id) {
        AliasDictionaryDTO alias = aliasDictionaryAdminService.findById(id);
        if (alias == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(alias);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody AliasDictionaryFormDTO form) {
        String validation = validateForm(form, false);
        if (validation != null) {
            return buildError(HttpStatus.BAD_REQUEST, validation);
        }
        try {
            AliasDictionaryDTO created = aliasDictionaryAdminService.create(form);
            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("item", created);
            return ResponseEntity.ok(body);
        } catch (DuplicateKeyException e) {
            log.warn("alias_dictionary alias 중복", e);
            return buildError(HttpStatus.CONFLICT, "이미 등록된 alias 입니다.");
        } catch (Exception e) {
            log.error("alias_dictionary 등록 실패", e);
            return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "등록 중 오류가 발생했습니다.");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable("id") long id,
                                                      @RequestBody AliasDictionaryFormDTO form) {
        if (form == null) {
            return buildError(HttpStatus.BAD_REQUEST, "입력값이 없습니다.");
        }
        form.setId(id);
        String validation = validateForm(form, true);
        if (validation != null) {
            return buildError(HttpStatus.BAD_REQUEST, validation);
        }
        try {
            boolean updated = aliasDictionaryAdminService.update(form);
            if (!updated) {
                return buildError(HttpStatus.NOT_FOUND, "수정 대상이 존재하지 않습니다.");
            }
            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            return ResponseEntity.ok(body);
        } catch (DuplicateKeyException e) {
            log.warn("alias_dictionary alias 중복", e);
            return buildError(HttpStatus.CONFLICT, "이미 등록된 alias 입니다.");
        } catch (Exception e) {
            log.error("alias_dictionary 수정 실패", e);
            return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "수정 중 오류가 발생했습니다.");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable("id") long id) {
        try {
            boolean deleted = aliasDictionaryAdminService.delete(id);
            if (!deleted) {
                return buildError(HttpStatus.NOT_FOUND, "삭제 대상이 존재하지 않습니다.");
            }
            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("alias_dictionary 삭제 실패", e);
            return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "삭제 중 오류가 발생했습니다.");
        }
    }

    private ResponseEntity<Map<String, Object>> buildError(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    private String validateForm(AliasDictionaryFormDTO form, boolean requireId) {
        if (form == null) {
            return "입력값이 없습니다.";
        }
        if (requireId && form.getId() == null) {
            return "수정 대상이 없습니다.";
        }
        if (!StringUtils.hasText(form.getAlias())) {
            return "alias는 필수입니다.";
        }
        if (!StringUtils.hasText(form.getCanonicalTerms())) {
            return "canonical terms는 필수입니다.";
        }
        return null;
    }
}
