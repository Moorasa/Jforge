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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
 * 🔒 P6-4 골든 심화 — <b>풀조합 MGMT_LIST_DETAIL</b>(searchArea + listToolbar + listArea(TABLE_VIEW)
 * + detailToolbar + detailBasic + detailTabs)를 <b>한 화면에</b> 배치한 산출 10종 골든.
 *
 * <p>단위 골든(뷰 단독·detail 단독)이 못 잡는 <b>슬롯 상호작용</b>(list.ftl의 search/toolbar 조건부 렌더 +
 * listArea 뷰 분기 + shell with-detail + Detail 세트 동시 산출)을 한 번에 회귀 고정한다.
 * {@code CLEAN}(정상값)과 {@code INJECT}(전 슬롯·전 문맥 악성 props) 2케이스로, 인젝션 회귀도 골든에 박제한다.
 *
 * <p>골든 갱신: {@code mvn -Dtest=FullCombinationGoldenSnapshotTest -Dforge.golden.update=true test}.
 */
class FullCombinationGoldenSnapshotTest {

    private static final boolean UPDATE_GOLDEN = Boolean.getBoolean("forge.golden.update");

    enum Case {
        CLEAN("golden/fullCombination", CLEAN_JSON),
        INJECT("golden/injection_fullCombination", INJECT_JSON);

        final String goldenDir;
        final String json;

        Case(String goldenDir, String json) {
            this.goldenDir = goldenDir;
            this.json = json;
        }
    }

    // 정상 픽스처: 6슬롯 전부 배치.
    private static final String CLEAN_JSON = """
            {
              "schemaVersion": 1, "archetype": "MGMT_LIST_DETAIL", "stem": "userMgmt", "role": "admin",
              "slots": {
                "searchArea": [
                  { "instanceId": "search_1", "moduleTypeCode": "SEARCH_FILTER_BAR",
                    "props": { "filters": [ { "name": "status", "label": "상태", "options": "A:활성,I:비활성" } ],
                               "keywordYn": true, "dateRangeYn": true } } ],
                "listToolbar": [
                  { "instanceId": "toolbar_1", "moduleTypeCode": "TOOLBAR",
                    "props": { "buttons": [ { "actionCode": "add", "label": "추가", "styleClass": "btn-primary" },
                                            { "actionCode": "delete", "label": "삭제", "styleClass": "btn-secondary" } ] } } ],
                "listArea": [
                  { "instanceId": "table_1", "moduleTypeCode": "TABLE_VIEW",
                    "props": { "columns": [ { "name": "userId", "displayName": "ID", "displayYn": true, "sortYn": true },
                                            { "name": "userName", "displayName": "이름", "displayYn": true, "sortYn": false } ],
                               "selectMode": "checkbox", "pagingYn": true } } ],
                "detailToolbar": [
                  { "instanceId": "dtoolbar_1", "moduleTypeCode": "TOOLBAR",
                    "props": { "buttons": [ { "actionCode": "approve", "label": "승인", "styleClass": "btn-primary" } ] } } ],
                "detailBasic": [
                  { "instanceId": "basic_1", "moduleTypeCode": "DETAIL_BASIC",
                    "props": { "editableYn": true, "attributeYn": true, "basicStyleClass": "basic-compact",
                               "fields": [ { "name": "userId", "label": "사용자 ID", "type": "text", "requiredYn": true, "styleClass": "fld-id" },
                                           { "name": "email", "label": "이메일", "type": "email", "requiredYn": false, "styleClass": "" } ] } } ],
                "detailTabs": [
                  { "instanceId": "tabs_1", "moduleTypeCode": "ASSOCIATE_TABS",
                    "props": { "tabs": [ { "label": "권한", "tabClass": "tab-role", "frameId": "roleFrame" } ] } } ]
              }
            }
            """;

    // 인젝션 픽스처: 전 슬롯 대표 props에 전 문맥(HTML텍스트/속성/JS/CSS) 악성값.
    private static final String INJECT_JSON = """
            {
              "schemaVersion": 1, "archetype": "MGMT_LIST_DETAIL", "stem": "userMgmt", "role": "admin",
              "slots": {
                "searchArea": [
                  { "instanceId": "search_1", "moduleTypeCode": "SEARCH_FILTER_BAR",
                    "props": { "filters": [ { "name": "st\\"onx", "label": "<script>alert(1)</script>", "options": "A:<b>on</b>,I:x" } ],
                               "keywordYn": true, "dateRangeYn": false } } ],
                "listToolbar": [
                  { "instanceId": "toolbar_1", "moduleTypeCode": "TOOLBAR",
                    "props": { "buttons": [ { "actionCode": "a\\"b", "label": "</script>${7*7}", "styleClass": "x\\"evil" } ] } } ],
                "listArea": [
                  { "instanceId": "table_1", "moduleTypeCode": "TABLE_VIEW",
                    "props": { "columns": [ { "name": "c\\"d", "displayName": "<img onerror=1>", "displayYn": true, "sortYn": true } ],
                               "selectMode": "checkbox", "pagingYn": true } } ],
                "detailBasic": [
                  { "instanceId": "basic_1", "moduleTypeCode": "DETAIL_BASIC",
                    "props": { "editableYn": true, "attributeYn": false, "basicStyleClass": "ok\\"evil",
                               "fields": [ { "name": "n\\" x", "label": "<script>x</script>", "type": "javascript:evil", "requiredYn": true, "styleClass": "y\\"z" } ] } } ],
                "detailTabs": [
                  { "instanceId": "tabs_1", "moduleTypeCode": "ASSOCIATE_TABS",
                    "props": { "tabs": [ { "label": "</script>${9*9}", "tabClass": "t\\"c", "frameId": "f\\">" } ] } } ]
              }
            }
            """;

