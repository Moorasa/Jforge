package com.jworks.forge.catalog.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jworks.forge.catalog.domain.ModuleType;
import com.jworks.forge.catalog.service.ModuleTypeService;

/**
 * 모듈 카탈로그 조회 REST API (P2-3).
 * PROP_SCHEMA_JSON은 원문 JSON 트리로 응답에 실린다(도메인의 @JsonRawValue).
 */
@RestController
@RequestMapping("/api/module-types")
public class ModuleTypeController {

    private final ModuleTypeService service;

    public ModuleTypeController(ModuleTypeService service) {
        this.service = service;
    }

    /** 활성 목록(SORT_ORDER 순). ?category={CODE} 로 카테고리 필터(옵션). */
    @GetMapping
    public List<ModuleType> list(@RequestParam(name = "category", required = false) String category) {
        return service.list(category);
    }

    /** 단건(PROP_SCHEMA_JSON 포함). 없으면 404(ApiExceptionHandler 경유). */
    @GetMapping("/{code}")
    public ModuleType get(@PathVariable("code") String code) {
        return service.get(code);
    }
}
