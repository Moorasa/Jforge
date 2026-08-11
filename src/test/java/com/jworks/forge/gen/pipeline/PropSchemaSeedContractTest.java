package com.jworks.forge.gen.pipeline;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 🔒 시드에 들어가는 PROP_SCHEMA_JSON 이 규약(docs/스키마_PROP_SCHEMA.md)을 지키는지
 * <b>전 시드 파일을 훑어</b> 정적 검증한다. 라이브 DB 없이 파일만 읽는다.
 *
 * <p><b>왜 필요했나</b>: V10 이 select 의 {@code options} 를 <b>문자열</b>로 넣었다
 * ({@code "options":"primary:기본,..."}). 규약과 폼 렌더러는 <b>배열</b>
 * ({@code [{value,label}]})만 읽으므로, 속성패널의 "모양"·"강조"·"입력 유형"·"맞춤"
 * 드롭다운이 <b>항목 0개로 조용히</b> 떴다. 아무 에러도 없어서 오래 눈에 안 띄었다.
 * 문자열 형식은 <b>반복행 셀</b>의 값에서 쓰는 형태인데(§159) 그걸 최상위 select 에
 * 옮긴 것이 원인이다 — 헷갈리기 쉬운 지점이라 그물을 친다.
 */
class PropSchemaSeedContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * {@code '{"...":...}'::jsonb} 리터럴을 뽑는다(시드의 PROP_SCHEMA_JSON 표기).
     *
     * <p>여는 중괄호 뒤에 <b>따옴표</b>가 오는 것만 받는다 — 그러지 않으면
     * {@code #> '{fields,1,options}'} 같은 <b>jsonb 경로 리터럴</b>까지 걸려서, 거기서부터
     * 다음 스키마 끝까지를 통째로 집어삼킨다(파싱 실패).
     */
    private static final Pattern JSONB_LITERAL =
            Pattern.compile("'(\\{\\s*\".*?\\})'::jsonb", Pattern.DOTALL);

    private record SchemaRef(String file, int index, JsonNode root) { }

    /** {@code V12__...sql} → 12. 마이그레이션 적용 순서다(사전순이 아니다 — V10 &lt; V2 가 된다). */
    private static int versionOf(Path f) {
        Matcher m = Pattern.compile("^V(\\d+)__").matcher(f.getFileName().toString());
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }

    /**
     * 마이그레이션을 <b>순서대로 다 돌린 뒤의 최종 스키마</b>를 돌려준다.
     *
     * <p>파일 텍스트를 그냥 다 모으면 <b>나중에 고친 것</b>까지 위반으로 잡힌다 —
     * 예를 들어 V10 의 잘못된 options 는 V14 가 UPDATE 로 교정하므로 DB 최종 상태는 정상이다.
     * 실제 상태를 보려면 같은 모듈의 스키마는 <b>버전이 큰 파일 것이 이긴다</b>.
     * 키는 스키마 {@code title}(= 모듈 표시명)로 잡는다.
     */
    private static List<SchemaRef> effectiveSchemas() throws IOException {
        java.util.LinkedHashMap<String, SchemaRef> byTitle = new java.util.LinkedHashMap<>();
        Path ddl = Paths.get("src", "main", "resources", "db", "ddl");
        assertTrue(Files.isDirectory(ddl), "DDL 디렉터리: " + ddl);

        List<Path> ordered;
        try (Stream<Path> files = Files.list(ddl)) {
            ordered = files.filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted(java.util.Comparator.comparingInt(PropSchemaSeedContractTest::versionOf))
                    .toList();
        }

        for (Path f : ordered) {
            String sql = Files.readString(f, StandardCharsets.UTF_8);
            Matcher m = JSONB_LITERAL.matcher(sql);
            int i = 0;
            while (m.find()) {
                JsonNode root;
                try {
                    root = MAPPER.readTree(m.group(1));
                } catch (Exception parseFail) {
                    throw new AssertionError(
                            f.getFileName() + " 의 " + i + "번째 jsonb 리터럴이 유효 JSON 이 아니다: "
                                    + parseFail.getMessage());
                }
                // PROP_SCHEMA 만 본다(다른 jsonb 컬럼이 섞여도 안전하게 건너뛴다).
                if (root.has("fields")) {
                    String title = root.path("title").asText("(제목없음)#" + f.getFileName() + i);
                    byTitle.put(title, new SchemaRef(f.getFileName().toString(), i, root));
                }
                i++;
            }
        }
        assertFalse(byTitle.isEmpty(), "시드에서 PROP_SCHEMA 를 하나도 못 찾았다");
        return new ArrayList<>(byTitle.values());
    }

    @Test
    void 모든_select_필드의_options는_배열이고_비어있지_않다() throws IOException {
        List<String> violations = new ArrayList<>();
        int selectCount = 0;

        for (SchemaRef ref : effectiveSchemas()) {
            for (JsonNode field : ref.root().path("fields")) {
                if (!"select".equals(field.path("type").asText())) { continue; }
                selectCount++;
                String where = ref.file() + " [" + ref.root().path("title").asText()
                        + "] 필드 '" + field.path("key").asText() + "'";
                JsonNode options = field.get("options");

                if (options == null) {
                    violations.add(where + ": options 누락");
                } else if (!options.isArray()) {
                    violations.add(where + ": options 가 배열이 아니다(" + options.getNodeType()
                            + ") — 반복행 셀의 문자열 형식을 최상위 select 에 쓴 것 아닌가? 값=" + options);
                } else if (options.isEmpty()) {
                    violations.add(where + ": options 가 빈 배열 — 드롭다운이 항목 0개로 뜬다");
                } else {
                    for (JsonNode o : options) {
                        if (!o.hasNonNull("value") || !o.hasNonNull("label")) {
                            violations.add(where + ": options 원소에 value/label 누락 — " + o);
                        }
                    }
                }
            }
        }

        assertTrue(selectCount > 0, "검사한 select 필드가 0개 — 추출 정규식이 깨졌다");
        assertTrue(violations.isEmpty(),
                "PROP_SCHEMA select options 규약 위반 " + violations.size() + "건:\n  "
                        + String.join("\n  ", violations));
    }

    /** default 가 있으면 options 의 value 중 하나여야 한다 — 아니면 아무것도 선택되지 않은 채 뜬다. */
    @Test
    void select_기본값은_options_중_하나다() throws IOException {
        List<String> violations = new ArrayList<>();
        for (SchemaRef ref : effectiveSchemas()) {
            for (JsonNode field : ref.root().path("fields")) {
                if (!"select".equals(field.path("type").asText())) { continue; }
                JsonNode def = field.get("default");
                JsonNode options = field.get("options");
                if (def == null || def.isNull() || options == null || !options.isArray()) { continue; }
                boolean hit = false;
                for (JsonNode o : options) {
                    if (def.asText().equals(o.path("value").asText())) { hit = true; }
                }
                if (!hit) {
                    violations.add(ref.file() + " 필드 '" + field.path("key").asText()
                            + "' 의 default(" + def.asText() + ")가 options 에 없다");
                }
            }
        }
        assertTrue(violations.isEmpty(), String.join("\n  ", violations));
    }

    /** columns(반복행)의 셀 타입은 단순타입만 — 중첩 select 는 렌더러가 지원하지 않는다. */
    @Test
    void 반복행_셀은_단순타입만_쓴다() throws IOException {
        List<String> allowed = List.of("text", "number", "boolean", "select");
        List<String> violations = new ArrayList<>();
        for (SchemaRef ref : effectiveSchemas()) {
            for (JsonNode field : ref.root().path("fields")) {
                if (!"columns".equals(field.path("type").asText())) { continue; }
                for (JsonNode c : field.path("columns")) {
                    String t = c.path("type").asText();
                    if (!allowed.contains(t)) {
                        violations.add(ref.file() + " 필드 '" + field.path("key").asText()
                                + "' 의 셀 '" + c.path("key").asText() + "' 타입=" + t);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), String.join("\n  ", violations));
    }
}
