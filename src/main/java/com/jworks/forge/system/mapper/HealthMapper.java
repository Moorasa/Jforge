package com.jworks.forge.system.mapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * DB 커넥션 스모크용 매퍼 (P0-2).
 * 기동 시 자동 실행하지 않는다 — 연결이 준비되면 진단 엔드포인트/테스트로 호출해 검증.
 */
@Mapper
public interface HealthMapper {

    /** {@code SELECT 1} — 커넥션/매퍼 배선 확인. */
    int selectOne();
}
