package com.jworks.forge.dbmeta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jworks.forge.dbmeta.TargetDbUrl;
import com.jworks.forge.dbmeta.dto.DbMetaDtos.ConnectionTestResult;
import com.jworks.forge.dbmeta.dto.DbMetaDtos.DbColumn;
import com.jworks.forge.dbmeta.dto.DbMetaDtos.DbTable;

/**
 * 🔒 P11: 스키마 조회가 <b>읽기전용 카탈로그 API</b>만 쓰고, 식별자 게이트가 작동하는지 고정한다.
 * 실제 DB 없이 JDBC 계층을 목킹한다(오프라인 환경 제약 + 순수 단위 검증).
 */
class DbIntrospectionServiceTest {

    private static final TargetDbUrl TARGET = TargetDbUrl.of("localhost", 5432, "app", "public");

    @Test
    void 테이블_목록은_키워드로_걸러진다() throws Exception {
        Connection conn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        ResultSet rs = mock(ResultSet.class);
        when(conn.getMetaData()).thenReturn(meta);
        when(meta.getTables(isNull(), eq("public"), eq("%"), any())).thenReturn(rs);
        when(rs.next()).thenReturn(true, true, true, false);
        when(rs.getString("TABLE_NAME")).thenReturn("TB_USER", "TB_ROLE", "TB_USER_LOG");
        when(rs.getString("TABLE_TYPE")).thenReturn("TABLE", "TABLE", "VIEW");
        when(rs.getString("REMARKS")).thenReturn("사용자", null, null);

        var service = new DbIntrospectionService((url, user, pw) -> conn);
        List<DbTable> all = service.listTables(TARGET, "reader", "pw", null);
        assertEquals(3, all.size());
        assertEquals("TB_USER", all.get(0).name());
        assertEquals("사용자", all.get(0).remarks());

        // 같은 목킹을 다시 쓰기 위해 ResultSet을 새로 준비한다.
        ResultSet rs2 = mock(ResultSet.class);
        when(meta.getTables(isNull(), eq("public"), eq("%"), any())).thenReturn(rs2);
        when(rs2.next()).thenReturn(true, true, false);
        when(rs2.getString("TABLE_NAME")).thenReturn("TB_USER", "TB_ROLE");
        when(rs2.getString("TABLE_TYPE")).thenReturn("TABLE", "TABLE");

        List<DbTable> filtered = service.listTables(TARGET, "reader", "pw", "user");
        assertEquals(1, filtered.size(), "키워드 부분일치(대소문자 무시)만 남아야 한다");
        assertEquals("TB_USER", filtered.get(0).name());
    }

    @Test
    void 컬럼_목록은_PK를_표시한다() throws Exception {
        Connection conn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        ResultSet pkRs = mock(ResultSet.class);
        ResultSet colRs = mock(ResultSet.class);
        when(conn.getMetaData()).thenReturn(meta);
        when(meta.getPrimaryKeys(isNull(), eq("public"), eq("TB_USER"))).thenReturn(pkRs);
        when(pkRs.next()).thenReturn(true, false);
        when(pkRs.getString("COLUMN_NAME")).thenReturn("USER_ID");
        when(meta.getColumns(isNull(), eq("public"), eq("TB_USER"), eq("%"))).thenReturn(colRs);
        when(colRs.next()).thenReturn(true, true, false);
        when(colRs.getString("COLUMN_NAME")).thenReturn("USER_ID", "USER_NM");
        when(colRs.getString("TYPE_NAME")).thenReturn("varchar", "varchar");
        when(colRs.getString("IS_NULLABLE")).thenReturn("NO", "YES");
        when(colRs.getString("REMARKS")).thenReturn("아이디", "이름");

        var service = new DbIntrospectionService((url, user, pw) -> conn);
        List<DbColumn> columns = service.listColumns(TARGET, "reader", "pw", "TB_USER");

        assertEquals(2, columns.size());
        assertTrue(columns.get(0).primaryKey(), "PK 컬럼이 표시돼야 keyColumn 자동선택이 된다");
        assertFalse(columns.get(0).nullable());
        assertFalse(columns.get(1).primaryKey());
        assertTrue(columns.get(1).nullable());
        assertEquals("아이디", columns.get(0).remarks());
    }

    /** 🔒 커넥션은 반드시 읽기전용 세션으로 고정된다. */
    @Test
    void 커넥션은_읽기전용으로_고정된다() throws Exception {
        Connection conn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(conn.getMetaData()).thenReturn(meta);
        when(meta.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(meta.getDatabaseProductVersion()).thenReturn("16.2");

        var service = new DbIntrospectionService((url, user, pw) -> conn);
        ConnectionTestResult result = service.test(TARGET, "reader", "pw");

        assertTrue(result.success());
        verify(conn).setReadOnly(true);
    }

    /** 🔒 읽기전용 설정을 못 하면 연결을 포기한다(안전측 수렴). */
    @Test
    void 읽기전용_설정이_실패하면_연결을_포기한다() throws Exception {
        Connection conn = mock(Connection.class);
        org.mockito.Mockito.doThrow(new SQLException("read-only unsupported"))
                .when(conn).setReadOnly(true);

        var service = new DbIntrospectionService((url, user, pw) -> conn);
        ConnectionTestResult result = service.test(TARGET, "reader", "pw");

        assertFalse(result.success());
        verify(conn).close();
    }

    /** 🔒 식별자 게이트 — 악성 테이블명은 커넥션을 열기도 전에 거부한다. */
    @Test
    void 악성_테이블명은_접속_전에_거부된다() throws Exception {
        Connection conn = mock(Connection.class);
        var service = new DbIntrospectionService((url, user, pw) -> conn);

        assertThrows(IllegalArgumentException.class,
                () -> service.listColumns(TARGET, "reader", "pw", "TB_USER; DROP TABLE X"));
        assertThrows(IllegalArgumentException.class,
                () -> service.listColumns(TARGET, "reader", "pw", "TB USER"));
        assertThrows(IllegalArgumentException.class,
                () -> service.listColumns(TARGET, "reader", "pw", ""));

        verify(conn, never()).getMetaData();
    }

    @Test
    void 연결_실패는_요약_메시지로_돌아온다() {
        var service = new DbIntrospectionService((url, user, pw) -> {
            throw new SQLException("FATAL: password authentication failed for user \"reader\"");
        });

        ConnectionTestResult result = service.test(TARGET, "reader", "bad");

        assertFalse(result.success());
        assertTrue(result.message().contains("password authentication failed"));
    }

    @Test
    void 카탈로그_조회_실패는_DbMetaException으로_승격된다() throws Exception {
        Connection conn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(conn.getMetaData()).thenReturn(meta);
        when(meta.getTables(isNull(), eq("public"), eq("%"), any()))
                .thenThrow(new SQLException("permission denied for schema public"));

        var service = new DbIntrospectionService((url, user, pw) -> conn);

        assertThrows(DbIntrospectionService.DbMetaException.class,
                () -> service.listTables(TARGET, "reader", "pw", null));
    }
}
