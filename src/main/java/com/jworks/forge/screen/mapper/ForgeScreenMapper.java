package com.jworks.forge.screen.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.jworks.forge.screen.domain.ForgeScreen;

/**
 * TB_FRG_SCREEN 매퍼 (인터페이스 + XML). 모든 조회는 컬럼 명시(SELECT * 금지).
 * DEFINITION_JSON은 단건 조회에서만 ::text 로 원문 왕복하고, 목록에서는 제외해 경량화한다.
 */
@Mapper
public interface ForgeScreenMapper {

    /** 프로젝트별 화면 목록(메타만, DEFINITION_JSON 본문 제외). 폐기(DELETED) 제외. */
    List<ForgeScreen> selectListByProject(@Param("projectId") Long projectId);

    /** 단건 조회(DEFINITION_JSON 원문 포함). 폐기(DELETED) 제외. */
    ForgeScreen selectById(@Param("screenId") Long screenId);

    /** 신규 등록. 생성된 SCREEN_ID를 파라미터 객체에 채운다. DEFINITION_JSON은 ::jsonb 캐스팅. */
    int insert(ForgeScreen screen);

    /** 메타 수정(name/archetype/role/status). DEFINITION_JSON 본문은 갱신하지 않는다. */
    int updateMeta(ForgeScreen screen);

    /** 상태 삭제(STATUS_CODE='DELETED'). 물리 삭제 금지. 영향 행 수 반환. */
    int softDelete(@Param("screenId") Long screenId);

    /**
     * DEFINITION_JSON 본문 갱신(P3-5b/P3-6b 저장 경로). {@code ::jsonb} 캐스팅 + {@code #{}} 바인딩만.
     * 폐기(DELETED) 화면은 갱신하지 않는다. 영향 행 수 반환(0이면 미존재).
     */
    int updateDefinition(@Param("screenId") Long screenId,
                         @Param("definitionJson") String definitionJson);
}
