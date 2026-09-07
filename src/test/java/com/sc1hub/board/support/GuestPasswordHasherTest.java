package com.sc1hub.board.support;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuestPasswordHasherTest {

    private final GuestPasswordHasher hasher = new GuestPasswordHasher(new BCryptPasswordEncoder(4));

    @Test
    void hashesAndVerifiesWithoutStoringThePlaintext() {
        String hash = hasher.hash("1234");
        assertNotEquals("1234", hash);
        assertTrue(hasher.isHashed(hash));
        assertTrue(hasher.matches("1234", hash));
        assertFalse(hasher.matches("4321", hash));
        assertFalse(hasher.matches(null, hash));
    }

    @Test
    void legacyPlaintextRowsStillVerifyUntilMigrated() {
        assertFalse(hasher.isHashed("secret"));
        assertTrue(hasher.matches("secret", "secret"));
        assertFalse(hasher.matches("secret ", "secret"));
        assertFalse(hasher.matches("secret", ""));
        assertFalse(hasher.matches("secret", null));
    }

    @Test
    void longPasswordsKeepTheirFullLengthDespiteBcryptTruncation() {
        String base = "a".repeat(90);
        String hash = hasher.hash(base + "x");
        assertTrue(hasher.matches(base + "x", hash));
        // BCrypt 만 쓰면 72바이트 이후는 무시되지만, 사전 해시 덕분에 뒷부분이 달라도 구분된다.
        assertFalse(hasher.matches(base + "y", hash));
    }

    @Test
    void randomSecretsAreUniqueAndUnknowable() {
        String first = GuestPasswordHasher.newRandomSecret();
        String second = GuestPasswordHasher.newRandomSecret();
        assertNotEquals(first, second);
        assertTrue(first.length() >= 32);
        assertFalse(hasher.isHashed(first));
    }
}
