package com.jworks.forge.catalog.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.jworks.forge.catalog.domain.ModuleType;

/**
 * TB_FRG_MODULE_TYPE 매퍼 (인터페이스 + XML). 모든 조회는 컬럼 명시(SELECT * 금지).
 * PROP_SCHEMA_JSON(JSONB)은 ::text 로 뽑아 원문 문자열로 전달한다.
 */
@Mapper
public interface ModuleTypeMapper {

    /** 사용중(USE_YN='Y') 모듈타입 목록. SORT_ORDER 오름차순. */
    List<ModuleType> selectActiveList();

    /** 카테고리별 사용중 목록. SORT_ORDER 오름차순. */
    List<ModuleType> selectActiveByCategory(@Param("categoryCode") String categoryCode);

    /** 단건 조회(사용중만). PROP_SCHEMA_JSON 포함. 없으면 null. */
    ModuleType selectActiveByCode(@Param("moduleTypeCode") String moduleTypeCode);
}
