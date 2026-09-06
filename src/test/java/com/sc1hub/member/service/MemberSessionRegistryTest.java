package com.sc1hub.member.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberSessionRegistryTest {

    private final MemberSessionRegistry registry = new MemberSessionRegistry();

    @Test
    void invalidateMember_invalidatesEveryRegisteredSession() {
        MockHttpSession first = new MockHttpSession();
        MockHttpSession second = new MockHttpSession();
        registry.register("user", first);
        registry.register("user", second);

        registry.invalidateMember("user");

        assertTrue(first.isInvalid());
        assertTrue(second.isInvalid());
    }

    @Test
    void invalidateMemberExcept_keepsTheCurrentSessionButRevokesOthers() {
        MockHttpSession keep = new MockHttpSession();
        MockHttpSession other = new MockHttpSession();
        registry.register("user", keep);
        registry.register("user", other);

        registry.invalidateMemberExcept("user", keep);

        assertFalse(keep.isInvalid(), "현재 세션은 유지되어야 한다");
        assertTrue(other.isInvalid(), "다른 기기의 세션은 회수되어야 한다");

        // keep 은 다시 등록되어 이후 회수 대상이 된다.
        registry.invalidateMember("user");
        assertTrue(keep.isInvalid());
    }

    @Test
    void unregister_removesSessionSoItIsNoLongerTracked() {
        MockHttpSession session = new MockHttpSession();
        registry.register("user", session);
        registry.unregister("user", session);

        registry.invalidateMember("user");

        assertFalse(session.isInvalid(), "등록 해제된 세션은 회수 대상이 아니다");
    }
}
