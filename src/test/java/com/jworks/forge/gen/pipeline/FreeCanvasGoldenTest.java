package com.jworks.forge.gen.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jworks.forge.gen.context.TemplateContextBuilder;
import com.jworks.forge.gen.safety.PathSafetyService;
import com.jworks.forge.gen.template.CodeGenTemplateConfig;
import com.jworks.forge.gen.template.TemplateRenderer;
import com.jworks.forge.project.domain.ForgeProject;
import com.jworks.forge.project.service.ForgeProjectService;
import com.jworks.forge.screen.domain.ForgeScreen;
import com.jworks.forge.screen.service.ForgeScreenService;

/**
 * P13-6 FREE_CANVAS 골든 스냅샷 회귀망 (계약 §17).
 *
 * <p><b>왜 필요했나</b>: 아키타입 중 FREE_CANVAS 만 골든이 없어 동작 테스트에만 의존했다.
 * 같은 공백이 DASHBOARD 에 있었고, 그 탓에 숫자가 포매터 객체로 찍히는 결함(§17.9)을
 * 아무도 못 잡고 있었다. 캔버스를 더 확장하기 전에 그물을 먼저 친다.
 *
 * <h2>고정 시나리오 5종</h2>
 * <ul>
 *   <li>{@code freeCanvas_empty} — 인스턴스 0개(빈 시트). 골격만 산출되는지.</li>
 *   <li>{@code freeCanvas_controls} — 원자 컨트롤 3종(BUTTON/LABEL/TEXT_INPUT) 좌표 배치.</li>
 *   <li>{@code freeCanvas_nested} — 3단 중첩 + §17.10 컨테이너 격리 + §17.11 들여쓰기.</li>
 *   <li>{@code freeCanvas_composite} — 복합 모듈 2종 + 같은 타입 3개(§17.4 3파일 고정).</li>
 *   <li>{@code freeCanvas_injection} — 전 문맥 악성 props → 이스케이프/0바이트.</li>
 * </ul>
 *
 * <p>산출은 아키타입 특성상 화면당 <b>3종 고정</b>({@code {stem}.jsp/.js/.css})이다(§17.4).
 *
 * <h2>골든 갱신 (의도된 템플릿 변경 시에만)</h2>
 * <pre>
 *   mvn -Dtest=FreeCanvasGoldenTest -Dforge.golden.update=true test
 *   git diff src/test/resources/golden/freeCanvas_*   # 반드시 육안 검토
 * </pre>
 */
class FreeCanvasGoldenTest {

    private static final boolean UPDATE_GOLDEN = Boolean.getBoolean("forge.golden.update");

    private static final String STEM = "freeBoard";

    @TempDir
    Path rootEmpty;
    @TempDir
    Path rootControls;
    @TempDir
    Path rootNested;
    @TempDir
    Path rootComposite;
    @TempDir
    Path rootInjection;

    // ------------------------------------------------------------------
    // 고정 입력
    // ------------------------------------------------------------------

    private static String definition(String canvasNode, String items) {
        return """
                {
                  "schemaVersion": 1,
                  "archetype": "FREE_CANVAS",
                  "stem": "%s",
                  "role": "admin",
                %s  "slots": { "canvasArea": [%s] }
                }
                """.formatted(STEM, canvasNode, items);
    }

    private static final String CANVAS = "  \"canvas\": { \"widthPx\": 1280, \"heightPx\": 800 },\n";

    /** 빈 캔버스 — 인스턴스 0개. */
    private static final String DEF_EMPTY = definition(CANVAS, "");

