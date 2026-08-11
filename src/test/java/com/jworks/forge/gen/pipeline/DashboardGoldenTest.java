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
 * DASHBOARD 골든 스냅샷 회귀망.
 *
 * <p><b>왜 필요했나</b>: P13-9 문서가 직접 적어 둔 그대로다 —
 * 숫자가 값이 아니라 FreeMarker 포매터 객체로 찍히는 결함({@code ?string} → {@code ?c})이
 * {@code module/widgetBase.ftl} 에도 있었는데 <b>"대시보드 골든이 없어 아무도 못 잡고 있었다"</b>.
 * FREE_CANVAS 를 채운 뒤 남은 마지막 무방비 아키타입이 여기였다.
 *
 * <h2>고정 시나리오</h2>
 * <ul>
 *   <li>{@code dashboard_widgets} — BAR_CHART + EMPTY_STATE. shell 1 + 모듈 3종×2 = <b>7파일</b>.</li>
 *   <li>{@code dashboard_injection} — 전 문맥 악성 props + 숫자 자리 문자열.</li>
 * </ul>
 *
 * <h2>골든 갱신</h2>
 * <pre>mvn -Dtest=DashboardGoldenTest -Dforge.golden.update=true test</pre>
 */
class DashboardGoldenTest {

    private static final boolean UPDATE_GOLDEN = Boolean.getBoolean("forge.golden.update");

    private static final String STEM = "opsBoard";

    @TempDir
    Path rootWidgets;
    @TempDir
    Path rootInjection;
    @TempDir
    Path rootDup;

    /** shell + (BarChart / EmptyState) × jsp·js·css. 파일명은 GenArtifacts 의 정적 접미사. */
    private static final Map<String, String> FILES = new LinkedHashMap<>();
    static {
        FILES.put("WEB-INF/views/admin/" + STEM + "/" + STEM + ".jsp", STEM + ".jsp");
        FILES.put("WEB-INF/views/admin/" + STEM + "/" + STEM + "BarChart.jsp", STEM + "BarChart.jsp");
        FILES.put("static/js/admin/" + STEM + "/" + STEM + "BarChart.js", STEM + "BarChart.js");
        FILES.put("static/css/admin/" + STEM + "/" + STEM + "BarChart.css", STEM + "BarChart.css");
        FILES.put("WEB-INF/views/admin/" + STEM + "/" + STEM + "EmptyState.jsp", STEM + "EmptyState.jsp");
        FILES.put("static/js/admin/" + STEM + "/" + STEM + "EmptyState.js", STEM + "EmptyState.js");
        FILES.put("static/css/admin/" + STEM + "/" + STEM + "EmptyState.css", STEM + "EmptyState.css");
    }

    private static String definition(String widgets) {
        return """
                {
                  "schemaVersion": 1,
                  "archetype": "DASHBOARD",
                  "stem": "%s",
                  "role": "admin",
                  "slots": { "widgetArea": [%s] }
                }
                """.formatted(STEM, widgets);
    }

    private static final String DEF_WIDGETS = definition("""

                { "instanceId": "barChart_1", "moduleTypeCode": "BAR_CHART",
                  "props": { "title": "처리율", "value": 82, "unit": "%" } },
                { "instanceId": "emptyState_1", "moduleTypeCode": "EMPTY_STATE",
                  "props": { "title": "대기 건 없음", "description": "처리할 항목이 없습니다.",
                             "actionText": "새로 만들기" } }
              """);

    /** 🔒 전 문맥 악성 props + 숫자 자리에 문자열(§17.9 회귀 표면). */
    private static final String DEF_INJECTION = definition("""

                { "instanceId": "barChart_1", "moduleTypeCode": "BAR_CHART",
                  "props": { "title": "<script>alert(1)</script> ${7*7}",
                             "value": "99\\" onload=\\"alert(1)",
                             "unit": "</script><b>%</b>" } },
                { "instanceId": "emptyState_1", "moduleTypeCode": "EMPTY_STATE",
                  "props": { "title": "#{2+2}",
                             "description": "line\\u2028sep </script><script>evil()</script>",
                             "actionText": "\\"><img src=x onerror=alert(1)>" } }
              """);

    // ---------- 스냅샷 ----------

    @Test
    void 위젯_대시보드_골든_7종이_일치한다() throws IOException {
        Map<String, String> bodies = runGolden("dashboard_widgets", rootWidgets, DEF_WIDGETS);

        // 🔒 §17.9 회귀: 숫자는 값으로 찍혀야 한다(포매터 객체 금지).
        String barJsp = bodies.get(STEM + "BarChart.jsp");
        assertTrue(barJsp.contains("data-value=\"82\""), "숫자 값 산출:\n" + barJsp);
        assertFalse(barJsp.contains("NumberFormatter"), "포매터 객체가 찍혔다:\n" + barJsp);
        assertFalse(barJsp.contains("@"), "객체 toString 유출 의심:\n" + barJsp);
    }

