package com.jworks.forge.gen.template;

/**
 * 코드생성 템플릿의 해석·렌더 실패 시 던진다 (P4-1).
 * (템플릿 미존재, FreeMarker 파싱·렌더 오류 등을 명확한 런타임 예외로 감싼다 —
 * 생성 실패를 조용히 삼키지 않는다.)
 */
public class TemplateRenderException extends RuntimeException {

    public TemplateRenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
