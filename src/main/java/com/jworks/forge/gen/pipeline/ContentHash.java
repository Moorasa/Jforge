package com.jworks.forge.gen.pipeline;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 산출물 내용 해시 (P12, 계약 §16). <b>순수 함수.</b>
 *
 * <p>생성 시점의 내용 해시를 GEN_HIST에 남겨두면, 다음 생성 때 디스크의 실제 파일과 대조해
 * <b>"마지막 생성 이후 사람이 손댔는가"</b>(드리프트)를 판정할 수 있다. 해시 대상은
 * {@link AtomicFileWriter}가 쓰는 것과 동일한 <b>UTF-8 바이트</b>다(그래야 왕복 비교가 성립).
 */
public final class ContentHash {

    private ContentHash() {
    }

    /** 문자열의 UTF-8 SHA-256(소문자 16진). */
    public static String sha256(String content) {
        return sha256(content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8));
    }

    /** 바이트 배열의 SHA-256(소문자 16진). */
    public static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 JDK 표준 보장 알고리즘 — 도달 불가.
            throw new IllegalStateException("SHA-256 미지원", e);
        }
    }
}
