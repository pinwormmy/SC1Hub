package com.sc1hub.member.controller;

import com.sc1hub.member.mapper.MemberMapper;
import com.sc1hub.member.service.LegacyPasswordMigration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 관리자 전용 보안 상태 API. {@code /api/admin/**} 는 AdminInterceptor 가 보호한다.
 * 비밀번호 값은 어떤 형태로도 반환하지 않고 집계와 실행 요약만 준다.
 */
@RestController
@RequestMapping("/api/admin/security")
public class SecurityAdminController {

    private final MemberMapper memberMapper;
    private final LegacyPasswordMigration legacyPasswordMigration;

    public SecurityAdminController(MemberMapper memberMapper, LegacyPasswordMigration legacyPasswordMigration) {
        this.memberMapper = memberMapper;
        this.legacyPasswordMigration = legacyPasswordMigration;
    }

    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("legacyPasswordCount", memberMapper.countLegacyPasswordMembers());
        body.put("legacyPasswordMigration", legacyPasswordMigration.getLastRun());
        return body;
    }

    @PostMapping(value = "/legacy-passwords/migrate", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> migrateLegacyPasswords() {
        LegacyPasswordMigration.Summary summary = legacyPasswordMigration.run();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("legacyPasswordMigration", summary);
        body.put("legacyPasswordCount", memberMapper.countLegacyPasswordMembers());
        return body;
    }
}