    @Test
    void 인젝션_골든이_일치하고_유출이_없다() throws IOException {
        Map<String, String> bodies = runGolden("dashboard_injection", rootInjection, DEF_INJECTION);

        for (Map.Entry<String, String> e : bodies.entrySet()) {
            String name = e.getKey();
            String body = e.getValue();
            assertFalse(body.contains("<script>alert"), name + ": 원시 script 유출");
            assertFalse(body.contains("<script>evil"), name + ": 원시 script 유출");
            assertFalse(body.contains("<img"), name + ": 원시 img 유출");
            if (name.endsWith(".jsp")) {
                // 🔒 §18 — 생성물은 JSP다. 원시 EL 시퀀스가 남으면 타겟이 렌더할 때 평가된다.
                // 템플릿 자신의 정적 EL(${ctx} / c:set 의 contextPath)은 자유문자열이 아니므로 제외.
                assertFalse(body.replace("${ctx}", "")
                                .replace("${pageContext.request.contextPath}", "")
                                .contains("${"),
                        name + ": 원시 ${ 유출:\n" + body);
                assertFalse(body.contains("#{"), name + ": 원시 #{ 유출");
                // 판정 기준은 "onload 라는 글자가 있나"가 아니라 **원시 따옴표로 열린 핸들러인가**다.
                // htmlAttr 이 "를 &quot; 로 바꾸므로 속성값 안의 onload= 텍스트는 빠져나가지 못한다
                // (data-value="99&quot; onload=&quot;alert(1)" 은 안전한 산출이다).
                assertFalse(body.matches("(?s).*\\son\\w+\\s*=\\s*[\"'].*"),
                        name + ": 원시 따옴표로 열린 이벤트 핸들러 유출:\n" + body);
            }
            assertFalse(body.contains("JWORKS"), name + ": 배너 유출");
        }
        // 숫자 자리에 문자열이 오면 그대로 텍스트로만 다뤄진다(속성 탈출 0).
        assertFalse(bodies.get(STEM + "BarChart.jsp").contains("onload=\"alert"), "속성 탈출");
    }

    /**
     * ⚠ <b>알려진 결함을 현재 동작으로 못박는다</b>: 같은 위젯 타입을 2개 놓으면 파일명이 충돌한다
     * (모듈 파일명 = stem + 타입별 정적 접미사 → 인스턴스 구분자가 없다). P13 문서가
     * "범위 밖, 기록만" 으로 남긴 항목이며 골든이 없어 검증도 없었다. 고치면 이 테스트가 깨진다 —
     * 그때 <b>의도된 변경</b>임을 확인하고 갱신하라.
     */
    @Test
    void 같은_위젯을_2개_놓으면_첫번째만_산출된다_알려진_결함() throws IOException {
        String def = definition("""

                    { "instanceId": "barChart_1", "moduleTypeCode": "BAR_CHART",
                      "props": { "title": "첫째", "value": 11 } },
                    { "instanceId": "barChart_2", "moduleTypeCode": "BAR_CHART",
                      "props": { "title": "둘째", "value": 22 } }
                  """);
        GenResult r = generate(rootDup, def);
        assertEquals(GenResult.SUCCESS, r.resultCode());

        // 인스턴스마다 아티팩트를 계획하는데 파일명에는 인스턴스 구분자가 없다
        // → **같은 경로를 두 번 쓴다**(3개 경로 × 인스턴스 2 = 기록 6건).
        var barPaths = r.files().stream()
                .map(f -> f.relativePath().replace('\\', '/'))
                .filter(p -> p.contains("/" + STEM + "BarChart."))
                .toList();
        assertEquals(6, barPaths.size(), "인스턴스 2개분이 계획된다(현재 동작)");
        assertEquals(3, barPaths.stream().distinct().count(),
                "그런데 경로는 3개뿐 — 같은 파일을 두 번 쓴다(결함의 핵심)");

        // 템플릿이 widgetArea 에서 **첫 일치 인스턴스**를 집으므로 둘째는 산출에 없다.
        String jsp = read(rootDup.resolve(
                "WEB-INF/views/admin/" + STEM + "/" + STEM + "BarChart.jsp"));
        assertTrue(jsp.contains("첫째"), "첫 인스턴스가 산출:\n" + jsp);
        assertFalse(jsp.contains("둘째"), "둘째가 조용히 사라지는 것이 현재 동작이다:\n" + jsp);
        assertTrue(jsp.contains("data-value=\"11\""), "첫 인스턴스 값:\n" + jsp);
    }

    @Test
    void 같은_입력_2회_생성시_산출_본문이_동일하다() throws IOException {
        Map<String, String> first = generateAndRead(rootWidgets, DEF_WIDGETS);
        Map<String, String> second = generateAndRead(rootWidgets, DEF_WIDGETS);
        assertEquals(first, second, "2회 생성 본문이 다름(결정성 위반)");
    }

    // ---------- helpers ----------

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

    private Map<String, String> generateAndRead(Path targetRoot, String definitionJson)
            throws IOException {
        GenResult r = generate(targetRoot, definitionJson);
        assertEquals(GenResult.SUCCESS, r.resultCode(),
                "고정 입력은 SUCCESS 여야 한다: " + r.failReason());
        Map<String, String> bodies = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : FILES.entrySet()) {
            bodies.put(e.getValue(), read(targetRoot.resolve(e.getKey())));
        }
        return bodies;
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
        System.out.println("[golden-update] " + goldenDir + " <- " + bodies.keySet());
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
        s.setScreenId(60L);
        s.setProjectId(1L);
        s.setStem(STEM);
        s.setRoleCode("admin");
        s.setArchetypeCode("DASHBOARD");
        s.setDefinitionJson(definitionJson);

        ForgeProject p = new ForgeProject();
        p.setProjectId(1L);
        p.setTargetRootPath(targetRoot.toString());
        p.setPackageBase("com.jworks.forge");
        p.setJspBasePath("WEB-INF/views");
        p.setJsBasePath("static/js");
        p.setCssBasePath("static/css");
        p.setRuntimeVer("1.0.0");

        when(screenService.get(60L)).thenReturn(s);
        when(projectService.get(1L)).thenReturn(p);
        return new ScreenGenerator(screenService, projectService, contextBuilder, renderer,
                pathSafety, fileWriter, runtimeSyncer, stubGenerator).generate(60L);
    }
}
