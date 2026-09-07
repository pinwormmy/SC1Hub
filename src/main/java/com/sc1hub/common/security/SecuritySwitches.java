package com.sc1hub.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 관리자가 재배포 없이 즉시 바꿀 수 있는 쓰기 스위치. 프로퍼티는 기동 시 기본값이며, 재시작하면
 * 기본값으로 돌아간다(도배 상황에서 몇 초 안에 쓰기를 얼릴 수 있게 하는 것이 목적).
 */
@Component
public class SecuritySwitches {

    private final boolean publicWritesDefault;
    private final boolean guestWritesDefault;
    private volatile boolean publicWritesEnabled;
    private volatile boolean guestWritesEnabled;

    public SecuritySwitches(
            @Value("${sc1hub.security.public-writes-enabled:true}") boolean publicWritesEnabled,
            @Value("${sc1hub.security.guest-writes-enabled:false}") boolean guestWritesEnabled) {
        this.publicWritesDefault = publicWritesEnabled;
        this.guestWritesDefault = guestWritesEnabled;
        this.publicWritesEnabled = publicWritesEnabled;
        this.guestWritesEnabled = guestWritesEnabled;
    }

    /** 회원의 글·댓글·채팅·이미지 업로드·회원정보 수정과 신규 가입을 허용하는지. */
    public boolean isPublicWritesEnabled() {
        return publicWritesEnabled;
    }

    public void setPublicWritesEnabled(boolean enabled) {
        this.publicWritesEnabled = enabled;
    }

    /** 비회원(로그인 없는) 글·댓글·채팅을 허용하는지. 기본은 허용하지 않음. */
    public boolean isGuestWritesEnabled() {
        return guestWritesEnabled;
    }

    public void setGuestWritesEnabled(boolean enabled) {
        this.guestWritesEnabled = enabled;
    }

    public boolean isPublicWritesDefault() {
        return publicWritesDefault;
    }

    public boolean isGuestWritesDefault() {
        return guestWritesDefault;
    }
}
