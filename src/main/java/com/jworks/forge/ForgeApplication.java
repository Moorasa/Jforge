package com.jworks.forge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * J-FORGE 부트스트랩 엔트리.
 * 독립 화면 빌더 — 팔레트 조립 → JSP+JS+CSS 3종을 타겟 프로젝트에 생성.
 */
@SpringBootApplication
public class ForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForgeApplication.class, args);
    }
}
