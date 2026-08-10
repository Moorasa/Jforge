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
 * 🔒 P5-3 (c): V4 시드 TREE_VIEW 정합·멱등 정적 검증 (라이브 DB 없이 파일 파싱).
 *
 * <ul>
 *   <li>멱등: {@code ON CONFLICT (MODULE_TYPE_CODE) DO NOTHING} + {@code SET search_path TO jforge} 포함.</li>
 *   <li>add-only: V4는 CARD_VIEW·TREE_VIEW만 INSERT(V3 무변경은 V3 파일 미수정으로 보장).</li>
 *   <li>정합: TEMPLATE_KEY {@code module/treeView} / PREVIEW_KEY {@code preview/treeView} / SORT_ORDER 5.</li>
 *   <li>PROP_SCHEMA_JSON({@code ::jsonb}) 이 유효 JSON이고, 그 field key가 산출 JS 배선 키와 정합.</li>
 * </ul>
 */
class TreeViewSeedIdempotencyTest {

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
    void V4시드는_멱등이고_TREE_VIEW를_정합값으로_시드한다() throws Exception {
        String sql = readSeed();
        assertTrue(sql.contains("SET search_path TO jforge;"), "search_path 설정");
        assertTrue(sql.contains("ON CONFLICT (MODULE_TYPE_CODE) DO NOTHING"), "멱등 절");
        assertTrue(sql.contains("'TREE_VIEW'"), "TREE_VIEW INSERT");
        assertTrue(sql.contains("'module/treeView'"), "TEMPLATE_KEY 정합(계약 §8.2)");
        assertTrue(sql.contains("'preview/treeView'"), "PREVIEW_KEY 정합");
        assertTrue(sql.contains("::jsonb"), "PROP_SCHEMA_JSON jsonb 캐스팅");
        // SORT_ORDER 5 (CARD_VIEW 4 다음).
        assertTrue(sql.contains("'preview/treeView', 5)"), "SORT_ORDER 5");
        // add-only: V4는 V3 모듈코드를 재-INSERT하지 않는다.
        assertTrue(!sql.contains("'TABLE_VIEW'") && !sql.contains("'SEARCH_FILTER_BAR'")
                && !sql.contains("'TOOLBAR'"), "V4는 V3 모듈을 재시드하지 않음(add-only)");
        // add-only: CARD_VIEW(P5-2) 시드는 무변경(여전히 존재).
        assertTrue(sql.contains("'CARD_VIEW'"), "P5-2 CARD_VIEW 시드 무변경(add-only)");
    }

    @Test
    void V4_TREE_PROP_SCHEMA는_유효JSON이고_field키가_산출JS배선과_정합한다() throws Exception {
        String sql = readSeed();
        // TREE_VIEW INSERT 블록의 JSON 리터럴만 추출('트리 뷰' title 포함하는 리터럴).
        JsonNode root = null;
        Matcher m = Pattern.compile("'(\\{.*?\\})'::jsonb", Pattern.DOTALL).matcher(sql);
        while (m.find()) {
            JsonNode candidate = MAPPER.readTree(m.group(1)); // 유효 JSON이 아니면 여기서 실패
            if ("트리 뷰".equals(candidate.path("title").asText())) {
                root = candidate;
                break;
            }
        }
        assertNotNull(root, "TREE_VIEW PROP_SCHEMA_JSON 리터럴 추출");
        assertEquals("트리 뷰", root.path("title").asText());

        Set<String> keys = new HashSet<>();
        for (JsonNode f : root.path("fields")) {
            keys.add(f.path("key").asText());
        }
        // 산출 JS(treeViewJs.ftl)가 config/rootMapping/selectionType으로 배선하는 키들이 스키마에 존재.
        for (String k : List.of("labelField", "idField", "parentField", "iconField",
                "selectMode", "rootLabel", "rootIconClass", "orderingYn", "searchYn", "treeStyleClass")) {
            assertTrue(keys.contains(k), "PROP_SCHEMA에 배선 키 존재: " + k);
        }
        // labelField 기본값이 JS config 폴백과 정합(비어있지 않은 데이터 키).
        JsonNode labelField = fieldByKey(root, "labelField");
        assertNotNull(labelField);
        assertEquals("name", labelField.path("default").asText(), "labelField 기본값=name");
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
