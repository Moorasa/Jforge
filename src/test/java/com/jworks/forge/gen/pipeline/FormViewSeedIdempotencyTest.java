package com.jworks.forge.gen.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * 🔒 P5-4 (c): V4 시드 FORM_VIEW 정합·멱등 정적 검증 (라이브 DB 없이 파일 파싱).
 *
 * <ul>
 *   <li>멱등: {@code ON CONFLICT (MODULE_TYPE_CODE) DO NOTHING} + {@code SET search_path TO jforge} 포함.</li>
 *   <li>add-only: V4는 CARD_VIEW·TREE_VIEW·FORM_VIEW만 INSERT(V3 무변경은 V3 파일 미수정으로 보장).</li>
 *   <li>정합: TEMPLATE_KEY {@code module/formView} / PREVIEW_KEY {@code preview/formView} / SORT_ORDER 6.</li>
 *   <li>PROP_SCHEMA_JSON({@code ::jsonb})이 유효 JSON이고, field key가 산출 JS 배선 키와 정합.</li>
 * </ul>
 */
class FormViewSeedIdempotencyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Path seedFile() {
        return Paths.get("src", "main", "resources", "db", "ddl", "V4__module_type_seed_p5.sql");
    }

    private static String readSeed() throws Exception {
        Path f = seedFile();
        assertTrue(Files.exists(f), "V4 시드 파일 존재: " + f);
        return Files.readString(f, StandardCharsets.UTF_8);
    }

    @Test
    void V4시드는_멱등이고_FORM_VIEW를_정합값으로_시드한다() throws Exception {
        String sql = readSeed();
        assertTrue(sql.contains("SET search_path TO jforge;"), "search_path 설정");
        assertTrue(sql.contains("ON CONFLICT (MODULE_TYPE_CODE) DO NOTHING"), "멱등 절");
        assertTrue(sql.contains("'FORM_VIEW'"), "FORM_VIEW INSERT");
        assertTrue(sql.contains("'module/formView'"), "TEMPLATE_KEY 정합(계약 §8.2)");
        assertTrue(sql.contains("'preview/formView'"), "PREVIEW_KEY 정합");
        assertTrue(sql.contains("::jsonb"), "PROP_SCHEMA_JSON jsonb 캐스팅");
        // SORT_ORDER 6 (TREE_VIEW 5 다음).
        assertTrue(sql.contains("'preview/formView', 6)"), "SORT_ORDER 6");
        // add-only: V4는 V3 모듈코드를 재-INSERT하지 않는다.
        assertTrue(!sql.contains("'TABLE_VIEW'") && !sql.contains("'SEARCH_FILTER_BAR'")
                && !sql.contains("'TOOLBAR'"), "V4는 V3 모듈을 재시드하지 않음(add-only)");
        // add-only: CARD_VIEW(P5-2)·TREE_VIEW(P5-3) 시드 무변경(여전히 존재).
        assertTrue(sql.contains("'CARD_VIEW'"), "P5-2 CARD_VIEW 시드 무변경(add-only)");
        assertTrue(sql.contains("'TREE_VIEW'"), "P5-3 TREE_VIEW 시드 무변경(add-only)");
    }

    @Test
    void V4_FORM_PROP_SCHEMA는_유효JSON이고_field키가_산출JS배선과_정합한다() throws Exception {
        String sql = readSeed();
        // FORM_VIEW INSERT 블록의 JSON 리터럴만 추출('폼 뷰' title 포함하는 리터럴).
        JsonNode root = null;
        Matcher m = Pattern.compile("'(\\{.*?\\})'::jsonb", Pattern.DOTALL).matcher(sql);
        while (m.find()) {
            JsonNode candidate = MAPPER.readTree(m.group(1)); // 유효 JSON이 아니면 여기서 실패
            if ("폼 뷰".equals(candidate.path("title").asText())) {
                root = candidate;
                break;
            }
        }
        assertNotNull(root, "FORM_VIEW PROP_SCHEMA_JSON 리터럴 추출");
        assertEquals("폼 뷰", root.path("title").asText());

        Set<String> keys = new HashSet<>();
        for (JsonNode f : root.path("fields")) {
            keys.add(f.path("key").asText());
        }
        // 산출 JS(formViewJs.ftl)/JSP(formView.ftl)가 배선하는 최상위 키들이 스키마에 존재.
        for (String k : List.of("selectionType", "fields", "formStyleClass")) {
            assertTrue(keys.contains(k), "PROP_SCHEMA에 배선 키 존재: " + k);
        }
        // selectionType 기본값이 commonListFormView.js 38행 기본(checkbox)과 정합.
        JsonNode selectionType = fieldByKey(root, "selectionType");
        assertNotNull(selectionType);
        assertEquals("checkbox", selectionType.path("default").asText(), "selectionType 기본값=checkbox");

        // fields 하위 컬럼 스키마에 name/label/type/requiredYn/styleClass 존재(§8.4 이스케이프 대상).
        JsonNode fields = fieldByKey(root, "fields");
        assertNotNull(fields);
        Set<String> colKeys = new HashSet<>();
        for (JsonNode c : fields.path("columns")) {
            colKeys.add(c.path("key").asText());
        }
        for (String k : List.of("name", "label", "type", "requiredYn", "styleClass")) {
            assertTrue(colKeys.contains(k), "fields 컬럼 스키마에 키 존재: " + k);
        }
    }

    private static JsonNode fieldByKey(JsonNode root, String key) {
        for (JsonNode f : root.path("fields")) {
            if (key.equals(f.path("key").asText())) {
                return f;
            }
        }
        return null;
    }
}
