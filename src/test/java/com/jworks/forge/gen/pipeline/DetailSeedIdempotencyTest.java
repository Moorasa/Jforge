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
 * 🔒 P5.5a/b (c): V5 시드 정합·멱등 정적 검증 (라이브 DB 없이 파일 파싱, 계약 §9.1).
 *
 * <ul>
 *   <li>멱등: {@code ON CONFLICT (MODULE_TYPE_CODE) DO NOTHING} + {@code SET search_path TO jforge}.</li>
 *   <li>add-only: V5는 DETAIL_BASIC·ASSOCIATE_TABS만 INSERT(V3/V4 모듈 재시드 0).</li>
 *   <li>정합: CATEGORY 'DETAIL', TEMPLATE_KEY {@code module/detailBasic}·{@code module/associateTabs}.</li>
 *   <li>PROP_SCHEMA_JSON({@code ::jsonb})이 유효 JSON이고 field/column 키가 산출 템플릿 배선 키와 정합.</li>
 * </ul>
 */
class DetailSeedIdempotencyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String readSeed() throws Exception {
        Path f = Paths.get("src", "main", "resources", "db", "ddl", "V5__module_type_seed_p55.sql");
        assertTrue(Files.exists(f), "V5 시드 파일 존재: " + f);
        return Files.readString(f, StandardCharsets.UTF_8);
    }

    @Test
    void V5시드는_멱등이고_상세모듈_2종을_정합값으로_시드한다() throws Exception {
        String sql = readSeed();
        assertTrue(sql.contains("SET search_path TO jforge;"), "search_path 설정");
        assertTrue(sql.contains("ON CONFLICT (MODULE_TYPE_CODE) DO NOTHING"), "멱등 절");
        assertTrue(sql.contains("'DETAIL_BASIC'"), "DETAIL_BASIC INSERT");
        assertTrue(sql.contains("'ASSOCIATE_TABS'"), "ASSOCIATE_TABS INSERT");
        assertTrue(sql.contains("'module/detailBasic'"), "TEMPLATE_KEY detailBasic");
        assertTrue(sql.contains("'module/associateTabs'"), "TEMPLATE_KEY associateTabs");
        assertTrue(sql.contains("::jsonb"), "PROP_SCHEMA_JSON jsonb 캐스팅");
        // 카테고리 DETAIL(ArchetypeSlots detailBasic/detailTabs {DETAIL,VIEW}와 정합).
        assertTrue(sql.contains("'DETAIL_BASIC', '기본정보', 'DETAIL'"), "DETAIL_BASIC 카테고리 DETAIL");
        assertTrue(sql.contains("'ASSOCIATE_TABS', '연관 탭', 'DETAIL'"), "ASSOCIATE_TABS 카테고리 DETAIL");
        // add-only: V5는 V3/V4 모듈코드를 재-INSERT하지 않는다.
        for (String prior : List.of("'TABLE_VIEW'", "'SEARCH_FILTER_BAR'", "'TOOLBAR'",
                "'CARD_VIEW'", "'TREE_VIEW'", "'FORM_VIEW'")) {
            assertTrue(!sql.contains(prior), "V5는 선행 모듈 재시드 0(add-only): " + prior);
        }
    }

    @Test
    void V5_PROP_SCHEMA는_유효JSON이고_배선키가_산출템플릿과_정합한다() throws Exception {
        String sql = readSeed();
        Matcher m = Pattern.compile("'(\\{.*?\\})'::jsonb", Pattern.DOTALL).matcher(sql);
        assertTrue(m.find(), "DETAIL_BASIC PROP_SCHEMA 추출");
        JsonNode basic = MAPPER.readTree(m.group(1));
        assertEquals("기본정보", basic.path("title").asText());
        Set<String> basicKeys = fieldKeys(basic);
        for (String k : List.of("fields", "editableYn", "attributeYn", "basicStyleClass")) {
            assertTrue(basicKeys.contains(k), "DETAIL_BASIC 스키마 배선 키: " + k);
        }
        // fields 컬럼 키가 detail.ftl 배선(name/label/type/requiredYn/styleClass)과 정합.
        Set<String> basicCols = columnKeys(basic, "fields");
        for (String c : List.of("name", "label", "type", "requiredYn", "styleClass")) {
            assertTrue(basicCols.contains(c), "DETAIL_BASIC fields 컬럼 키: " + c);
        }

        assertTrue(m.find(), "ASSOCIATE_TABS PROP_SCHEMA 추출");
        JsonNode tabs = MAPPER.readTree(m.group(1));
        assertEquals("연관 탭", tabs.path("title").asText());
        // tabs 컬럼 키가 detail.ftl/detailJs.ftl 배선(label/tabClass/frameId)과 정합.
        Set<String> tabCols = columnKeys(tabs, "tabs");
        for (String c : List.of("label", "tabClass", "frameId")) {
            assertTrue(tabCols.contains(c), "ASSOCIATE_TABS tabs 컬럼 키: " + c);
        }
    }

    private static Set<String> fieldKeys(JsonNode root) {
        Set<String> keys = new HashSet<>();
        for (JsonNode f : root.path("fields")) {
            keys.add(f.path("key").asText());
        }
        return keys;
    }

    private static Set<String> columnKeys(JsonNode root, String fieldKey) {
        Set<String> keys = new HashSet<>();
        for (JsonNode f : root.path("fields")) {
            if (fieldKey.equals(f.path("key").asText())) {
                for (JsonNode c : f.path("columns")) {
                    keys.add(c.path("key").asText());
                }
            }
        }
        return keys;
    }
}
