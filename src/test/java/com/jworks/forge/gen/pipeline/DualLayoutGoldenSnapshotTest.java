package com.jworks.forge.gen.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * P5-5c 골든파일 스냅샷 — DUAL_LAYOUT(좌우 2단) shell/js/css 3종(계약 §10).
 * 골든 갱신: {@code mvn -Dtest=DualLayoutGoldenSnapshotTest -Dforge.golden.update=true test}.
 */
class DualLayoutGoldenSnapshotTest {

    private static final boolean UPDATE_GOLDEN = Boolean.getBoolean("forge.golden.update");
    private static final String GOLDEN_DIR = "golden/dualLayout";

    /** 정상값 픽스처(악성 아님) — DualLayoutGenerationTest 정상 픽스처와 동일 구조. */
    private static final String DUAL_JSON = """
            {
              "schemaVersion": 1, "archetype": "DUAL_LAYOUT", "stem": "orgDual", "role": "admin",
              "slots": {
                "leftArea": [
                  { "instanceId": "left_1", "moduleTypeCode": "LAYOUT_FRAME",
                    "props": { "frameId": "leftListFrame", "title": "조직 목록", "paneClass": "pane-list" } } ],
                "rightArea": [
                  { "instanceId": "right_1", "moduleTypeCode": "LAYOUT_FRAME",
                    "props": { "frameId": "rightDetailFrame", "title": "조직 상세" } } ]
              }
            }
            """;

