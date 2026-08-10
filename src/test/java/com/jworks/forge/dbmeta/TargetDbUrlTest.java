package com.jworks.forge.dbmeta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 🔒 P11(계약 §15): JDBC URL은 조각에서만 조립되고, 파라미터 주입이 원천 차단되는지 고정한다. */
class TargetDbUrlTest {

    @Test
    void 정상_조각은_쿼리스트링_없는_URL로_조립된다() {
        TargetDbUrl url = TargetDbUrl.of("db.internal", 5432, "app_db", "public");

        assertEquals("jdbc:postgresql://db.internal:5432/app_db", url.jdbcUrl());
        assertFalse(url.jdbcUrl().contains("?"), "쿼리스트링이 붙으면 안 된다");
        assertFalse(url.jdbcUrl().contains("&"));
    }

    @Test
    void schema가_비면_public으로_수렴한다() {
        assertEquals("public", TargetDbUrl.of("localhost", 5432, "app", null).schema());
        assertEquals("public", TargetDbUrl.of("localhost", 5432, "app", "  ").schema());
    }

    /**
     * 🔒 드라이버 파라미터 주입(socketFactory 계열 RCE의 진입점)은 host 게이트에서 전부 탈락해야 한다.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "db?socketFactory=org.springframework.context.support.ClassPathXmlApplicationContext",
            "db&socketFactoryArg=http://evil/x.xml",
            "db/app",
            "db:5433",
            "db host",
            "-db",
            "db-",
            "",
            "../etc"
    })
    void 악성_host는_거부된다(String host) {
        assertThrows(IllegalArgumentException.class,
                () -> TargetDbUrl.of(host, 5432, "app", "public"));
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, -1, 65536, 99999 })
    void 범위밖_포트는_거부된다(int port) {
        assertThrows(IllegalArgumentException.class,
                () -> TargetDbUrl.of("localhost", port, "app", "public"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "app;DROP DATABASE x", "app db", "app/x", "app?x=1", "" })
    void 악성_database는_거부된다(String database) {
        assertThrows(IllegalArgumentException.class,
                () -> TargetDbUrl.of("localhost", 5432, database, "public"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "public; --", "pub lic", "1public", "pub-lic" })
    void 악성_schema는_거부된다(String schema) {
        assertThrows(IllegalArgumentException.class,
                () -> TargetDbUrl.of("localhost", 5432, "app", schema));
    }

    @ParameterizedTest
    @ValueSource(strings = { "user name", "user;x", "user/x", "" })
    void 악성_username은_거부된다(String username) {
        assertThrows(IllegalArgumentException.class, () -> TargetDbUrl.requireValidUsername(username));
    }

    @Test
    void 정상_username은_통과한다() {
        TargetDbUrl.requireValidUsername("app_reader");
        TargetDbUrl.requireValidUsername("readonly.user-1");
    }
}
