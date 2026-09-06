package com.sc1hub.member.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 회원 ID 별 활성 세션을 추적해, 관리자에 의한 등급 변경·탈퇴나 비밀번호 변경 시 이미 발급된
 * 세션을 즉시 회수할 수 있게 한다. 단일 인스턴스 인메모리 세션 환경 전용이며, 세션 소멸
 * 이벤트({@link com.sc1hub.common.interceptor.MemberSessionListener})가 항목을 정리하므로 누수가 없다.
 */
@Component
public class MemberSessionRegistry {

    private final ConcurrentHashMap<String, Set<HttpSession>> sessionsByMember = new ConcurrentHashMap<>();

    public void register(String memberId, HttpSession session) {
        if (memberId == null || session == null) {
            return;
        }
        sessionsByMember.computeIfAbsent(memberId, key -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregister(String memberId, HttpSession session) {
        if (memberId == null || session == null) {
            return;
        }
        sessionsByMember.computeIfPresent(memberId, (key, sessions) -> {
            sessions.remove(session);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    /** 해당 회원의 모든 세션을 무효화한다(현재 요청 세션 포함). */
    public void invalidateMember(String memberId) {
        invalidateMemberExcept(memberId, null);
    }

    /** {@code keep} 세션만 남기고 해당 회원의 다른 모든 세션을 무효화한다. */
    public void invalidateMemberExcept(String memberId, HttpSession keep) {
        if (memberId == null) {
            return;
        }
        Set<HttpSession> sessions = sessionsByMember.remove(memberId);
        if (sessions == null) {
            return;
        }
        for (HttpSession session : sessions) {
            if (session == keep) {
                register(memberId, session);
                continue;
            }
            try {
                session.invalidate();
            } catch (IllegalStateException alreadyInvalidated) {
                // 이미 무효화된 세션은 무시한다.
            }
        }
    }
}
