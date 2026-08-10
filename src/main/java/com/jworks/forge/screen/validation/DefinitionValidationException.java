package com.jworks.forge.screen.validation;

import java.util.Collections;
import java.util.List;

/**
 * DEFINITION_JSON 구조 검증 실패(P3-5b). 저장 경계(쓰기)에서 화이트리스트 밖 구조를 거부할 때 던진다.
 *
 * <p>여러 위반을 <b>한 번에 모아</b> 반환한다(UX: 사용자가 오류를 하나씩 고치며 재시도하지 않도록).
 * {@link com.jworks.forge.common.web.ApiExceptionHandler}가 이 예외를 400(BAD_REQUEST)으로 매핑한다.
 *
 * <p>여기서 방어하는 것은 <b>구조</b>(slotKey/moduleTypeCode/instanceId/archetype/role/카테고리/cardinality)뿐이며,
 * props의 자유문자열 값(label/displayName/styleClass 등)은 검증하지 않고 무가공 저장한다
 * (스키마_DEFINITION_JSON.md §5 — 표시 문자열 이스케이프는 소비자 책임).
 */
public class DefinitionValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final List<String> violations;

    public DefinitionValidationException(List<String> violations) {
        super(buildMessage(violations));
        this.violations = Collections.unmodifiableList(violations);
    }

    /** 위반 사유 목록(사유별 1문장). */
    public List<String> getViolations() {
        return violations;
    }

    private static String buildMessage(List<String> violations) {
        if (violations == null || violations.isEmpty()) {
            return "DEFINITION_JSON 검증 실패";
        }
        return "DEFINITION_JSON 검증 실패(" + violations.size() + "건): "
                + String.join(" | ", violations);
    }
}
