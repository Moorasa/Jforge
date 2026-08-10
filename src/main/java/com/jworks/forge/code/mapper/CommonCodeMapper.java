package com.jworks.forge.code.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.jworks.forge.code.domain.CommonCode;

/** TB_FRG_COMMON_CODE 조회 매퍼. */
@Mapper
public interface CommonCodeMapper {

    /** 그룹 내 사용중 코드 목록(SORT_ORDER 순). */
    List<CommonCode> selectByGroup(@Param("grpCode") String grpCode);
}
