package com.jworks.forge.screen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 화면 생성/메타수정 입력(빈 검증). DEFINITION_JSON 본문은 이 DTO에 없다 —
 * 생성 시 서버가 최소 골격으로 채우고, 본문 갱신은 P3-6b 전용 엔드포인트가 담당한다.
 *
 * <p>{@code stem}은 파일 접두(파일명·식별자로 재사용)이므로 스키마_DEFINITION_JSON.md §1.1과 동일한
 * 정규식({@code ^[a-z][a-zA-Z0-9]*$})으로 형태를 검증한다. 이는 저장 시점 1차 데이터 방어이며,
 * 최종 경로안전은 P4의 PathSafetyService가 담당한다(§5 이중 경계).
 */
public record ForgeScreenRequest(

        @NotNull(message = "projectId는 필수")
        Long projectId,

        @NotBlank(message = "screenName은 필수")
        @Size(max = 200)
        String screenName,

        @NotBlank(message = "stem은 필수")
        @Pattern(
                regexp = "^[a-z][a-zA-Z0-9]*$",
                message = "stem은 소문자로 시작하는 영숫자만 허용(^[a-z][a-zA-Z0-9]*$)")
        @Size(max = 100)
        String stem,

        @NotBlank(message = "archetypeCode는 필수")
        @Size(max = 50)
        String archetypeCode,

        @NotBlank(message = "roleCode는 필수")
        @Pattern(
                regexp = "^[a-z][a-zA-Z0-9]*$",
                message = "roleCode는 소문자로 시작하는 영숫자만 허용(^[a-z][a-zA-Z0-9]*$)")
        @Size(max = 50)
        String roleCode,

        /** 메타 수정(PUT)에서만 사용. 생성 시에는 서버가 DRAFT로 설정한다. */
        @Size(max = 50)
        String statusCode
) {
}
