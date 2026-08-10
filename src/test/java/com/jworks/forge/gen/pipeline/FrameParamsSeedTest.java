package com.jworks.forge.gen.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 🔒 계약 §19/§19.4 — V13 시드(LAYOUT_FRAME 의 frameSrc/frameParams) 정적 검증.
 * 라이브 DB 없이 파일을 파싱한다(V6 시드 테스트와 같은 방식).
 */
class FrameParamsSeedTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String readSeed() throws Exception {
        Path f = Paths.get("src", "main", "resources", "db", "ddl", "V13__layout_frame_src_params.sql");
        assertTrue(Files.exists(f), "V13 시드 파일 존재: " + f);
        return Files.readString(f, StandardCharsets.UTF_8);
    }

    @Test
    void V13은_멱등이고_LAYOUT_FRAME만_건드린다() throws Exception {
        String sql = readSeed();
        assertTrue(sql.contains("SET search_path TO jforge;"), "search_path 설정");
        assertTrue(sql.contains("WHERE MODULE_TYPE_CODE = 'LAYOUT_FRAME'"), "대상 한정");
        // 멱등: 이미 frameSrc 가 있으면 UPDATE 하지 않는다(재실행 안전).
        assertTrue(sql.contains("NOT (PROP_SCHEMA_JSON -> 'fields' @> '[{\"key\":\"frameSrc\"}]'::jsonb)"),
                "멱등 가드 절");
        // add-only: 선행 모듈 시드를 다시 쓰지 않는다.
        for (String prior : List.of("'TABLE_VIEW'", "'CARD_VIEW'", "'PANEL'", "'BUTTON'")) {
            assertFalse(sql.contains(prior), "V13 은 선행 모듈을 건드리지 않는다: " + prior);
        }
        assertFalse(sql.contains("DROP ") || sql.contains("DELETE "), "파괴적 구문 0");
    }

    @Test
    void V13_PROP_SCHEMA는_유효JSON이고_기존_배선키를_보존한다() throws Exception {
        Matcher m = Pattern.compile("'(\\{.*?\\})'::jsonb", Pattern.DOTALL).matcher(readSeed());
        assertTrue(m.find(), "PROP_SCHEMA 추출");
        JsonNode root = MAPPER.readTree(m.group(1));

        Set<String> keys = new HashSet<>();
        for (JsonNode f : root.path("fields")) {
            keys.add(f.path("key").asText());
        }
        // 기존 배선(§10.2)을 잃지 않는다 — 잃으면 속성 패널에서 편집 수단이 사라진다.
        assertTrue(keys.containsAll(List.of("frameId", "title", "paneClass")), "기존 키 보존: " + keys);
        assertTrue(keys.containsAll(List.of("frameSrc", "frameParams")), "신규 키: " + keys);
    }

    @Test
    void frameParams는_이름_값_두칸짜리_반복행이다() throws Exception {
        Matcher m = Pattern.compile("'(\\{.*?\\})'::jsonb", Pattern.DOTALL).matcher(readSeed());
        assertTrue(m.find());
        JsonNode root = MAPPER.readTree(m.group(1));

        JsonNode params = null;
        for (JsonNode f : root.path("fields")) {
            if ("frameParams".equals(f.path("key").asText())) { params = f; }
        }
        assertTrue(params != null, "frameParams 필드 존재");
        // schemaFormRenderer 가 반복행 그리드로 그리는 타입(스키마_PROP_SCHEMA 지원 타입).
        assertEquals("columns", params.path("type").asText());
        assertTrue(params.path("default").isArray(), "기본값은 빈 배열");

        Set<String> cols = new HashSet<>();
        for (JsonNode c : params.path("columns")) {
            cols.add(c.path("key").asText());
            // 셀은 단순타입만 허용된다(중첩 금지).
            assertEquals("text", c.path("type").asText(), "셀 타입은 text");
        }
        assertEquals(Set.of("name", "value"), cols, "이름/값 두 칸");
    }
}
