package com.jworks.forge.project.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.jworks.forge.project.domain.ForgeProject;

/**
 * TB_FRG_PROJECT 매퍼 (인터페이스 + XML). 모든 조회는 컬럼 명시(SELECT * 금지).
 */
@Mapper
public interface ForgeProjectMapper {

    /** 사용중(USE_YN='Y') 프로젝트 목록. */
    List<ForgeProject> selectActiveList();

    /** 단건 조회(논리삭제 포함 없이 사용중만). */
    ForgeProject selectActiveById(@Param("projectId") Long projectId);

    /** 신규 등록. 생성된 PROJECT_ID를 파라미터 객체에 채운다. */
    int insert(ForgeProject project);

    /** 수정. 영향 행 수 반환. */
    int update(ForgeProject project);

    /** 논리삭제(USE_YN='N'). 영향 행 수 반환. */
    int softDelete(@Param("projectId") Long projectId);
}
