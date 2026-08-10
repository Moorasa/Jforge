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
 * 🔒 P4-7 인젝션 회귀 golden — props(자유문자열)에 심은 악성 페이로드가 산출물에서
 * 이스케이프된 리터럴로만 나타남을 <b>golden 스냅샷으로 고정</b>해 회귀를 감지한다.
 *
 * <p>{@link ArchetypeTemplateSetTest} 는 "원문 유출 없음"의 <em>부정</em> 단언(assertFalse)만 한다.
 * 본 테스트는 실제 파이프라인 산출을 golden 으로 <b>양성 고정</b>해, 이스케이프 로직이
 * 미묘하게 바뀌어도(예: 이스케이프 누락/과다) diff 로 즉시 드러나게 한다.
 *
 * <p>페이로드: {@code </script>}, {@code "><img onerror>}, {@code '}, {@code \\},
 * {@code ${...}}(FreeMarker 인터폴레이션), U+2028(라인구분자, JS 문법 파괴).
 *
 * <h2>골든 갱신</h2>
 * {@code mvn -Dtest=InjectionGoldenTest -Dforge.golden.update=true test} 후 diff 리뷰 → 커밋.
 * 갱신 시 반드시 페이로드 원문(태그/인터폴레이션)이 <b>실행형으로 유출되지 않았는지</b> 눈으로 확인.
 */
class InjectionGoldenTest {

    private static final boolean UPDATE_GOLDEN = Boolean.getBoolean("forge.golden.update");
    private static final String GOLDEN_DIR = "golden/injection_mgmtListDetail";

    /**
     * 🔒 인젝션 페이로드를 props 자유문자열(displayName/label/styleClass/actionCode/name/options)에 심음.
     * U+2028(라인구분자)은  로 삽입 — JS 문자열 컨텍스트에서 이스케이프되지 않으면 문법을 깬다.
     */
    private static final String MALICIOUS_JSON = ("""
            {
              "schemaVersion": 1,
              "archetype": "MGMT_LIST_DETAIL",
              "stem": "userMgmt",
              "role": "admin",
              "slots": {
                "searchArea": [
                  { "instanceId": "searchFilterBar_1", "moduleTypeCode": "SEARCH_FILTER_BAR",
                    "props": { "filters": [ { "name": "x\\"><script>alert(1)</script>", "label": "<img src=x onerror=alert(1)>", "options": "a:<b>,c:${7*7}" } ],
                               "keywordYn": true, "dateRangeYn": true } }
                ],
                "listToolbar": [
                  { "instanceId": "toolbar_1", "moduleTypeCode": "TOOLBAR",
                    "props": { "buttons": [
                      { "actionCode": "add\\" onclick=\\"alert(1)", "label": "</script><script>evil()</script>", "styleClass": "btn a<b> \\"x\\"" } ] } }
                ],
                "listArea": [
                  { "instanceId": "tableView_1", "moduleTypeCode": "TABLE_VIEW",
                    "props": { "columns": [
                      { "name": "u\\"></td><script>x</script>", "displayName": "line\\u2028sep </script><script>alert(1)</script> ${9*9}", "displayYn": true, "sortYn": true } ],
                      "selectMode": "checkbox", "pagingYn": true, "excelYn": true, "csvYn": true } }
                ]
              }
            }
            """);

    private static final Map<String, String> GOLDEN_FILES = new LinkedHashMap<>();
    static {
        GOLDEN_FILES.put("WEB-INF/views/admin/userMgmt/userMgmtList.jsp", "userMgmtList.jsp");
        GOLDEN_FILES.put("WEB-INF/views/admin/userMgmt/userMgmtListTableView.jsp",
                "userMgmtListTableView.jsp");
        GOLDEN_FILES.put("static/js/admin/userMgmt/userMgmtListTableView.js",
                "userMgmtListTableView.js");
    }

    // ------------------------------------------------------------------
    // 🔒 P5-6: 뷰타입별(CARD/TREE/FORM) 인젝션 회귀 golden
    // ------------------------------------------------------------------

    /** 🔒 CARD_VIEW props 자유문자열에 인젝션 페이로드 심음(CardViewGenerationTest 페이로드와 동일). */
    private static final String CARD_MALICIOUS_JSON = """
            {
              "schemaVersion": 1, "archetype": "MGMT_LIST_DETAIL", "stem": "userMgmt", "role": "admin",
              "slots": { "listArea": [
                { "instanceId": "cardView_1", "moduleTypeCode": "CARD_VIEW",
                  "props": {
                    "titleField": "t\\"></section><script>alert(1)</script>",
                    "subtitleField": "line\\u2028sep </script><script>evil()</script> ${7*7}",
                    "imageField": "i\\" onerror=\\"alert(1)",
                    "columns": [ { "name": "c\\"></td><script>x</script>", "displayName": "</script><script>alert(1)</script> ${9*9}", "displayYn": true, "sortYn": true } ],
                    "selectMode": "checkbox", "pagingYn": true,
                    "cardStyleClass": "card a<b> \\"x\\"" } } ] }
            }
            """;

    /** 🔒 TREE_VIEW props 자유문자열에 인젝션 페이로드 심음(TreeViewGenerationTest 페이로드와 동일). */
    private static final String TREE_MALICIOUS_JSON = """
            {
              "schemaVersion": 1, "archetype": "MGMT_LIST_DETAIL", "stem": "userMgmt", "role": "admin",
              "slots": { "listArea": [
                { "instanceId": "treeView_1", "moduleTypeCode": "TREE_VIEW",
                  "props": {
                    "labelField": "l\\"></section><script>alert(1)</script>",
                    "idField": "line\\u2028sep </script><script>evil()</script> ${7*7}",
                    "parentField": "p\\" onerror=\\"alert(1)",
                    "iconField": "i\\"></section><script>x</script>",
                    "rootLabel": "</script><script>alert(1)</script> ${9*9}",
                    "rootIconClass": "icon a<b> \\"x\\"",
                    "selectMode": "checkbox", "orderingYn": true, "searchYn": true,
                    "treeStyleClass": "tree a<b> \\"y\\"" } } ] }
            }
            """;

    /** 🔒 FORM_VIEW 폼 필드 props에 인젝션 페이로드 심음(FormViewGenerationTest 페이로드와 동일, 미허용 type 포함). */
    private static final String FORM_MALICIOUS_JSON = """
            {
              "schemaVersion": 1, "archetype": "MGMT_LIST_DETAIL", "stem": "userMgmt", "role": "admin",
              "slots": { "listArea": [
                { "instanceId": "formView_1", "moduleTypeCode": "FORM_VIEW",
                  "props": {
                    "selectionType": "checkbox",
                    "formStyleClass": "form a<b> \\"z\\"",
                    "fields": [
                      { "name": "n\\"></section><script>alert(1)</script>",
                        "label": "</label><script>evil()</script> ${7*7}",
                        "type": "text\\"><script>x</script>",
                        "requiredYn": true,
                        "styleClass": "fld a<b> \\"x\\"" },
                      { "name": "l\\u2028sep </script><script>bad()</script> ${9*9}",
                        "label": "p\\" onerror=\\"alert(1)",
                        "type": "javascript:alert(1)",
                        "requiredYn": false,
                        "styleClass": "ok-cls" } ] } } ] }
            }
            """;

    /** 뷰타입별 인젝션 golden 케이스: golden 디렉터리 + 악성 입력 + 산출 파일 맵(뷰 jsp/js). */
    enum InjectionViewCase {
        CARD("golden/injection_cardView", CARD_MALICIOUS_JSON, "CardView"),
        TREE("golden/injection_treeView", TREE_MALICIOUS_JSON, "TreeView"),
        FORM("golden/injection_formView", FORM_MALICIOUS_JSON, "FormView");

        final String goldenDir;
        final String json;
        final String viewSuffix;
        final Map<String, String> files;

        InjectionViewCase(String goldenDir, String json, String viewSuffix) {
            this.goldenDir = goldenDir;
            this.json = json;
            this.viewSuffix = viewSuffix;
            Map<String, String> m = new LinkedHashMap<>();
            m.put("WEB-INF/views/admin/userMgmt/userMgmtList" + viewSuffix + ".jsp",
                    "userMgmtList" + viewSuffix + ".jsp");
            m.put("static/js/admin/userMgmt/userMgmtList" + viewSuffix + ".js",
                    "userMgmtList" + viewSuffix + ".js");
            this.files = m;
        }
    }

    @ParameterizedTest
    @EnumSource(InjectionViewCase.class)
    void 뷰타입_인젝션_페이로드_산출물이_이스케이프된_golden과_일치한다(InjectionViewCase vc)
            throws IOException {
        GenResult result = newGenerator(vc.json).generate(10L);
        assertEquals(GenResult.SUCCESS, result.resultCode(),
                vc + " 산출은 성공(이스케이프 후 정상 렌더): " + result.failReason());

        if (UPDATE_GOLDEN) {
            updateViewGolden(vc);
            return;
        }

        Path goldenRoot = goldenSourceDir(vc.goldenDir);
        for (Map.Entry<String, String> e : vc.files.entrySet()) {
            String actual = normalize(Files.readString(
                    targetRoot.resolve(e.getKey()), StandardCharsets.UTF_8));
            Path goldenFile = goldenRoot.resolve(e.getValue());
            assertTrue(Files.exists(goldenFile), vc + " 인젝션 골든 없음: " + goldenFile);
            String expected = normalize(Files.readString(goldenFile, StandardCharsets.UTF_8));
            if (!expected.equals(actual)) {
                fail(diagnose(vc + "/" + e.getValue(), expected, actual));
            }
        }
    }

    /** 🔒 뷰타입 golden 자체가 실행형 페이로드를 담지 않음을 매 실행 재단언. */
    @ParameterizedTest
    @EnumSource(InjectionViewCase.class)
    void 뷰타입_golden은_실행형_페이로드를_담지_않는다(InjectionViewCase vc) throws IOException {
        newGenerator(vc.json).generate(10L);
        for (Map.Entry<String, String> e : vc.files.entrySet()) {
            String body = normalize(Files.readString(
                    targetRoot.resolve(e.getKey()), StandardCharsets.UTF_8));
            String name = vc + "/" + e.getValue();
            assertFalse(body.contains("<script>alert(1)</script>"), name + ": <script> 원문 유출");
            assertFalse(body.contains("<script>evil()</script>"), name + ": <script> 원문 유출");
            assertFalse(body.contains("<script>bad()</script>"), name + ": <script> 원문 유출");
            assertFalse(body.contains("<script>x</script>"), name + ": <script> 원문 유출");
            assertFalse(body.contains("onerror=\"alert(1)\""), name + ": onerror 속성탈출 유출");
            assertFalse(body.contains("49"), name + ": ${7*7} 템플릿인젝션 평가됨");
            assertFalse(body.contains("81"), name + ": ${9*9} 템플릿인젝션 평가됨");
            // 🔒 §18 — 위 두 줄은 **생성 시점(FreeMarker)** 평가만 본다. 생성물은 JSP 이므로
            // 원시 `${`/`#{` 가 남으면 **타겟 톰캣이 렌더할 때** EL 이 평가한다. JSP 산출에는
            // EL 시작 시퀀스가 한 개도 남으면 안 된다(템플릿 자신의 `${ctx}` 는 htmlText 를
            // 거치지 않으므로 아래 검사에서 제외 — 산출 JSP 의 정적 리터럴이다).
            if (e.getValue().endsWith(".jsp")) {
                assertFalse(body.replace("${ctx}", "").replace("${pageContext.request.contextPath}", "")
                        .contains("${"), name + ": 원시 EL 시퀀스 ${ 유출");
                assertFalse(body.contains("#{"), name + ": 원시 지연 EL 시퀀스 #{ 유출");
            }
            assertFalse(body.contains("a<b>"), name + ": cssToken 위반토큰 유출");
            if (e.getValue().endsWith(".js")) {
                assertFalse(body.indexOf((char) 0x2028) >= 0, name + ": U+2028 leak(JS)");
                assertFalse(body.contains("</script>"), name + ": JS 문자열에서 </script> 유출");
            }
            assertFalse(body.contains("JWORKS"), name + ": 배너 유출");
        }
    }

    private void updateViewGolden(InjectionViewCase vc) {
        Path dir = Paths.get("src", "test", "resources",
                vc.goldenDir.replace("/", java.io.File.separator));
        try {
            Files.createDirectories(dir);
            for (Map.Entry<String, String> e : vc.files.entrySet()) {
                String body = normalize(Files.readString(
                        targetRoot.resolve(e.getKey()), StandardCharsets.UTF_8));
                Files.writeString(dir.resolve(e.getValue()), body, StandardCharsets.UTF_8);
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
        System.out.println("[golden-update] injection " + vc + " -> " + dir.toAbsolutePath());
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

    @TempDir
    Path targetRoot;

    private ScreenGenerator newGenerator() {
        return newGenerator(MALICIOUS_JSON);
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

    @Test
    void 인젝션_페이로드_산출물이_이스케이프된_golden과_일치한다() throws IOException {
        GenResult result = newGenerator().generate(10L);
        assertEquals(GenResult.SUCCESS, result.resultCode(), "산출은 성공(이스케이프 후 정상 렌더)");

        if (UPDATE_GOLDEN) {
            updateGolden();
            return;
        }

        Path goldenRoot = goldenSourceDir();
        for (Map.Entry<String, String> e : GOLDEN_FILES.entrySet()) {
            String actual = normalize(Files.readString(
                    targetRoot.resolve(e.getKey()), StandardCharsets.UTF_8));
            Path goldenFile = goldenRoot.resolve(e.getValue());
            assertTrue(Files.exists(goldenFile), "인젝션 골든 없음: " + goldenFile);
            String expected = normalize(Files.readString(goldenFile, StandardCharsets.UTF_8));
            if (!expected.equals(actual)) {
                fail(diagnose(e.getValue(), expected, actual));
            }
        }
    }

    /** 🔒 golden 자체가 실행형 페이로드를 담고 있지 않음을 매 실행 재단언(golden 무결성). */
    @Test
    void golden은_실행형_페이로드를_담지_않는다() throws IOException {
        newGenerator().generate(10L);
        for (Map.Entry<String, String> e : GOLDEN_FILES.entrySet()) {
            String body = normalize(Files.readString(
                    targetRoot.resolve(e.getKey()), StandardCharsets.UTF_8));
            String name = e.getValue();
            assertFalse(body.contains("<script>alert(1)</script>"), name + ": <script> 원문 유출");
            assertFalse(body.contains("<script>evil()</script>"), name + ": <script> 원문 유출");
            assertFalse(body.contains("<script>x</script>"), name + ": <script> 원문 유출");
            assertFalse(body.contains("<img src=x onerror=alert(1)>"), name + ": onerror 원문 유출");
            assertFalse(body.contains("49"), name + ": ${7*7} 템플릿인젝션 평가됨");
            assertFalse(body.contains("81"), name + ": ${9*9} 템플릿인젝션 평가됨");
            // 🔒 §18 — 위 두 줄은 **생성 시점(FreeMarker)** 평가만 본다. 생성물은 JSP 이므로
            // 원시 `${`/`#{` 가 남으면 **타겟 톰캣이 렌더할 때** EL 이 평가한다. JSP 산출에는
            // EL 시작 시퀀스가 한 개도 남으면 안 된다(템플릿 자신의 `${ctx}` 는 htmlText 를
            // 거치지 않으므로 아래 검사에서 제외 — 산출 JSP 의 정적 리터럴이다).
            if (e.getValue().endsWith(".jsp")) {
                assertFalse(body.replace("${ctx}", "").replace("${pageContext.request.contextPath}", "")
                        .contains("${"), name + ": 원시 EL 시퀀스 ${ 유출");
                assertFalse(body.contains("#{"), name + ": 원시 지연 EL 시퀀스 #{ 유출");
            }
            // 원시 U+2028 이 JS/JSP 산출에 그대로 남으면 안 됨(JS 문법 파괴).
            if (name.endsWith(".js")) {
                assertFalse(body.indexOf((char) 0x2028) >= 0, name + ": U+2028 leak(JS)");
                assertFalse(body.contains("</script>"), name + ": JS 문자열에서 </script> 유출");
            }
            assertFalse(body.contains("JWORKS"), name + ": 배너 유출");
        }
    }

    // ------------------------------------------------------------------
    // helpers (GoldenSnapshotTest 와 동일 정규화/진단 규칙)
    // ------------------------------------------------------------------

    private static String normalize(String s) {
        return s.replace("\r\n", "\n").replace("\r", "\n");
    }

    private static String diagnose(String fileName, String expected, String actual) {
        String[] exp = expected.split("\n", -1);
        String[] act = actual.split("\n", -1);
        int n = Math.min(exp.length, act.length);
        StringBuilder sb = new StringBuilder();
        sb.append("인젝션 골든 불일치: ").append(fileName).append('\n');
        for (int i = 0; i < n; i++) {
            if (!exp[i].equals(act[i])) {
                sb.append("  first diff at line ").append(i + 1).append(":\n");
                sb.append("    expected: ").append(exp[i]).append('\n');
                sb.append("    actual  : ").append(act[i]).append('\n');
                return sb.toString();
            }
        }
        if (exp.length != act.length) {
            sb.append("  라인 수 다름 expected=").append(exp.length)
                    .append(" actual=").append(act.length);
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
        return Paths.get("src", "test", "resources", GOLDEN_DIR);
    }

    private void updateGolden() {
        Path dir = Paths.get("src", "test", "resources", GOLDEN_DIR);
        try {
            Files.createDirectories(dir);
            for (Map.Entry<String, String> e : GOLDEN_FILES.entrySet()) {
                String body = normalize(Files.readString(
                        targetRoot.resolve(e.getKey()), StandardCharsets.UTF_8));
                Files.writeString(dir.resolve(e.getValue()), body, StandardCharsets.UTF_8);
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
        System.out.println("[golden-update] injection golden -> " + dir.toAbsolutePath());
    }
}