    /** 원자 컨트롤 3종. */
    private static final String DEF_CONTROLS = definition(CANVAS, """

                    { "instanceId": "button_1", "moduleTypeCode": "BUTTON",
                      "props": { "text": "저장", "styleClass": "btn-primary",
                                 "layoutXPx": 24, "layoutYPx": 24, "layoutWPx": 120, "layoutHPx": 40,
                                 "layoutZ": 2 } },
                    { "instanceId": "label_1", "moduleTypeCode": "LABEL",
                      "props": { "text": "게시판 관리",
                                 "layoutXPx": 24, "layoutYPx": 80, "layoutWPx": 240, "layoutHPx": 32 } },
                    { "instanceId": "textInput_1", "moduleTypeCode": "TEXT_INPUT",
                      "props": { "placeholder": "제목을 입력하세요", "type": "text",
                                 "layoutXPx": 24, "layoutYPx": 120, "layoutWPx": 360, "layoutHPx": 36 } }
                  """);

    /**
     * 3단 중첩. panelOuter(z 있음) / panelInner(z 없음) — §17.10 이전에는 이 차이로
     * 자식 z 가 부모 밖으로 새는지가 갈렸다. 골든이 그 규칙을 고정한다.
     */
    private static final String DEF_NESTED = definition(CANVAS, """

                    { "instanceId": "panelOuter", "moduleTypeCode": "PANEL",
                      "props": { "title": "바깥 패널", "borderYn": true,
                                 "layoutXPx": 40, "layoutYPx": 40, "layoutWPx": 520, "layoutHPx": 360,
                                 "layoutZ": 1 } },
                    { "instanceId": "panelInner", "moduleTypeCode": "PANEL",
                      "props": { "title": "안쪽 패널", "fillYn": true,
                                 "layoutXPx": 20, "layoutYPx": 40, "layoutWPx": 440, "layoutHPx": 240,
                                 "layoutParentId": "panelOuter" } },
                    { "instanceId": "button_deep", "moduleTypeCode": "BUTTON",
                      "props": { "text": "깊은 버튼",
                                 "layoutXPx": 16, "layoutYPx": 32, "layoutWPx": 140, "layoutHPx": 36,
                                 "layoutZ": 99, "layoutParentId": "panelInner" } },
                    { "instanceId": "button_out", "moduleTypeCode": "BUTTON",
                      "props": { "text": "바깥 버튼",
                                 "layoutXPx": 300, "layoutYPx": 60, "layoutWPx": 140, "layoutHPx": 36,
                                 "layoutZ": 5 } }
                  """);

    /** 복합 모듈 2종 + 같은 타입 3개(§17.4 파일 충돌 0 회귀). */
    private static final String DEF_COMPOSITE = definition(CANVAS, """

                    { "instanceId": "tableView_1", "moduleTypeCode": "TABLE_VIEW",
                      "props": { "columns": [ { "name": "boardNm", "displayName": "게시판명" },
                                              { "name": "useYn", "displayName": "사용여부" } ],
                                 "selectionMode": "checkbox", "pagingYn": true,
                                 "layoutXPx": 24, "layoutYPx": 24, "layoutWPx": 600, "layoutHPx": 300 } },
                    { "instanceId": "formView_1", "moduleTypeCode": "FORM_VIEW",
                      "props": { "fields": [ { "name": "boardNm", "label": "게시판명", "type": "text" } ],
                                 "layoutXPx": 650, "layoutYPx": 24, "layoutWPx": 400, "layoutHPx": 300 } },
                    { "instanceId": "button_1", "moduleTypeCode": "BUTTON",
                      "props": { "text": "첫째",
                                 "layoutXPx": 24, "layoutYPx": 360, "layoutWPx": 100, "layoutHPx": 36 } },
                    { "instanceId": "button_2", "moduleTypeCode": "BUTTON",
                      "props": { "text": "둘째",
                                 "layoutXPx": 140, "layoutYPx": 360, "layoutWPx": 100, "layoutHPx": 36 } },
                    { "instanceId": "button_3", "moduleTypeCode": "BUTTON",
                      "props": { "text": "셋째",
                                 "layoutXPx": 256, "layoutYPx": 360, "layoutWPx": 100, "layoutHPx": 36 } }
                  """);

