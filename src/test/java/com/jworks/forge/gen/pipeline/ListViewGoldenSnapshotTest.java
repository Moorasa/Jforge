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
 * P5-6 골든파일 스냅샷 검증 — MVP 스코프 listArea 뷰 3종(CARD_VIEW / TREE_VIEW / FORM_VIEW).
 *
 * <p>{@link GoldenSnapshotTest}(P4-7, TABLE_VIEW)의 골격을 확장한다. 각 뷰를 {@code listArea}에
 * 배치한 <b>고정 DEFINITION_JSON + 고정 프로젝트 메타</b>를 격리된 {@code @TempDir} 타겟 루트로
 * 실제 파이프라인({@link ScreenGenerator})으로 생성한 뒤, 산출 7종
 * (shell/list 4종 + 해당 뷰 3종)을
 * {@code src/test/resources/golden/mgmtListDetail_{cardView,treeView,formView}/} 의 기대 산출물과
 * <b>개행 정규화(CRLF→LF) 후</b> 바이트 비교한다. 불일치 시 어느 파일·어느 라인이 어긋났는지
 * 진단 출력하며 실패한다.
 *
 * <h2>정상값 픽스처(악성 아님)</h2>
 * 각 뷰의 DEFINITION_JSON 은 {@code CardViewGenerationTest}/{@code TreeViewGenerationTest}/
 * {@code FormViewGenerationTest} 의 정상 픽스처(설정 props 정상값)와 동일하게 유지한다. 인젝션
 * 회귀 golden 은 {@link InjectionGoldenTest} 가 담당(별도 golden 세트).
 *
 * <h2>결정성(재현성)</h2>
 * 입력은 고정 상수. 타임스탬프·랜덤·절대경로가 산출 본문에 들어가지 않는다. 백업 파일은 비교
 * 대상에서 제외(생성 파일 본문만). 비교 전 CRLF/CR → LF 정규화.
 *
 * <h2>🔒 골든 안전성</h2>
 * 골든은 실제 안전 산출물이어야 한다: JWORKS 배너 0, JSP 스크립트릿 0, jQuery 3.7.1 외 참조 0,
 * JS 네임스페이스+IIFE(__defined) 존재, §8.6 JWorks init 배선 일치. 본 테스트가 매 실행마다
 * 이 속성을 자동 단언한다.
 *
 * <h2>골든 갱신 절차 (의도된 템플릿 변경 시에만)</h2>
 * <pre>
 *   mvn -Dtest=ListViewGoldenSnapshotTest -Dforge.golden.update=true test
 *   # git diff 로 산출 변경 검토(배너/스크립트릿/이스케이프/JWorks API 회귀 없는지) 후 커밋.
 * </pre>
 * 자세한 내용은 {@code src/test/resources/golden/README.md} 참조.
 */
class ListViewGoldenSnapshotTest {

    private static final boolean UPDATE_GOLDEN = Boolean.getBoolean("forge.golden.update");

    /** MVP 스코프 뷰 3종 각각의 고정 픽스처·골든 디렉터리·산출 파일 맵·NS/배선 단언 자료. */
    enum ViewCase {
        CARD("golden/mgmtListDetail_cardView", CARDVIEW_JSON, "CardView",
                "JWorks_JSCommonListCardView.init({", cardFiles()),
        TREE("golden/mgmtListDetail_treeView", TREEVIEW_JSON, "TreeView",
                "JWorks_JSCommonListTreeView.init({", treeFiles()),
        FORM("golden/mgmtListDetail_formView", FORMVIEW_JSON, "FormView",
                "JWorks_JSCommonListFormView.init({", formFiles());

        final String goldenDir;
        final String json;
        final String viewSuffix;   // CardView/TreeView/FormView
        final String initWiring;   // §8.6 JWorks init 시그니처
        final Map<String, String> files; // relativePath -> golden 파일명

        ViewCase(String goldenDir, String json, String viewSuffix, String initWiring,
                Map<String, String> files) {
            this.goldenDir = goldenDir;
            this.json = json;
            this.viewSuffix = viewSuffix;
            this.initWiring = initWiring;
            this.files = files;
        }
    }

    // ------------------------------------------------------------------
    // 고정 픽스처(정상값) — 단위 GenerationTest 정상 픽스처와 동일
    // ------------------------------------------------------------------

