package com.jworks.forge.gen.context;

/**
 * TemplateContext 구성 단계의 하드 실패(P4-2).
 *
 * <p>stem 화이트리스트 위반, DEFINITION_JSON 파싱 실패, 필수 입력 누락 등
 * <b>컨텍스트를 안전하게 구성할 수 없는</b> 경우에 던진다. 계약 §5.3상 이 단계 실패는
 * (아무 파일도 못 쓰므로) 상위 파이프라인에서 {@code RESULT_CODE=FAIL}로 이어진다.
 * 미지원 slotKey/moduleTypeCode 같은 forward-compat 스킵은 예외가 아니라 로그 경고다.
 */
public class TemplateContextException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TemplateContextException(String message) {
        super(message);
    }

    public TemplateContextException(String message, Throwable cause) {
        super(message, cause);
    }
}