    /**
     * 🔒 전 문맥 악성 props. 좌표는 문자열이라 CSS 0바이트여야 하고, 표시문자열은
     * 이스케이프된 형태로만 존재해야 한다.
     */
    private static final String DEF_INJECTION = definition(CANVAS, """

                    { "instanceId": "panel_evil", "moduleTypeCode": "PANEL",
                      "props": { "title": "<script>alert(1)</script>",
                                 "styleClass": "a}#x{display:none",
                                 "layoutXPx": 10, "layoutYPx": 10, "layoutWPx": 400, "layoutHPx": 200 } },
                    { "instanceId": "button_evil", "moduleTypeCode": "BUTTON",
                      "props": { "text": "\\"><img src=x onerror=alert(1)>",
                                 "styleClass": "btn\\" onclick=\\"alert(1)",
                                 "layoutXPx": "0;} body{background:red}", "layoutYPx": "0px",
                                 "layoutWPx": "100px;} *{display:none}", "layoutHPx": "40",
                                 "layoutParentId": "panel_evil" } },
                    { "instanceId": "label_evil", "moduleTypeCode": "LABEL",
                      "props": { "text": "EL:${7*7} DEFERRED:#{2+2} SEP:\\u2028 END:</script>",
                                 "layoutXPx": 20, "layoutYPx": 240, "layoutWPx": 300, "layoutHPx": 32 } }
                  """);

    // ------------------------------------------------------------------
    // 스냅샷 비교
    // ------------------------------------------------------------------

    @Test
    void 빈_캔버스_골든이_일치한다() throws IOException {
        runGolden("freeCanvas_empty", rootEmpty, DEF_EMPTY);
    }

    @Test
    void 원자_컨트롤_골든이_일치한다() throws IOException {
        runGolden("freeCanvas_controls", rootControls, DEF_CONTROLS);
    }

    @Test
    void 중첩_3단_골든이_일치한다() throws IOException {
        Map<String, String> bodies = runGolden("freeCanvas_nested", rootNested, DEF_NESTED);
        String jsp = bodies.get(STEM + ".jsp");
        String css = bodies.get(STEM + ".css");

        // §17.10 — 컨테이너는 z 유무와 무관하게 표시가 붙고, 격리 규칙이 항상 나온다.
        assertTrue(jsp.contains("frg-fc-item frg-fc-container frg-fc-1"), "z 있는 컨테이너 표시");
        assertTrue(jsp.contains("frg-fc-item frg-fc-container frg-fc-2"), "z 없는 컨테이너 표시");
        assertTrue(css.contains(".frg-fc-container {\n\tisolation: isolate;\n}"), "격리 규칙");
        // §17.11/§17.12 — 컨테이너(8) → 내용 상자(12) → 자식 컨테이너(16) → 내용 상자(20) → 자식(24)
        assertTrue(jsp.contains("\n        <div class=\"frg-fc-item frg-fc-container frg-fc-1\""),
                "루트 컨테이너 8칸");
        assertTrue(jsp.contains("\n            <div class=\"frg-fc-panel-body\">"), "내용 상자 12칸");
        assertTrue(jsp.contains("\n                <div class=\"frg-fc-item frg-fc-container frg-fc-2\""),
                "1단 자식 16칸");
        assertTrue(jsp.contains("\n                        <div class=\"frg-fc-item frg-fc-3\""),
                "2단 자식 24칸");
        // §17.12 — 자식은 장식 div 가 아니라 내용 상자 안에 있다("상자 안 상자").
        int bodyOpen = jsp.indexOf("<div class=\"frg-fc-panel-body\">");
        int child = jsp.indexOf("frg-fc-item frg-fc-container frg-fc-2");
        assertTrue(bodyOpen >= 0 && child > bodyOpen, "자식이 내용 상자 안에 있어야 한다:\n" + jsp);
    }

