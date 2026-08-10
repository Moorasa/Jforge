package com.jworks.forge.dbmeta.service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.jworks.forge.dbmeta.TargetDbUrl;
import com.jworks.forge.dbmeta.dto.DbMetaDtos.ConnectionTestResult;
import com.jworks.forge.dbmeta.dto.DbMetaDtos.DbColumn;
import com.jworks.forge.dbmeta.dto.DbMetaDtos.DbTable;

/**
 * 🔒 타겟 DB 스키마 조회 (P11, 계약 §15). <b>읽기전용 · 카탈로그 한정.</b>
 *
 * <p><b>왜 안전한가</b>:
 * <ul>
 *   <li><b>SQL을 한 줄도 작성하지 않는다.</b> 조회는 전부 JDBC {@link DatabaseMetaData} API
 *       ({@code getTables}/{@code getColumns}/{@code getPrimaryKeys})로 수행하며, 식별자는
 *       문자열 결합이 아니라 <b>드라이버에 인자로</b> 전달된다 → SQL 인젝션 표면 자체가 없다.</li>
 *   <li>접속은 {@link TargetDbUrl}이 조립한 URL만 사용한다(쿼리 파라미터 0).</li>
 *   <li>커넥션은 열자마자 {@code setReadOnly(true)}로 세션을 읽기전용으로 고정하고,
 *       로그인 타임아웃을 둔다. DDL/DML 실행 경로는 이 클래스에 존재하지 않는다.</li>
 *   <li>테이블명은 호출 전 식별자 화이트리스트로 한 번 더 거른다(방어 심층화).</li>
 * </ul>
 */
@Service
public class DbIntrospectionService {

    private static final Logger log = LoggerFactory.getLogger(DbIntrospectionService.class);

    /** 카탈로그 조회에 넘길 식별자 형태(계약 §14.1과 동형). */
    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_$]{0,62}$");
    /** 조회 대상 오브젝트 종류(뷰 포함 — 목록 화면의 소스로 흔히 쓰인다). */
    private static final String[] TABLE_TYPES = { "TABLE", "VIEW" };
    /** 응답 폭주 방지 상한. */
    private static final int MAX_TABLES = 2000;
    private static final int MAX_COLUMNS = 500;
    /** 접속 시도 제한(초). */
    private static final int LOGIN_TIMEOUT_SEC = 5;

    /** 테스트에서 실제 DB 없이 검증할 수 있도록 커넥션 획득을 주입 가능하게 둔다. */
    @FunctionalInterface
    public interface ConnectionFactory {
        Connection open(String jdbcUrl, String username, String password) throws SQLException;
    }

    private final ConnectionFactory connectionFactory;

    public DbIntrospectionService() {
        this(DbIntrospectionService::openWithDriverManager);
    }

    public DbIntrospectionService(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /** 연결 가능 여부만 확인한다(카탈로그 조회 없음). */
    public ConnectionTestResult test(TargetDbUrl target, String username, String password) {
        try (Connection conn = open(target, username, password)) {
            String product = conn.getMetaData().getDatabaseProductName()
                    + " " + conn.getMetaData().getDatabaseProductVersion();
            return new ConnectionTestResult(true, "연결에 성공했습니다.", product);
        } catch (SQLException e) {
            log.warn("[DbMeta] 연결 실패 — host={} db={} : {}",
                    target.host(), target.database(), e.getClass().getSimpleName());
            return new ConnectionTestResult(false, summarize(e), null);
        }
    }

    /** 스키마의 테이블/뷰 목록. {@code keyword}는 서버 메모리에서 대소문자 무시 부분일치로 거른다. */
    public List<DbTable> listTables(TargetDbUrl target, String username, String password, String keyword) {
        String needle = (keyword == null) ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<DbTable> tables = new ArrayList<>();
        try (Connection conn = open(target, username, password);
             ResultSet rs = conn.getMetaData().getTables(null, target.schema(), "%", TABLE_TYPES)) {
            while (rs.next() && tables.size() < MAX_TABLES) {
                String name = rs.getString("TABLE_NAME");
                if (name == null) {
                    continue;
                }
                if (!needle.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(needle)) {
                    continue;
                }
                tables.add(new DbTable(name, rs.getString("TABLE_TYPE"), rs.getString("REMARKS")));
            }
        } catch (SQLException e) {
            throw new DbMetaException(summarize(e));
        }
        return tables;
    }

    /** 테이블의 컬럼 목록(+PK 표시). 순서는 드라이버가 주는 서수 순서를 따른다. */
    public List<DbColumn> listColumns(TargetDbUrl target, String username, String password, String table) {
        requireIdentifier(table, "테이블명");
        List<DbColumn> columns = new ArrayList<>();
        try (Connection conn = open(target, username, password)) {
            Set<String> pk = primaryKeys(conn, target.schema(), table);
            try (ResultSet rs = conn.getMetaData().getColumns(null, target.schema(), table, "%")) {
                while (rs.next() && columns.size() < MAX_COLUMNS) {
                    String name = rs.getString("COLUMN_NAME");
                    if (name == null) {
                        continue;
                    }
                    columns.add(new DbColumn(
                            name,
                            rs.getString("TYPE_NAME"),
                            "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")),
                            pk.contains(name),
                            rs.getString("REMARKS")));
                }
            }
        } catch (SQLException e) {
            throw new DbMetaException(summarize(e));
        }
        return columns;
    }

    /** PK 컬럼 집합. 드라이버가 PK 조회를 지원하지 않아도 조회 전체를 실패시키지 않는다. */
    private Set<String> primaryKeys(Connection conn, String schema, String table) {
        Set<String> keys = new HashSet<>();
        try (ResultSet rs = conn.getMetaData().getPrimaryKeys(null, schema, table)) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                if (name != null) {
                    keys.add(name);
                }
            }
        } catch (SQLException e) {
            log.warn("[DbMeta] PK 조회 실패(계속) — table={} : {}", table, e.getClass().getSimpleName());
        }
        return keys;
    }

    /** 🔒 읽기전용 세션으로 커넥션을 연다. */
    private Connection open(TargetDbUrl target, String username, String password) throws SQLException {
        TargetDbUrl.requireValidUsername(username);
        Connection conn = connectionFactory.open(target.jdbcUrl(), username, password);
        try {
            conn.setReadOnly(true);
        } catch (SQLException e) {
            // 읽기전용 설정을 지원하지 않는 드라이버라면 연결 자체를 포기한다(안전측).
            conn.close();
            throw e;
        }
        return conn;
    }

    private static Connection openWithDriverManager(String url, String user, String password)
            throws SQLException {
        DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SEC);
        return DriverManager.getConnection(url, user, password);
    }

    private static void requireIdentifier(String value, String label) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " 형식이 올바르지 않습니다.");
        }
    }

    /** 드라이버 메시지를 길이 제한해 전달한다(사용자 본인의 DB 진단에 필요한 최소 정보). */
    private static String summarize(SQLException e) {
        String message = (e.getMessage() == null) ? e.getClass().getSimpleName() : e.getMessage();
        return message.length() > 300 ? message.substring(0, 300) + "…" : message;
    }

    /** 카탈로그 조회 실패(호출측이 502/400으로 변환). */
    public static class DbMetaException extends RuntimeException {
        public DbMetaException(String message) {
            super(message);
        }
    }
}
