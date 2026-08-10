package com.jworks.forge.dbmeta;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 🔒 대칭키 암복호 유틸 (P11, 계약 §15). 타겟 DB 비밀번호를 <b>평문으로 저장하지 않기 위한</b> 최소 구현.
 *
 * <p><b>알고리즘</b>: AES-256/GCM(무결성 포함). 저장 형태는 {@code base64(iv(12B) || ciphertext||tag)}.
 * JDK 표준 {@code javax.crypto}만 사용한다(외부 의존성 0 — 버전 충돌 표면 없음).
 *
 * <p><b>키 조달 순서</b>:
 * <ol>
 *   <li>설정 {@code forge.secret.key}(base64 32바이트)가 있으면 그것을 쓴다(운영/CI에서 주입).</li>
 *   <li>없으면 사용자 홈의 {@code ~/.j-forge/secret.key}를 읽는다.</li>
 *   <li>그 파일도 없으면 {@link SecureRandom}으로 <b>새로 생성해 기록</b>한다(첫 실행 자동 셋업).</li>
 * </ol>
 * 어느 단계도 성공하지 못하면 {@link #isAvailable()}이 {@code false}가 되고, 호출측은 DB 연결 기능을
 * <b>비활성</b>으로 응답한다. <b>하드코딩 기본키·평문 폴백은 없다</b>(안전측 수렴).
 *
 * <p>주의: 이 키는 <b>로컬 개발 도구</b> 수준의 보호다. 키 파일을 읽을 수 있는 사용자는 복호도 가능하다
 * (같은 사용자 계정에서 도는 도구이므로 위협모델상 동일 경계). 팀 서버로 승격할 때는 외부 시크릿
 * 관리자로 {@code forge.secret.key}를 주입하는 경로를 쓴다.
 */
@Component
public class SecretCipher {

    private static final Logger log = LoggerFactory.getLogger(SecretCipher.class);

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;   // AES-256
    private static final int IV_BYTES = 12;    // GCM 권장
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final SecretKey key;

    public SecretCipher(@Value("${forge.secret.key:}") String configuredKey) {
        this.key = resolveKey(configuredKey);
    }

    /** 키가 조달됐는지. false면 DB 접속정보 저장/사용이 불가능하다(기능 비활성). */
    public boolean isAvailable() {
        return key != null;
    }

    /** 평문 → {@code base64(iv||ct)}. */
    public String encrypt(String plain) {
        requireAvailable();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            // 원문·키는 절대 로그에 남기지 않는다.
            throw new IllegalStateException("암호화 실패: " + e.getClass().getSimpleName());
        }
    }

    /** {@code base64(iv||ct)} → 평문. 위변조(GCM 태그 불일치) 시 예외. */
    public String decrypt(String encoded) {
        requireAvailable();
        try {
            byte[] raw = Base64.getDecoder().decode(encoded);
            if (raw.length <= IV_BYTES) {
                throw new IllegalArgumentException("암호문 길이 부족");
            }
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(raw, 0, iv, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(raw, IV_BYTES, raw.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("복호화 실패: " + e.getClass().getSimpleName());
        }
    }

    private void requireAvailable() {
        if (key == null) {
            throw new IllegalStateException("암호화 키가 없어 DB 접속정보를 다룰 수 없습니다.");
        }
    }

    /** 설정 → 키 파일 → 신규 생성 순으로 키를 조달한다. 전부 실패하면 null(기능 비활성). */
    private SecretKey resolveKey(String configuredKey) {
        if (configuredKey != null && !configuredKey.isBlank()) {
            try {
                byte[] raw = Base64.getDecoder().decode(configuredKey.trim());
                if (raw.length != KEY_BYTES) {
                    log.error("[SecretCipher] forge.secret.key는 base64 인코딩된 {}바이트여야 합니다 — 무시", KEY_BYTES);
                } else {
                    log.info("[SecretCipher] 설정 키 사용");
                    return new SecretKeySpec(raw, "AES");
                }
            } catch (IllegalArgumentException e) {
                log.error("[SecretCipher] forge.secret.key base64 디코드 실패 — 무시");
            }
        }
        return loadOrCreateKeyFile();
    }

    /** {@code ~/.j-forge/secret.key}를 읽고, 없으면 생성한다. 실패 시 null. */
    private SecretKey loadOrCreateKeyFile() {
        try {
            Path dir = Path.of(System.getProperty("user.home"), ".j-forge");
            Path file = dir.resolve("secret.key");
            if (Files.isRegularFile(file)) {
                byte[] raw = Base64.getDecoder().decode(Files.readString(file, StandardCharsets.UTF_8).trim());
                if (raw.length == KEY_BYTES) {
                    return new SecretKeySpec(raw, "AES");
                }
                log.error("[SecretCipher] 키 파일 길이 불일치 — DB 연결 기능 비활성: {}", file);
                return null;
            }
            Files.createDirectories(dir);
            byte[] raw = new byte[KEY_BYTES];
            random.nextBytes(raw);
            Files.writeString(file, Base64.getEncoder().encodeToString(raw), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            restrictToOwner(file);
            log.info("[SecretCipher] 로컬 키 파일 생성: {}", file);
            return new SecretKeySpec(raw, "AES");
        } catch (Exception e) {
            log.error("[SecretCipher] 키 조달 실패 — DB 연결 기능 비활성: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    /** 소유자 외 접근 제한(가능한 파일시스템에서만 — 실패는 치명적이지 않으므로 경고만). */
    private void restrictToOwner(Path file) {
        try {
            var view = Files.getFileAttributeView(file, java.nio.file.attribute.PosixFileAttributeView.class);
            if (view != null) {
                view.setPermissions(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
                return;
            }
            // Windows: 상속 권한을 그대로 두되(사용자 프로필 하위) 최소한 읽기전용 표시는 하지 않는다.
            log.debug("[SecretCipher] POSIX 권한 미지원 파일시스템 — 사용자 프로필 권한에 의존");
        } catch (Exception e) {
            log.warn("[SecretCipher] 키 파일 권한 설정 실패(계속): {}", e.getClass().getSimpleName());
        }
    }
}