    @Test
    void 복합_모듈과_동일타입_다중배치_골든이_일치한다() throws IOException {
        runGolden("freeCanvas_composite", rootComposite, DEF_COMPOSITE);
        // §17.4 — 캔버스는 모듈별 파일을 만들지 않는다. 화면 산출은 언제나 3개.
        long screenFiles = countScreenArtifacts(rootComposite);
        assertEquals(3, screenFiles, "FREE_CANVAS 화면 산출은 3파일 고정이어야 한다");
    }

    @Test
    void 인젝션_골든이_일치하고_유출이_없다() throws IOException {
        Map<String, String> bodies = runGolden("freeCanvas_injection", rootInjection, DEF_INJECTION);
        String jsp = bodies.get(STEM + ".jsp");
        String css = bodies.get(STEM + ".css");

        // 표시문자열: **원시 태그**가 살아 있으면 안 된다.
        // (이스케이프된 "&lt;img src=x onerror=..." 는 무해한 텍스트이므로 문자열 포함 자체를
        //  금지하면 안 된다 — 태그로 열리는지만 본다.)
        assertFalse(jsp.contains("<script>alert"), "원시 script 유출:\n" + jsp);
        assertFalse(jsp.contains("<img"), "원시 img 태그 유출:\n" + jsp);
        assertFalse(jsp.matches("(?s).*<[a-zA-Z][^>]*\\son\\w+\\s*=.*"),
                "원시 이벤트 핸들러 속성 유출:\n" + jsp);
        // U+2028 은 **JS 문자열 문맥에서만** 문법을 깨다(거긴 jsString 이 공백으로 바꿈).
        // HTML 텍스트 노드에서는 무해하므로 JSP 가 아니라 JS 산출에 대해서만 단언한다.
        assertFalse(bodies.get(STEM + ".js").contains(" "), "JS 에 원시 U+2028 유출");
        // 이스케이프가 실제로 일어났다는 양성 증거.
        assertTrue(jsp.contains("&lt;img src=x onerror=alert(1)&gt;"), "이스케이프 흔적 없음:\n" + jsp);
        assertTrue(jsp.contains("&lt;script&gt;alert(1)&lt;/script&gt;"), "제목 이스케이프:\n" + jsp);

        // 🔒 계약 §18 — 생성물은 JSP 이므로 원시 `${...}`/`#{...}` 는 **타겟 톰캣이 렌더할 때**
        // EL 로 평가된다(생성 시점 FreeMarker 평가와 다른 층위). `$`/`#` 엔티티화로 원천 차단.
        assertFalse(jsp.contains("EL:49"), "EL 이 평가되면 안 된다:\n" + jsp);
        assertFalse(jsp.contains("${7*7}"), "원시 EL 시퀀스가 생성 JSP 에 실렸다:\n" + jsp);
        assertFalse(jsp.contains("#{2+2}"), "원시 지연 EL 시퀀스가 실렸다:\n" + jsp);
        assertTrue(jsp.contains("EL:&#36;{7*7}"), "EL 차단 흔적 없음:\n" + jsp);
        assertTrue(jsp.contains("DEFERRED:&#35;{2+2}"), "지연 EL 차단 흔적 없음:\n" + jsp);

        // 🔒 CSS: 문자열 좌표는 단 한 글자도 들어가지 않는다(§17.2 ?is_number 게이트).
        assertFalse(css.contains("body{background"), "CSS 인젝션 유출:\n" + css);
        assertFalse(css.contains("display:none"), "CSS 인젝션 유출:\n" + css);
        assertFalse(css.contains("panel_evil") || css.contains("button_evil"),
                "instanceId 가 CSS 로 유입:\n" + css);
        // 좌표 4키를 통과 못 한 인스턴스는 좌표 규칙 자체가 없어야 한다(seq 2 = button_evil).
        assertFalse(css.contains(".frg-fc-2 {"), "무효 좌표인데 규칙이 산출됨:\n" + css);
        // 그래도 마크업은 남는다(안전측 폴백).
        assertTrue(jsp.contains("frg-fc-2"), "마크업까지 사라지면 안 된다:\n" + jsp);
    }