    private static final String CARDVIEW_JSON = """
            {
              "schemaVersion": 1, "archetype": "MGMT_LIST_DETAIL", "stem": "userMgmt", "role": "admin",
              "slots": { "listArea": [
                { "instanceId": "cardView_1", "moduleTypeCode": "CARD_VIEW",
                  "props": { "titleField": "userName", "subtitleField": "userId", "imageField": "avatar",
                             "columns": [ { "name": "deptName", "displayName": "부서", "displayYn": true, "sortYn": false } ],
                             "selectMode": "checkbox", "pagingYn": true, "categoryYn": true,
                             "cardStyleClass": "card-compact" } } ] }
            }
            """;

    private static final String TREEVIEW_JSON = """
            {
              "schemaVersion": 1, "archetype": "MGMT_LIST_DETAIL", "stem": "userMgmt", "role": "admin",
              "slots": { "listArea": [
                { "instanceId": "treeView_1", "moduleTypeCode": "TREE_VIEW",
                  "props": { "labelField": "orgName", "idField": "orgNo", "parentField": "parentNo",
                             "iconField": "orgType", "selectMode": "checkbox",
                             "rootLabel": "전체 조직", "rootIconClass": "icon-org-root",
                             "orderingYn": true, "searchYn": true, "treeStyleClass": "tree-compact" } } ] }
            }
            """;

    private static final String FORMVIEW_JSON = """
            {
              "schemaVersion": 1, "archetype": "MGMT_LIST_DETAIL", "stem": "userMgmt", "role": "admin",
              "slots": { "listArea": [
                { "instanceId": "formView_1", "moduleTypeCode": "FORM_VIEW",
                  "props": { "selectionType": "checkbox", "formStyleClass": "form-compact",
                             "fields": [
                               { "name": "userId", "label": "사용자 ID", "type": "text", "requiredYn": true, "styleClass": "fld-id" },
                               { "name": "email", "label": "이메일", "type": "email", "requiredYn": false, "styleClass": "" },
                               { "name": "memo", "label": "메모", "type": "textarea", "requiredYn": false, "styleClass": "" },
                               { "name": "grade", "label": "등급", "type": "select", "requiredYn": false, "styleClass": "" } ] } } ] }
            }
            """;

