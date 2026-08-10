package com.jworks.forge.dbmeta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 🔒 P11: 타겟 DB 비밀번호가 평문으로 남지 않고, 위변조가 탐지되는지 고정한다. */
class SecretCipherTest {

    private static String randomKey() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }

    @Test
    void 설정키로_암복호_왕복이_무손상이다() {
        SecretCipher cipher = new SecretCipher(randomKey());
        assertTrue(cipher.isAvailable());

        String plain = "p@ss word:한글/특수!";
        String encrypted = cipher.encrypt(plain);

        assertNotEquals(plain, encrypted, "평문이 그대로 남으면 안 된다");
        assertFalseContains(encrypted, "p@ss");
        assertEquals(plain, cipher.decrypt(encrypted));
    }

    @Test
    void 같은_평문도_매번_다른_암호문이_된다() {
        SecretCipher cipher = new SecretCipher(randomKey());
        assertNotEquals(cipher.encrypt("same"), cipher.encrypt("same"), "IV가 매번 달라야 한다");
    }

    @Test
    void 위변조된_암호문은_거부된다() {
        SecretCipher cipher = new SecretCipher(randomKey());
        String encrypted = cipher.encrypt("secret");
        // 마지막 문자를 바꿔 GCM 태그를 깨뜨린다.
        char last = encrypted.charAt(encrypted.length() - 1);
        String tampered = encrypted.substring(0, encrypted.length() - 1) + (last == 'A' ? 'B' : 'A');

        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered));
    }

    @Test
    void 다른_키로는_복호되지_않는다() {
        String encrypted = new SecretCipher(randomKey()).encrypt("secret");
        SecretCipher other = new SecretCipher(randomKey());
        assertThrows(IllegalStateException.class, () -> other.decrypt(encrypted));
    }

    @Test
    void 설정키가_없으면_홈에_키파일을_만들고_재사용한다(@TempDir Path home) throws Exception {
        String originalHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", home.toString());

            SecretCipher first = new SecretCipher("");
            assertTrue(first.isAvailable());
            Path keyFile = home.resolve(".j-forge").resolve("secret.key");
            assertTrue(Files.isRegularFile(keyFile), "키 파일이 생성되어야 한다");

            String encrypted = first.encrypt("secret");
            // 같은 홈으로 새 인스턴스를 만들면 저장된 키를 읽어 복호할 수 있어야 한다.
            assertEquals("secret", new SecretCipher("").decrypt(encrypted));
        } finally {
            System.setProperty("user.home", originalHome);
        }
    }

    @Test
    void 잘못된_길이의_설정키는_무시된다(@TempDir Path home) {
        String originalHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", home.toString());
            // 16바이트(길이 불일치) → 설정키 무시 후 키 파일 경로로 폴백(기능은 살아 있음).
            byte[] shortKey = new byte[16];
            SecretCipher cipher = new SecretCipher(Base64.getEncoder().encodeToString(shortKey));
            assertTrue(cipher.isAvailable());
            assertEquals("x", cipher.decrypt(cipher.encrypt("x")));
        } finally {
            System.setProperty("user.home", originalHome);
        }
    }

    private static void assertFalseContains(String haystack, String needle) {
        if (haystack.contains(needle)) {
            throw new AssertionError("암호문에 평문 조각이 노출됨");
        }
    }
}
