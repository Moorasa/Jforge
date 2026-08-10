package com.jworks.forge.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 부트스트랩 스모크용 헬스 엔드포인트.
 * P0-1 수용기준: 기동 후 GET /health 가 200을 반환한다.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "app", "J-FORGE");
    }
}
