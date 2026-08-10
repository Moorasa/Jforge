package com.jworks.forge.dbmeta;

import java.util.regex.Pattern;

/**
 * 🔒 타겟 DB 접속 좌표 + JDBC URL 조립기 (P11, 계약 §15). <b>순수 함수 · 불변.</b>
 *
 * <p><b>왜 URL을 사용자에게 입력받지 않는가(핵심 보안 결정)</b>: JDBC URL은 단순 주소가 아니라
 * <b>드라이버 동작을 바꾸는 파라미터</b>를 실을 수 있다. PostgreSQL 드라이버의 {@code socketFactory}·
 * {@code socketFactoryArg} 계열 파라미터는 임의 클래스 로딩으로 이어지는 알려진 공격 표면이다.
 * 따라서 J-FORGE는 URL 문자열을 <b>저장하지도, 입력받지도 않는다</b>. host/port/database 조각만
 * 화이트리스트로 받고 이 클래스가 조립하며, <b>쿼리스트링은 어떤 경우에도 붙지 않는다</b>.
 *
 * <p>게이트를 통과하지 못한 값은 {@link IllegalArgumentException}으로 즉시 거부한다(부분 허용 없음).
 */
public record TargetDbUrl(String host, int port, String database, String schema) {

    /** 호스트/IP — 영숫자로 시작·끝나며 점·하이픈·밑줄만 허용. {@code /} {@code :} {@code ?} {@code &} 전부 불가. */
    private static final Pattern HOST =
            Pattern.compile("^[A-Za-z0-9]([A-Za-z0-9._-]{0,253}[A-Za-z0-9])?$");
    /** DB 이름 — 하이픈 허용(실무상 흔함), 경로/파라미터 문자 불가. */
    private static final Pattern DATABASE = Pattern.compile("^[A-Za-z0-9_][A-Za-z0-9_-]{0,62}$");
    /** 스키마 — DB 식별자 형태(계약 §14.1과 동형). */
    private static final Pattern SCHEMA = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,62}$");

    /** 접속 사용자명 — 식별자 형태만(URL에는 들어가지 않지만 같은 수준으로 방어). */
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_.-]{0,62}$");

    public static final String DEFAULT_SCHEMA = "public";

    /**
     * 게이트를 통과한 좌표만 생성한다.
     *
     * @throws IllegalArgumentException 어느 조각이든 화이트리스트 위반 시
     */
    public static TargetDbUrl of(String host, Integer port, String database, String schema) {
        require(host != null && HOST.matcher(host).matches(),
                "host는 영숫자·점·하이픈만 허용합니다(경로·포트·파라미터 문자 불가).");
        require(port != null && port >= 1 && port <= 65535, "port는 1~65535여야 합니다.");
        require(database != null && DATABASE.matcher(database).matches(),
                "database 이름 형식이 올바르지 않습니다.");
        String resolvedSchema = (schema == null || schema.isBlank()) ? DEFAULT_SCHEMA : schema;
        require(SCHEMA.matcher(resolvedSchema).matches(), "schema 이름 형식이 올바르지 않습니다.");
        return new TargetDbUrl(host, port, database, resolvedSchema);
    }

    /** 접속 사용자명 게이트(호출측이 저장/사용 전에 검증). */
    public static void requireValidUsername(String username) {
        require(username != null && USERNAME.matcher(username).matches(),
                "username 형식이 올바르지 않습니다.");
    }

    /**
     * PostgreSQL JDBC URL. <b>쿼리스트링을 붙이지 않는다</b>(파라미터 주입 차단).
     * 스키마는 URL이 아니라 카탈로그 조회 인자로 전달한다.
     */
    public String jdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
