package com.jworks.forge.gen.hist;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * TB_FRG_GEN_HIST 매퍼 (인터페이스 + XML). 모든 조회는 컬럼 명시(SELECT * 금지).
 * FILE_LIST_JSON은 INSERT 시 {@code ::jsonb} 캐스팅, 조회 시 {@code ::text}로 원문 왕복.
 */
@Mapper
public interface GenHistMapper {

    /**
     * 생성 이력 1행 등록. SCREEN_ID / FILE_LIST_JSON(::jsonb) / RESULT_CODE 만 INSERT하고
     * GEN_AT은 DB DEFAULT(now())에 맡긴다. 생성된 GEN_HIST_ID를 파라미터 객체에 채운다.
     */
    int insert(GenHist hist);

    /**
     * 화면별 생성 이력(최신순: GEN_AT DESC, tie-break GEN_HIST_ID DESC).
     * FILE_LIST_JSON은 {@code ::text}로 원문 문자열 왕복.
     */
    List<GenHist> selectByScreen(@Param("screenId") Long screenId);
}
