package com.jworks.forge.gen.pipeline;

/**
 * 원자적 파일쓰기(임시→rename)·백업·심볼릭링크 방어 중 실패 시 던진다 (P4-4).
 * (심볼릭링크 대상 거부, 임시파일 쓰기/이동 실패, 백업 실패 등을 명확한 런타임 예외로 감싼다.)
 */
public class AtomicWriteException extends RuntimeException {

    public AtomicWriteException(String message) {
        super(message);
    }

    public AtomicWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
