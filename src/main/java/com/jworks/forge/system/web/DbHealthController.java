package com.jworks.forge.system.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jworks.forge.system.mapper.HealthMapper;

/**
 * DB 커넥션 + MyBatis 배선 진단 엔드포인트 (P0-2).
 * GET /health/db → HealthMapper.selectOne() 결과. DB 미도달 시 의도적으로 예외 노출.
 */
@RestController
public class DbHealthController {

    private final HealthMapper healthMapper;

    public DbHealthController(HealthMapper healthMapper) {
        this.healthMapper = healthMapper;
    }

    @GetMapping("/health/db")
    public Map<String, Object> dbHealth() {
        return Map.of("db", healthMapper.selectOne());
    }
}
