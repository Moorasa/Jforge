package com.jworks.forge.dbmeta.dto;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * P11 DB 메타 API의 요청/응답 DTO 모음.
 *
 * <p>🔒 어떤 응답에도 비밀번호(평문·암호문)는 포함하지 않는다.
 */
public final class DbMetaDtos {

    private DbMetaDtos() {
    }

    /**
     * 접속정보 저장/테스트 요청. <b>JDBC URL을 받지 않는다</b> — 조각만 받아 서버가 조립한다(계약 §15).
     * {@code password}가 비어 있으면 <b>저장된 비밀번호를 재사용</b>한다(수정 시 재입력 강요 방지).
     */
    public record ConnectionRequest(
            @NotBlank @Size(max = 255) String host,
            @NotNull @Min(1) @Max(65535) Integer port,
            @NotBlank @Size(max = 64) String database,
            @Size(max = 64) String schema,
            @NotBlank @Size(max = 64) String username,
            @Size(max = 200) String password) {
    }

    /** 저장된 접속정보(비밀번호 제외). {@code configured=false}면 미설정 상태다. */
    public record ConnectionView(
            boolean configured,
            boolean secretAvailable,
            String host,
            Integer port,
            String database,
            String schema,
            String username) {

        /** 미설정 응답. */
        public static ConnectionView none(boolean secretAvailable) {
            return new ConnectionView(false, secretAvailable, null, null, null, null, null);
        }
    }

    /** 연결 테스트 결과. 실패 사유는 드라이버 메시지를 그대로 노출하지 않고 요약만 전달한다. */
    public record ConnectionTestResult(boolean success, String message, String productName) {
    }

    /** 카탈로그 테이블 1건. */
    public record DbTable(String name, String type, String remarks) {
    }

    /** 카탈로그 컬럼 1건. {@code primaryKey}는 화면의 keyColumn 자동 선택에 쓴다. */
    public record DbColumn(String name, String typeName, boolean nullable, boolean primaryKey, String remarks) {
    }

    /** 컬럼 목록 응답. */
    public record DbColumns(String table, List<DbColumn> columns) {
    }
}
