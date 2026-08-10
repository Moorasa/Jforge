package com.jworks.forge.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 프로젝트 생성/수정 입력. 형식 검증은 여기(빈 검증) + 경로 절대성은 서비스에서.
 */
public record ForgeProjectRequest(

        @NotBlank(message = "projectName은 필수")
        @Size(max = 200)
        String projectName,

        @NotBlank(message = "targetRootPath는 필수")
        @Size(max = 1000)
        String targetRootPath,

        // 생성 엔진이 Controller/Mapper stub 의 폴더 경로 세그먼트로 쓰므로 **필수**다.
        // 예전에는 빈 값을 허용해(^$|) NULL 로 저장됐고, 그 프로젝트로 파일을 생성하면
        // 컨텍스트 구성 단계에서야 "화이트리스트 위반"으로 막혀 원인을 알기 어려웠다.
        @NotBlank(message = "packageBase는 필수 — 생성될 Controller/Mapper 의 패키지 경로가 됩니다")
        @Pattern(
                regexp = "^([a-z][a-z0-9]*)(\\.[a-z][a-z0-9]*)*$",
                message = "packageBase는 소문자 자바 패키지 형식이어야 함 (예: com.acme.app)")
        @Size(max = 300)
        String packageBase,

        @Size(max = 500) String jspBasePath,
        @Size(max = 500) String jsBasePath,
        @Size(max = 500) String cssBasePath,
        @Size(max = 50) String dbTypeCode,
        @Size(max = 50) String runtimeVer
) {
}
