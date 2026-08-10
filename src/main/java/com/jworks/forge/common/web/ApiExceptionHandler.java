package com.jworks.forge.common.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.jworks.forge.screen.validation.DefinitionValidationException;

/**
 * REST 공통 예외 → 표준 상태코드 매핑.
 * NotFound=404, 검증/형식오류=400.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /**
     * DEFINITION_JSON 구조 검증 실패(P3-5b) → 400. 여러 위반이 message에 " | "로 합쳐져 온다.
     */
    @ExceptionHandler(DefinitionValidationException.class)
    public ResponseEntity<Map<String, Object>> handleDefinitionValidation(DefinitionValidationException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * 타겟 DB 카탈로그 조회 실패(P11) → 502. 빌더 자신의 오류가 아니라 <b>외부 DB</b> 응답 문제이므로
     * 400/500과 구분한다(사용자가 접속정보·네트워크를 점검하도록).
     */
    @ExceptionHandler(com.jworks.forge.dbmeta.service.DbIntrospectionService.DbMetaException.class)
    public ResponseEntity<Map<String, Object>> handleDbMeta(
            com.jworks.forge.dbmeta.service.DbIntrospectionService.DbMetaException e) {
        return body(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("검증 실패");
        return body(HttpStatus.BAD_REQUEST, msg);
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status.value());
        m.put("error", status.getReasonPhrase());
        m.put("message", message);
        return ResponseEntity.status(status).body(m);
    }
}
