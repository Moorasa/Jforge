package com.jworks.forge.gen.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * 🔒 P5-5c (c): V6 시드 정합·멱등 정적 검증 (라이브 DB 없이 파일 파싱, 계약 §10.2).
 */
class LayoutFrameSeedIdempotencyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String readSeed() throws Exception {
        Path f = Paths.get("src", "main", "resources", "db", "ddl", "V6__module_type_seed_p55c.sql");
        assertTrue(Files.exists(f), "V6 시드 파일 존재: " + f);
        return Files.readString(f, StandardCharsets.UTF_8);
    }

    @Test
    void V6시드는_멱등이고_LAYOUT_FRAME을_정합값으로_시드한다() throws Exception {
        String sql = readSeed();
        assertTrue(sql.contains("SET search_path TO jforge;"), "search_path 설정");
        assertTrue(sql.contains("ON CONFLICT (MODULE_TYPE_CODE) DO NOTHING"), "멱등 절");
        assertTrue(sql.contains("'LAYOUT_FRAME', '레이아웃 프레임', 'VIEW'"), "LAYOUT_FRAME 카테고리 VIEW");
        assertTrue(sql.contains("'module/layoutFrame'"), "TEMPLATE_KEY layoutFrame");
        assertTrue(sql.contains("::jsonb"), "PROP_SCHEMA_JSON jsonb 캐스팅");
        // add-only: V6는 선행 모듈코드를 재-INSERT하지 않는다.
        for (String prior : List.of("'TABLE_VIEW'", "'CARD_VIEW'", "'TREE_VIEW'", "'FORM_VIEW'",
                "'DETAIL_BASIC'", "'ASSOCIATE_TABS'", "'TOOLBAR'")) {
            assertTrue(!sql.contains(prior), "V6는 선행 모듈 재시드 0(add-only): " + prior);
        }
    }

    @Test
    void V6_PROP_SCHEMA는_유효JSON이고_배선키가_shell템플릿과_정합한다() throws Exception {
        String sql = readSeed();
        Matcher m = Pattern.compile("'(\\{.*?\\})'::jsonb", Pattern.DOTALL).matcher(sql);
        assertTrue(m.find(), "LAYOUT_FRAME PROP_SCHEMA 추출");
        JsonNode root = MAPPER.readTree(m.group(1));
        assertEquals("레이아웃 프레임", root.path("title").asText());
        Set<String> keys = new HashSet<>();
        for (JsonNode f : root.path("fields")) {
            keys.add(f.path("key").asText());
        }
        // shell.ftl 배선(frameId=iframe id, title=iframe title, paneClass=cssToken)과 정합.
        for (String k : List.of("frameId", "title", "paneClass")) {
            assertTrue(keys.contains(k), "LAYOUT_FRAME 스키마 배선 키: " + k);
        }
    }
}
