package com.jworks.forge.dbmeta.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.jworks.forge.dbmeta.domain.ProjectDbConnection;

/** TB_FRG_PROJECT_DB 접근 (P11). SQL은 XML에 있다(컬럼 명시·#{} 바인딩). */
@Mapper
public interface ProjectDbConnectionMapper {

    ProjectDbConnection selectByProject(@Param("projectId") Long projectId);

    /** 존재하면 갱신, 없으면 삽입(PK 충돌 upsert). */
    int upsert(ProjectDbConnection connection);

    int deleteByProject(@Param("projectId") Long projectId);
}
