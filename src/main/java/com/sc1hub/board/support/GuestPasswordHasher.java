package com.sc1hub.board.support;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * 비회원 글·댓글 비밀번호를 회원 비밀번호와 같은 BCrypt 로 저장·검증한다.
 *
 * <p>비회원 비밀번호는 최대 100자를 받으므로 BCrypt 의 72바이트 절단을 피하려고 SHA-256 → Base64 로
 * 먼저 고정 길이(44자)로 줄인 값을 해시한다. 저장값이 BCrypt 형식이 아니면(전환 전 평문 행) 상수 시간
 * 비교로 검증해 기존 글의 수정·삭제가 계속 되게 한다. 전환은 {@code GuestPasswordMigration} 이 백그라운드로
 * 수행한다.
 */
@Component
public class GuestPasswordHasher {

    private static final Pattern BCRYPT_FORMAT = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    private final PasswordEncoder passwordEncoder;

    public GuestPasswordHasher(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    /** 평문을 저장용 해시로 바꾼다. 빈 값은 그대로 돌려준다(호출자가 이미 필수 검사를 한다). */
    public String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            return rawPassword;
        }
        return passwordEncoder.encode(prehash(rawPassword));
    }

    /** 저장값이 해시면 BCrypt 로, 아직 평문이면 상수 시간 비교로 검증한다. */
    public boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null || storedPassword.isEmpty()) {
            return false;
        }
        if (isHashed(storedPassword)) {
            return passwordEncoder.matches(prehash(rawPassword), storedPassword);
        }
        return MessageDigest.isEqual(
                storedPassword.getBytes(StandardCharsets.UTF_8),
                rawPassword.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 아무도 모르는 무작위 비밀번호. 봇이 발행하는 글·댓글처럼 "비밀번호로 수정할 일이 없는" 비회원 행에
     * 쓴다(관리자는 비밀번호 없이 관리한다). 공유 비밀번호 하나로 모든 봇 글이 열리던 구조를 없앤다.
     */
    public static String newRandomSecret() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public boolean isHashed(String storedPassword) {
        return storedPassword != null && BCRYPT_FORMAT.matcher(storedPassword).matches();
    }

    private static String prehash(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 사용할 수 없습니다.", e);
        }
    }
}