    /** 🔒 캔버스 골든도 안전 산출물이어야 한다 — 배너 0 / 스크립트릿 0 / IIFE 골격. */
    @Test
    void 캔버스_골든은_안전_산출물이다() throws IOException {
        Map<String, String> bodies = generateAndRead(rootNested, DEF_NESTED);
        for (Map.Entry<String, String> e : bodies.entrySet()) {
            String name = e.getKey();
            String body = e.getValue();
            assertFalse(body.contains("JWORKS"), name + ": JWORKS 배너 유출");
            assertFalse(body.toUpperCase().contains("COPYRIGHT"), name + ": 저작권 배너 유출");
            if (name.endsWith(".jsp")) {
                assertFalse(body.matches("(?s).*<%[^@\\-].*"), name + ": 스크립트릿(<% ) 발견");
                assertFalse(body.contains("<%="), name + ": 표현식 스크립트릿 발견");
                assertFalse(body.contains("<%!"), name + ": 선언 스크립트릿 발견");
            }
        }
        assertTrue(bodies.get(STEM + ".js").contains("__defined"), "IIFE 골격");
    }

    /** 결정성: 같은 입력 2회 생성 → 본문 동일. */
    @Test
    void 같은_입력_2회_생성시_산출_본문이_동일하다() throws IOException {
        Map<String, String> first = generateAndRead(rootComposite, DEF_COMPOSITE);
        Map<String, String> second = generateAndRead(rootComposite, DEF_COMPOSITE);
        assertEquals(first, second, "2회 생성 본문이 다름(결정성 위반)");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** 생성 → (갱신 모드면 기록, 아니면 골든 비교) → 산출 본문 맵을 돌려준다. */
    private Map<String, String> runGolden(String goldenDir, Path targetRoot, String definitionJson)
            throws IOException {
        Map<String, String> bodies = generateAndRead(targetRoot, definitionJson);

        if (UPDATE_GOLDEN) {
            updateGolden(goldenDir, bodies);
            return bodies;
        }

        Path dir = goldenSourceDir(goldenDir);
        for (Map.Entry<String, String> e : bodies.entrySet()) {
            Path goldenFile = dir.resolve(e.getKey());
            assertTrue(Files.exists(goldenFile),
                    "골든 파일 없음(갱신 모드로 생성 필요): " + goldenFile);
            String expected = normalize(Files.readString(goldenFile, StandardCharsets.UTF_8));
            if (!expected.equals(e.getValue())) {
                fail(diagnose(goldenDir + "/" + e.getKey(), expected, e.getValue()));
            }
        }
        return bodies;
    }

    /** 화면 산출 3종을 생성해 파일명 → 본문(LF 정규화)으로 읽는다. */
    private Map<String, String> generateAndRead(Path targetRoot, String definitionJson)
            throws IOException {
        GenResult r = generate(targetRoot, definitionJson);
        assertEquals(GenResult.SUCCESS, r.resultCode(),
                "고정 입력은 SUCCESS 여야 한다: " + r.failReason());

        Map<String, String> bodies = new LinkedHashMap<>();
        bodies.put(STEM + ".jsp", read(targetRoot.resolve(
                "WEB-INF/views/admin/" + STEM + "/" + STEM + ".jsp")));
        bodies.put(STEM + ".js", read(targetRoot.resolve(
                "static/js/admin/" + STEM + "/" + STEM + ".js")));
        bodies.put(STEM + ".css", read(targetRoot.resolve(
                "static/css/admin/" + STEM + "/" + STEM + ".css")));
        return bodies;
    }

    private long countScreenArtifacts(Path targetRoot) throws IOException {
        GenResult r = generate(targetRoot, DEF_COMPOSITE);
        return r.files().stream()
                .filter(f -> f.relativePath().replace('\\', '/').contains("admin/" + STEM + "/"))
                .count();
    }

    private static String read(Path p) throws IOException {
        assertTrue(Files.exists(p), "산출 파일 없음: " + p);
        return normalize(Files.readString(p, StandardCharsets.UTF_8));
    }

    private static String normalize(String s) {
        return s.replace("\r\n", "\n").replace("\r", "\n");
    }

    private Path goldenSourceDir(String goldenDir) {
        var url = getClass().getClassLoader().getResource("golden/" + goldenDir);
        if (url != null) {
            try {
                return Paths.get(url.toURI());
            } catch (Exception ignored) {
                // 폴백으로 넘어간다.
            }
        }
        return Paths.get("src", "test", "resources", "golden", goldenDir);
    }

    private void updateGolden(String goldenDir, Map<String, String> bodies) {
        Path dir = Paths.get("src", "test", "resources", "golden", goldenDir);
        try {
            Files.createDirectories(dir);
            for (Map.Entry<String, String> e : bodies.entrySet()) {
                Files.writeString(dir.resolve(e.getKey()), e.getValue(), StandardCharsets.UTF_8);
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
        List<String> written = bodies.keySet().stream().toList();
        System.out.println("[golden-update] " + goldenDir + " <- " + written);
    }

    private static String diagnose(String fileName, String expected, String actual) {
        String[] exp = expected.split("\n", -1);
        String[] act = actual.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        sb.append("골든 불일치: ").append(fileName)
                .append(" (expected ").append(exp.length)
                .append(" lines, actual ").append(act.length).append(" lines)\n");
        for (int i = 0; i < Math.min(exp.length, act.length); i++) {
            if (!exp[i].equals(act[i])) {
                sb.append("  first diff at line ").append(i + 1).append(":\n")
                        .append("    expected: ").append(exp[i]).append('\n')
                        .append("    actual  : ").append(act[i]).append('\n');
                return sb.toString();
            }
        }
        if (exp.length != act.length) {
            sb.append("  라인 수가 다름. expected=").append(exp.length)
                    .append(", actual=").append(act.length);
        }
        return sb.toString();
    }

    private GenResult generate(Path targetRoot, String definitionJson) {
        ForgeScreenService screenService = mock(ForgeScreenService.class);
        ForgeProjectService projectService = mock(ForgeProjectService.class);
        var cfg = new CodeGenTemplateConfig().codeGenFreemarkerConfiguration();
        var renderer = new TemplateRenderer(cfg);
        var contextBuilder = new TemplateContextBuilder(new ObjectMapper());
        var pathSafety = new PathSafetyService();
        var fileWriter = new AtomicFileWriter();
        var runtimeSyncer = new RuntimeSyncer(pathSafety, fileWriter);
        var stubGenerator = new StubGenerator(pathSafety, fileWriter);

        ForgeScreen s = new ForgeScreen();
        s.setScreenId(30L);
        s.setProjectId(1L);
        s.setStem(STEM);
        s.setRoleCode("admin");
        s.setArchetypeCode("FREE_CANVAS");
        s.setDefinitionJson(definitionJson);

        ForgeProject p = new ForgeProject();
        p.setProjectId(1L);
        p.setTargetRootPath(targetRoot.toString());
        p.setPackageBase("com.jworks.forge");
        p.setJspBasePath("WEB-INF/views");
        p.setJsBasePath("static/js");
        p.setCssBasePath("static/css");
        p.setRuntimeVer("1.0.0");

        when(screenService.get(30L)).thenReturn(s);
        when(projectService.get(1L)).thenReturn(p);
        return new ScreenGenerator(screenService, projectService, contextBuilder, renderer,
                pathSafety, fileWriter, runtimeSyncer, stubGenerator).generate(30L);
    }
}
