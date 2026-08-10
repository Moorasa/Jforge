package com.jworks.forge.catalog.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.jworks.forge.catalog.domain.ModuleType;
import com.jworks.forge.catalog.mapper.ModuleTypeMapper;
import com.jworks.forge.common.web.NotFoundException;

/**
 * 모듈 카탈로그 조회 비즈니스 로직 (P2-3). 조회 전용(USE_YN='Y').
 * PROP_SCHEMA_JSON은 매퍼가 뽑은 원문 그대로 통과시킨다(가공 금지 — §2.1 신뢰경계).
 */
@Service
public class ModuleTypeService {

    private final ModuleTypeMapper mapper;

    public ModuleTypeService(ModuleTypeMapper mapper) {
        this.mapper = mapper;
    }

    /** 활성 목록(SORT_ORDER 순). category가 주어지면 해당 카테고리로 필터. */
    public List<ModuleType> list(String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()) {
            return mapper.selectActiveList();
        }
        return mapper.selectActiveByCategory(categoryCode);
    }

    /** 단건 조회. 없으면 404. */
    public ModuleType get(String moduleTypeCode) {
        ModuleType mt = mapper.selectActiveByCode(moduleTypeCode);
        if (mt == null) {
            throw new NotFoundException("module type not found: " + moduleTypeCode);
        }
        return mt;
    }
}
