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
 * P5.5a/b 골든파일 스냅샷 — 상세영역(Detail 슬롯 + AssociateTabs). {@link ListViewGoldenSnapshotTest}
 * 골격 재사용(계약 §9). {@code detailBasic}(DETAIL_BASIC) + {@code detailTabs}(ASSOCIATE_TABS) +
 * {@code detailToolbar}(TOOLBAR)를 배치한 고정 화면을 격리 {@code @TempDir}로 생성해, P5.5 신규 표면
 * (shell {@code with-detail} + Detail 3종)을 {@code golden/mgmtListDetail_detail/}과 정규화 후 바이트 비교.
 *
 * <p>골든 갱신: {@code mvn -Dtest=DetailGoldenSnapshotTest -Dforge.golden.update=true test}.
 */
class DetailGoldenSnapshotTest {

    private static final boolean UPDATE_GOLDEN = Boolean.getBoolean("forge.golden.update");
    private static final String GOLDEN_DIR = "golden/mgmtListDetail_detail";

    /** 정상값 픽스처(악성 아님) — DetailGenerationTest 정상 픽스처와 동일 구조. */
    private static final String DETAIL_JSON = """
            {
              "schemaVersion": 1, "archetype": "MGMT_LIST_DETAIL", "stem": "userMgmt", "role": "admin",
              "slots": {
                "listArea": [
                  { "instanceId": "tableView_1", "moduleTypeCode": "TABLE_VIEW",
                    "props": { "columns": [ { "name": "userId", "displayName": "ID", "displayYn": true, "sortYn": true } ],
                               "selectMode": "checkbox", "pagingYn": true } } ],
                "detailToolbar": [
                  { "instanceId": "detailToolbar_1", "moduleTypeCode": "TOOLBAR",
                    "props": { "buttons": [ { "actionCode": "approve", "label": "승인", "styleClass": "btn-primary" } ] } } ],
                "detailBasic": [
                  { "instanceId": "basic_1", "moduleTypeCode": "DETAIL_BASIC",
                    "props": { "editableYn": true, "attributeYn": true, "basicStyleClass": "basic-compact",
                               "fields": [
                                 { "name": "userId", "label": "사용자 ID", "type": "text", "requiredYn": true, "styleClass": "fld-id" },
                                 { "name": "memo", "label": "메모", "type": "textarea", "requiredYn": false, "styleClass": "" } ] } } ],
                "detailTabs": [
                  { "instanceId": "tabs_1", "moduleTypeCode": "ASSOCIATE_TABS",
                    "props": { "tabs": [
                      { "label": "권한", "tabClass": "tab-role", "frameId": "roleFrame" },
                      { "label": "이력", "tabClass": "tab-history", "frameId": "historyFrame" } ] } } ]
              }
            }
            """;

    /** P5.5 신규 표면 4파일. relativePath -> golden 파일명. */
    private static Map<String, String> detailFiles() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("WEB-INF/views/admin/userMgmt/userMgmt.jsp", "userMgmt.jsp");
        m.put("WEB-INF/views/admin/userMgmt/userMgmtDetail.jsp", "userMgmtDetail.jsp");
        m.put("static/js/admin/userMgmt/userMgmtDetail.js", "userMgmtDetail.js");
        // P6-2: 상세 CSS는 공통추출(commonScreenLayout.css) → per-screen Detail CSS 미산출(골든 제외).
        return m;
    }

    @TempDir
    Path targetRoot;

    @Test
    void 상세영역_골든_4종이_현재_산출과_정규화_후_일치한다() throws IOException {
        GenResult result = newGenerator(DETAIL_JSON).generate(10L);
        assertEquals(GenResult.SUCCESS, result.resultCode(), "고정 입력은 SUCCESS: " + result.failReason());

        Map<String, String> files = detailFiles();
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
    void 상세영역_골든은_안전_산출물이고_commonSection_배선을_담는다() throws IOException {
        newGenerator(DETAIL_JSON).generate(10L);
        String jsp = read("WEB-INF/views/admin/userMgmt/userMgmtDetail.jsp");
        String js = read("static/js/admin/userMgmt/userMgmtDetail.js");
        for (String body : new String[] { jsp, js }) {
            assertTrue(!body.contains("JWORKS"), "JWORKS 배너 0");
            assertTrue(!body.toUpperCase().contains("COPYRIGHT"), "저작권 배너 0");
        }
        // 스크립트릿 0(JSP).
        assertTrue(!jsp.matches("(?s).*<%[^@\\-].*"), "스크립트릿(<% ) 0");
        assertTrue(!jsp.contains("<%="), "표현식 스크립트릿 0");
        // MagicIAM commonSection 배선(§9.1 §8.6 동형).
        assertTrue(js.contains("window.MagicIAM_JSUserMgmtAdminDetail"), "Detail 네임스페이스");
        assertTrue(js.contains("MagicIAM_JSCommonSection.registEventBasicInfo({"), "기본정보 배선");
        assertTrue(js.contains("MagicIAM_JSCommonSection.registEventAssociateInfo({"), "연관탭 배선");
    }

    // ------------------------------------------------------------------
    // wiring / helpers (ListViewGoldenSnapshotTest 규칙과 동일)
    // ------------------------------------------------------------------
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
        s.setStem("userMgmt");
        s.setRoleCode("admin");
        s.setArchetypeCode("MGMT_LIST_DETAIL");
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
                .append(" (expected ").append(exp.length)
                .append(" lines, actual ").append(act.length).append(" lines)\n");
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
        System.out.println("[golden-update] detail wrote " + files.size() + " files to " + dir.toAbsolutePath());
    }
}
