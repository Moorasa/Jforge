package com.jworks.forge.screen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 화면 복제 입력 (P7-4). 원본 화면의 slots(모듈 배치·props)는 그대로 두고
 * 이름/stem 만 새로 받아 새 화면을 만든다. stem 규칙은 {@link ForgeScreenRequest}와 동일
 * (파일명·식별자 재사용 — §1.1 정규식 1차 방어, 최종 경로안전은 PathSafetyService).
 */
public record ForgeScreenDuplicateRequest(

        @NotBlank(message = "screenName은 필수")
        @Size(max = 200)
        String screenName,

        @NotBlank(message = "stem은 필수")
        @Pattern(
                regexp = "^[a-z][a-zA-Z0-9]*$",
                message = "stem은 소문자로 시작하는 영숫자만 허용(^[a-z][a-zA-Z0-9]*$)")
        @Size(max = 100)
        String stem
) {
}