    /** shell/list 4종 공통 + 해당 뷰 3종 = 7 파일. relativePath -> golden 파일명. */
    private static Map<String, String> viewFiles(String viewFile) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("WEB-INF/views/admin/userMgmt/userMgmt.jsp", "userMgmt.jsp");
        m.put("WEB-INF/views/admin/userMgmt/userMgmtList.jsp", "userMgmtList.jsp");
        m.put("static/js/admin/userMgmt/userMgmtList.js", "userMgmtList.js");
        m.put("static/css/admin/userMgmt/userMgmtList.css", "userMgmtList.css");
        m.put("WEB-INF/views/admin/userMgmt/userMgmtList" + viewFile + ".jsp",
                "userMgmtList" + viewFile + ".jsp");
        m.put("static/js/admin/userMgmt/userMgmtList" + viewFile + ".js",
                "userMgmtList" + viewFile + ".js");
        // P6-2: 뷰 CSS는 공통추출(commonScreenLayout.css) → per-screen 뷰 CSS 미산출(골든 제외).
        return m;
    }

    private static Map<String, String> cardFiles() {
        return viewFiles("CardView");
    }

    private static Map<String, String> treeFiles() {
        return viewFiles("TreeView");
    }

    private static Map<String, String> formFiles() {
        return viewFiles("FormView");
    }

    @TempDir
    Path targetRoot;

    // ------------------------------------------------------------------
    // 스냅샷 비교
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(ViewCase.class)
    void 골든_스냅샷_7종이_현재_산출과_정규화_후_일치한다(ViewCase vc) throws IOException {
        GenResult result = newGenerator(vc.json).generate(10L);
        assertEquals(GenResult.SUCCESS, result.resultCode(),
                vc + " 고정 입력은 SUCCESS 여야 한다: " + result.failReason());

        if (UPDATE_GOLDEN) {
            updateGolden(vc);
            return;
        }

        Path goldenRoot = goldenSourceDir(vc.goldenDir);
        for (Map.Entry<String, String> e : vc.files.entrySet()) {
            String rel = e.getKey();
            Path produced = targetRoot.resolve(rel);
            assertTrue(Files.exists(produced), vc + " 산출 파일 없음: " + rel);

            String actual = normalize(Files.readString(produced, StandardCharsets.UTF_8));
            Path goldenFile = goldenRoot.resolve(e.getValue());
            assertTrue(Files.exists(goldenFile),
                    vc + " 골든 파일 없음(갱신 모드로 생성 필요): " + goldenFile);
            String expected = normalize(Files.readString(goldenFile, StandardCharsets.UTF_8));

            if (!expected.equals(actual)) {
                fail(diagnose(vc + "/" + e.getValue(), expected, actual));
            }
        }
    }

    /** 🔒 골든이 실제 안전 산출물인지 + §8.6 JWorks init 배선을 담는지 매 실행 단언. */
    @ParameterizedTest
    @EnumSource(ViewCase.class)
    void 골든은_안전_산출물이고_JWorks_배선을_담는다(ViewCase vc) throws IOException {
        newGenerator(vc.json).generate(10L);
        Map<String, String> bodies = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : vc.files.entrySet()) {
            Path produced = targetRoot.resolve(e.getKey());
            bodies.put(e.getValue(), normalize(Files.readString(produced, StandardCharsets.UTF_8)));
        }

        for (Map.Entry<String, String> e : bodies.entrySet()) {
            String name = e.getKey();
            String body = e.getValue();
            assertFalse(body.contains("JWORKS"), name + ": JWORKS 배너 유출");
            assertFalse(body.toUpperCase().contains("COPYRIGHT"), name + ": 저작권 배너 유출");
            if (name.endsWith(".jsp")) {
                assertScriptletFree(name, body);
            }
        }

        // jQuery 3.7.1 확정(shell 매니페스트/헤더 경유). 다른 버전 참조 금지.
        String shell = bodies.get("userMgmt.jsp");
        assertFalse(containsOtherJqueryVersion(shell), vc + " shell 에 3.7.1 이외 jQuery 버전 참조");

        // list.js: 화면 네임스페이스 + IIFE.
        String listJs = bodies.get("userMgmtList.js");
        assertTrue(listJs.contains("window.JWorks_JSUserMgmtAdmin"), vc + " listJs 네임스페이스");
        assertTrue(listJs.contains("__defined"), vc + " listJs IIFE 골격");

        // 뷰 JS: 뷰별 네임스페이스 + §8.6 init 배선.
        String viewJs = bodies.get("userMgmtList" + vc.viewSuffix + ".js");
        assertTrue(viewJs.contains("window.JWorks_JSUserMgmtAdmin" + vc.viewSuffix),
                vc + " viewJs 네임스페이스(JWorks_JS{Domain}{Role}" + vc.viewSuffix + ")");
        assertTrue(viewJs.contains("__defined"), vc + " viewJs IIFE 골격");
        assertTrue(viewJs.contains(vc.initWiring), vc + " §8.6 JWorks init 배선: " + vc.initWiring);

        // FORM_VIEW 는 selectionType 배선을 담아야 한다(§8.6).
        if (vc == ViewCase.FORM) {
            assertTrue(viewJs.contains("selectionType:"), "FORM viewJs selectionType 배선");
        }
    }

    // ------------------------------------------------------------------
    // wiring / helpers (P4-7 GoldenSnapshotTest 규칙과 동일)
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

    private static String normalize(String s) {
        return s.replace("\r\n", "\n").replace("\r", "\n");
    }

    private static boolean containsOtherJqueryVersion(String body) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("jquery-(\\d+\\.\\d+\\.\\d+)").matcher(body);
        while (m.find()) {
            if (!"3.7.1".equals(m.group(1))) {
                return true;
            }
        }
        return false;
    }

    private static void assertScriptletFree(String name, String body) {
        assertFalse(body.matches("(?s).*<%[^@\\-].*"), name + ": 스크립트릿(<% ) 발견");
        assertFalse(body.contains("<%="), name + ": 표현식 스크립트릿(<%=) 발견");
        assertFalse(body.contains("<%!"), name + ": 선언 스크립트릿(<%!) 발견");
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
            sb.append("  라인 수가 다름(추가/삭제된 라인). expected=")
                    .append(exp.length).append(", actual=").append(act.length);
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
        return Paths.get("src", "test", "resources",
                goldenDir.replace("/", java.io.File.separator));
    }

    private void updateGolden(ViewCase vc) {
        Path dir = Paths.get("src", "test", "resources",
                vc.goldenDir.replace("/", java.io.File.separator));
        try {
            Files.createDirectories(dir);
            for (Map.Entry<String, String> e : vc.files.entrySet()) {
                Path produced = targetRoot.resolve(e.getKey());
                String body = normalize(Files.readString(produced, StandardCharsets.UTF_8));
                Files.writeString(dir.resolve(e.getValue()), body, StandardCharsets.UTF_8);
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
        System.out.println("[golden-update] " + vc + " wrote " + vc.files.size()
                + " files to " + dir.toAbsolutePath());
    }
}