    /** 풀조합 산출 10종. relativePath -> golden 파일명. */
    private static Map<String, String> comboFiles() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("WEB-INF/views/admin/userMgmt/userMgmt.jsp", "userMgmt.jsp");
        m.put("WEB-INF/views/admin/userMgmt/userMgmtList.jsp", "userMgmtList.jsp");
        m.put("static/js/admin/userMgmt/userMgmtList.js", "userMgmtList.js");
        m.put("static/css/admin/userMgmt/userMgmtList.css", "userMgmtList.css");
        m.put("WEB-INF/views/admin/userMgmt/userMgmtListTableView.jsp", "userMgmtListTableView.jsp");
        m.put("static/js/admin/userMgmt/userMgmtListTableView.js", "userMgmtListTableView.js");
        m.put("WEB-INF/views/admin/userMgmt/userMgmtDetail.jsp", "userMgmtDetail.jsp");
        m.put("static/js/admin/userMgmt/userMgmtDetail.js", "userMgmtDetail.js");
        // P6-2: 뷰/상세 CSS는 공통추출(commonScreenLayout.css) → per-screen CSS 미산출(골든 제외).
        return m;
    }

    @TempDir
    Path targetRoot;

    @ParameterizedTest
    @EnumSource(Case.class)
    void 풀조합_골든_8종이_현재_산출과_정규화_후_일치한다(Case c) throws IOException {
        GenResult result = newGenerator(c.json).generate(10L);
        assertEquals(GenResult.SUCCESS, result.resultCode(), c + " 고정 입력은 SUCCESS: " + result.failReason());

        Map<String, String> files = comboFiles();
        if (UPDATE_GOLDEN) {
            updateGolden(c, files);
            return;
        }
        Path goldenRoot = goldenSourceDir(c.goldenDir);
        for (Map.Entry<String, String> e : files.entrySet()) {
            Path produced = targetRoot.resolve(e.getKey());
            assertTrue(Files.exists(produced), c + " 산출 파일 없음: " + e.getKey());
            String actual = normalize(Files.readString(produced, StandardCharsets.UTF_8));
            Path goldenFile = goldenRoot.resolve(e.getValue());
            assertTrue(Files.exists(goldenFile), c + " 골든 파일 없음(갱신 모드 필요): " + goldenFile);
            String expected = normalize(Files.readString(goldenFile, StandardCharsets.UTF_8));
            if (!expected.equals(actual)) {
                fail(diagnose(c + "/" + e.getValue(), expected, actual));
            }
        }
    }

    @Test
    void 풀조합_CLEAN은_전슬롯을_렌더하고_INJECT는_전문맥_이스케이프된다() throws IOException {
        // CLEAN: 6슬롯 전부 렌더 확인.
        newGenerator(Case.CLEAN.json).generate(10L);
        String list = read("WEB-INF/views/admin/userMgmt/userMgmtList.jsp");
        String detail = read("WEB-INF/views/admin/userMgmt/userMgmtDetail.jsp");
        String shell = read("WEB-INF/views/admin/userMgmt/userMgmt.jsp");
        assertTrue(shell.contains("with-detail") && shell.contains("userMgmtDetail.jsp"), "shell 상세영역 배선");
        assertTrue(list.contains("class=\"search\"") && list.contains("class=\"list-toolbar\""), "search+toolbar 렌더");
        assertTrue(list.contains("userMgmtListTableView.jsp"), "listArea 뷰 include");
        assertTrue(detail.contains("id=\"basic-info\"") && detail.contains("id=\"associate-info\"")
                && detail.contains("class=\"detail-toolbar\""), "detail 3슬롯 렌더");

        // INJECT: 전 산출물에 원문 인젝션 유출 0.
        newGenerator(Case.INJECT.json).generate(10L);
        for (String rel : comboFiles().keySet()) {
            String body = read(rel);
            assertFalse(body.contains("<script>alert(1)</script>"), rel + ": search label 스크립트 유출");
            assertFalse(body.contains("<script>x</script>"), rel + ": basic label 스크립트 유출");
            assertFalse(body.contains("<img onerror=1>"), rel + ": column displayName 스크립트 유출");
            assertFalse(body.contains(">49<"), rel + ": ${7*7} 평가 유출");
            assertFalse(body.contains(">81<"), rel + ": ${9*9} 평가 유출");
            if (rel.endsWith(".js")) {
                assertFalse(body.contains("</script>"), rel + ": JS 내 </script> 원문 유출");
            }
        }
        // 미허용 type(javascript:evil) → text 수렴.
        assertTrue(read("WEB-INF/views/admin/userMgmt/userMgmtDetail.jsp").contains("type=\"text\""),
                "미허용 type은 text로 수렴");
    }

    // ------------------------------------------------------------------
    // wiring / helpers
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

    private Path goldenSourceDir(String goldenDir) {
        var url = getClass().getClassLoader().getResource(goldenDir);
        if (url != null) {
            try {
                return Paths.get(url.toURI());
            } catch (Exception ignore) {
                // fall through
            }
        }
        return Paths.get("src", "test", "resources", goldenDir.replace("/", java.io.File.separator));
    }

    private void updateGolden(Case c, Map<String, String> files) {
        Path dir = Paths.get("src", "test", "resources", c.goldenDir.replace("/", java.io.File.separator));
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
        System.out.println("[golden-update] " + c + " wrote " + files.size() + " files to " + dir.toAbsolutePath());
    }
}