    private static Map<String, String> dualFiles() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("WEB-INF/views/admin/orgDual/orgDual.jsp", "orgDual.jsp");
        m.put("static/js/admin/orgDual/orgDual.js", "orgDual.js");
        // P6-2: 듀얼 CSS는 공통추출(commonScreenLayout.css) → per-screen 듀얼 CSS 미산출(골든 제외).
        return m;
    }

    @TempDir
    Path targetRoot;

    @Test
    void DUAL_골든_3종이_현재_산출과_정규화_후_일치한다() throws IOException {
        GenResult result = newGenerator(DUAL_JSON).generate(10L);
        assertEquals(GenResult.SUCCESS, result.resultCode(), "고정 입력은 SUCCESS: " + result.failReason());

        Map<String, String> files = dualFiles();
        if (UPDATE_GOLDEN) {
            updateGolden(files);
            return;
        }
        Path goldenRoot = goldenSourceDir();
        for (Map.Entry<String, String> e : files.entrySet()) {
            Path produced = targetRoot.resolve(e.getKey());
            assertTrue(Files.exists(produced), "산출 파일 없음: " + e.getKey());
            String actual = normalize(Files.readString(produced, StandardCharsets.UTF_8));
            Path goldenFile = goldenRoot.resolve(e.getValue());
            assertTrue(Files.exists(goldenFile), "골든 파일 없음(갱신 모드 필요): " + goldenFile);
            String expected = normalize(Files.readString(goldenFile, StandardCharsets.UTF_8));
            if (!expected.equals(actual)) {
                fail(diagnose(e.getValue(), expected, actual));
            }
        }
    }

    @Test
    void DUAL_골든은_안전_산출물이고_commonSection_배선을_담는다() throws IOException {
        newGenerator(DUAL_JSON).generate(10L);
        String jsp = read("WEB-INF/views/admin/orgDual/orgDual.jsp");
        String js = read("static/js/admin/orgDual/orgDual.js");
        for (String body : new String[] { jsp, js }) {
            assertTrue(!body.contains("JWORKS"), "JWORKS 배너 0");
            assertTrue(!body.toUpperCase().contains("COPYRIGHT"), "저작권 배너 0");
        }
        assertTrue(!jsp.matches("(?s).*<%[^@\\-].*"), "스크립트릿(<% ) 0");
        assertTrue(!jsp.contains("<%="), "표현식 스크립트릿 0");
        assertTrue(jsp.contains("<body class=\"dual-layout\">"), "body.dual-layout(commonSection dual 계약)");
        assertTrue(js.contains("JWorks_JSCommonSection.postMessageEventListener"), "프레임 동기화 배선");
    }

    private ScreenGenerator newGenerator(String json) {
        ForgeScreenService screenService = mock(ForgeScreenService.class);
        ForgeProjectService projectService = mock(ForgeProjectService.class);
        var cfg = new CodeGenTemplateConfig().codeGenFreemarkerConfiguration();
        var renderer = new TemplateRenderer(cfg);
        var contextBuilder = new TemplateContextBuilder(new ObjectMapper());
        var pathSafety = new PathSafetyService();
        var fileWriter = new AtomicFileWriter();
        var runtimeSyncer = new RuntimeSyncer(pathSafety, fileWriter);
        var stubGenerator = new StubGenerator(pathSafety, fileWriter);
        when(screenService.get(10L)).thenReturn(screen(json));
        when(projectService.get(1L)).thenReturn(project());
        return new ScreenGenerator(screenService, projectService, contextBuilder, renderer,
                pathSafety, fileWriter, runtimeSyncer, stubGenerator);
    }

    private ForgeScreen screen(String json) {
        ForgeScreen s = new ForgeScreen();
        s.setScreenId(10L);
        s.setProjectId(1L);
        s.setStem("orgDual");
        s.setRoleCode("admin");
        s.setArchetypeCode("DUAL_LAYOUT");
        s.setDefinitionJson(json);
        return s;
    }

    private ForgeProject project() {
        ForgeProject p = new ForgeProject();
        p.setProjectId(1L);
        p.setTargetRootPath(targetRoot.toString());
        p.setPackageBase("com.jworks.forge");
        p.setJspBasePath("WEB-INF/views");
        p.setJsBasePath("static/js");
        p.setCssBasePath("static/css");
        p.setRuntimeVer("1.0.0");
        return p;
    }

    private String read(String rel) throws IOException {
        return normalize(Files.readString(targetRoot.resolve(rel), StandardCharsets.UTF_8));
    }

    private static String normalize(String s) {
        return s.replace("\r\n", "\n").replace("\r", "\n");
    }

    private static String diagnose(String fileName, String expected, String actual) {
        String[] exp = expected.split("\n", -1);
        String[] act = actual.split("\n", -1);
        int n = Math.min(exp.length, act.length);
        StringBuilder sb = new StringBuilder();
        sb.append("골든 불일치: ").append(fileName)
                .append(" (expected ").append(exp.length).append(", actual ").append(act.length).append(")\n");
        for (int i = 0; i < n; i++) {
            if (!exp[i].equals(act[i])) {
                sb.append("  first diff at line ").append(i + 1).append(":\n");
                sb.append("    expected: ").append(exp[i]).append('\n');
                sb.append("    actual  : ").append(act[i]).append('\n');
                return sb.toString();
            }
        }
        if (exp.length != act.length) {
            sb.append("  라인 수가 다름. expected=").append(exp.length).append(", actual=").append(act.length);
        }
        return sb.toString();
    }

    private Path goldenSourceDir() {
        var url = getClass().getClassLoader().getResource(GOLDEN_DIR);
        if (url != null) {
            try {
                return Paths.get(url.toURI());
            } catch (Exception ignore) {
                // fall through
            }
        }
        return Paths.get("src", "test", "resources", GOLDEN_DIR.replace("/", java.io.File.separator));
    }

    private void updateGolden(Map<String, String> files) {
        Path dir = Paths.get("src", "test", "resources", GOLDEN_DIR.replace("/", java.io.File.separator));
        try {
            Files.createDirectories(dir);
            for (Map.Entry<String, String> e : files.entrySet()) {
                Path produced = targetRoot.resolve(e.getKey());
                String body = normalize(Files.readString(produced, StandardCharsets.UTF_8));
                Files.writeString(dir.resolve(e.getValue()), body, StandardCharsets.UTF_8);
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
        System.out.println("[golden-update] dualLayout wrote " + files.size() + " files to " + dir.toAbsolutePath());
    }
}
