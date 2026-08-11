package com.jworks.forge.gen.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
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
 * 🔒 P5-3: TREE_VIEW 뷰 "한 세트" 산출 검증 (계약 §8.2/§8.4/§8.6).
 *
 * <p>AC:
 * <ul>
 *   <li>(a) TREE_VIEW를 listArea에 배치한 화면 생성 시 {@code {stem}ListTreeView} 3종(jsp/js/css)이
 *       산출·유효(스크립트릿 0, 배너 0), commonListTreeView API 배선(§8.6)이 맞물린다.</li>
 *   <li>(b) 🔒 props 악성 문자열({@code <script>}/{@code "}/{@code </script>}/{@code ${x}}/공백 classname)
 *       주입 시 산출물이 이스케이프된 리터럴(HTML {@code &lt;}/JS escape/CSS 토큰 드롭) — 실행/템플릿 인젝션 0.</li>
 * </ul>
 * (c) V4 시드 멱등은 {@link com.jworks.forge.gen.pipeline.TreeViewSeedIdempotencyTest} 참조(파싱 검증).
 */
class TreeViewGenerationTest {

    /** listArea에 TREE_VIEW 정상 배치(설정 props 정상값). */
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

    /** 🔒 TREE_VIEW props 자유문자열에 인젝션 페이로드 심음(계층 props 포함). */
    private static final String MALICIOUS_JSON = ("""
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
            """);

    private ForgeScreenService screenService;
    private ForgeProjectService projectService;
    private ScreenGenerator generator;

    @TempDir
    Path targetRoot;

    @BeforeEach
    void setUp() {
        screenService = mock(ForgeScreenService.class);
        projectService = mock(ForgeProjectService.class);
        var cfg = new CodeGenTemplateConfig().codeGenFreemarkerConfiguration();
        var renderer = new TemplateRenderer(cfg);
        var contextBuilder = new TemplateContextBuilder(new ObjectMapper());
        var pathSafety = new PathSafetyService();
        var fileWriter = new AtomicFileWriter();
        var runtimeSyncer = new RuntimeSyncer(pathSafety, fileWriter);
        var stubGenerator = new StubGenerator(pathSafety, fileWriter);
        generator = new ScreenGenerator(screenService, projectService, contextBuilder, renderer,
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

    private void wire(String json) {
        when(screenService.get(10L)).thenReturn(screen(json));
        when(projectService.get(1L)).thenReturn(project());
    }

    // ------------------------------------------------------------------
    // (a) TREE_VIEW 3종 산출 + API 배선
    // ------------------------------------------------------------------

    @Test
    void TREE_VIEW_배치시_ListTreeView_3종이_산출되고_commonListTreeView배선이_맞물린다() throws IOException {
        wire(TREEVIEW_JSON);

        GenResult result = generator.generate(10L);
        assertEquals(GenResult.SUCCESS, result.resultCode(), result.failReason());

        // 3종 아티팩트가 산출 목록에 포함.
        assertTrue(result.files().stream().anyMatch(f -> f.artifactKey().equals("listTreeView")));
        assertTrue(result.files().stream().anyMatch(f -> f.artifactKey().equals("listTreeViewJs")));
        // P6-2: 뷰 CSS는 공통추출(commonScreenLayout.css) → per-screen CSS 미산출. jsp/js 2종만.

        Path jsp = targetRoot.resolve("WEB-INF/views/admin/userMgmt/userMgmtListTreeView.jsp");
        Path js = targetRoot.resolve("static/js/admin/userMgmt/userMgmtListTreeView.js");
        assertTrue(Files.exists(jsp) && Files.exists(js), "2종 파일 실제 산출");

        // list.jsp가 TREE_VIEW 뷰 본문을 정적 접미사로 include(단일 소스 정합).
        String list = Files.readString(
                targetRoot.resolve("WEB-INF/views/admin/userMgmt/userMgmtList.jsp"), StandardCharsets.UTF_8);
        assertTrue(list.contains("<jsp:include page=\"./userMgmtListTreeView.jsp\" />"),
                "listArea include가 ./userMgmtListTreeView.jsp");

        // JSP: commonListTreeView 타겟 셀렉터/클래스명(§8.6 근거표) + 스크립트릿/배너 0.
        String jspBody = Files.readString(jsp, StandardCharsets.UTF_8);
        assertTrue(jspBody.contains("id=\"tree-view\""), "section#tree-view");
        assertTrue(jspBody.contains("class=\"layout-body\""), ".layout-body(469행)");
        assertTrue(jspBody.contains("section class=\"total\""), "section.total .count(471행)");
        assertTrue(jspBody.contains("class=\"count\""), ".count");
        assertTrue(jspBody.contains("section class=\"search\""), "section.search(139행)");
        assertTrue(jspBody.contains("class=\"search-icon\""), ".search-icon");
        assertFalse(jspBody.contains("<%") && !jspBody.contains("<%@"), "스크립트릿 0(지시어 제외)");
        assertFalse(jspBody.contains("JWORKS"), "배너 0");

        // JS: NS + commonListTreeView.init 배선(§8.6).
        String jsBody = Files.readString(js, StandardCharsets.UTF_8);
        assertTrue(jsBody.contains("window.JWorks_JSUserMgmtAdminTreeView"),
                "NS = JWorks_JS{Domain}{Role}TreeView");
        assertTrue(jsBody.contains("view.__defined"), "IIFE __defined 골격");
        assertTrue(jsBody.contains("JWorks_JSCommonListTreeView.init({"), "번들 런타임 init 배선");
        assertTrue(jsBody.contains("$container:"), "필수 옵션 $container(34행)");
        assertTrue(jsBody.contains("apiInfo:"), "apiInfo(40행)");
        assertTrue(jsBody.contains("parser:"), "apiInfo.parser(494행)");
        assertTrue(jsBody.contains("renderCallback:"), "apiInfo.renderCallback(461행)");
        assertTrue(jsBody.contains("features:"), "features(41행)");
        assertTrue(jsBody.contains("callbacks:"), "callbacks(42행)");
        assertTrue(jsBody.contains("onNodeClick:"), "callbacks.onNodeClick(108행)");
        assertTrue(jsBody.contains("onCheckChange:"), "callbacks.onCheckChange(129행)");
        assertTrue(jsBody.contains("dataMapping:"), "dataMapping(43행)");
        // ordering 활성 → orderingState 전달(50행).
        assertTrue(jsBody.contains("orderingState:"), "orderingState(50행, ordering 활성)");
        assertFalse(jsBody.contains("JWORKS"), "배너 0");
    }

    // ------------------------------------------------------------------
    // (b) 🔒 인젝션: 계층 props 악성 문자열이 이스케이프된 리터럴로만 산출
    // ------------------------------------------------------------------

    @Test
    void TREE_VIEW_props_인젝션페이로드가_이스케이프되어_실행형유출0() throws IOException {
        wire(MALICIOUS_JSON);

        GenResult result = generator.generate(10L);
        assertEquals(GenResult.SUCCESS, result.resultCode(), result.failReason());

        Path jsp = targetRoot.resolve("WEB-INF/views/admin/userMgmt/userMgmtListTreeView.jsp");
        Path js = targetRoot.resolve("static/js/admin/userMgmt/userMgmtListTreeView.js");
        String jspBody = Files.readString(jsp, StandardCharsets.UTF_8);
        String jsBody = Files.readString(js, StandardCharsets.UTF_8);

        for (String body : new String[] { jspBody, jsBody }) {
            assertFalse(body.contains("<script>alert(1)</script>"), "<script> 원문 유출");
            assertFalse(body.contains("<script>evil()</script>"), "<script> 원문 유출");
            assertFalse(body.contains("<script>x</script>"), "<script> 원문 유출");
            assertFalse(body.contains("49"), "${7*7} 템플릿인젝션 평가됨");
            assertFalse(body.contains("81"), "${9*9} 템플릿인젝션 평가됨");
            assertFalse(body.contains("JWORKS"), "배너 유출");
        }

        // JSP: data-* 속성은 htmlAttr로 " 가 &quot; 로 엔티티화 → onerror 주입 차단.
        assertFalse(jspBody.contains("onerror=\"alert(1)\""), "속성 탈출(onerror) 차단");
        assertTrue(jspBody.contains("&lt;script&gt;") || jspBody.contains("&quot;"),
                "HTML 이스케이프 흔적(&lt;/&quot;)");
        // rootIconClass = 'icon a<b> "x"' → cssToken 화이트리스트: 'icon'만 통과, 나머지 드롭.
        assertTrue(jspBody.contains("data-root-icon-class=\"icon\"")
                || jspBody.contains("class=\"tree\""), "cssToken: 유효 토큰만 통과");
        assertFalse(jspBody.contains("a<b>"), "cssToken: 위반 토큰 드롭");

        // JS: </script> 조기종료 방지(jsString이 / → \\/), U+2028 미유출.
        assertFalse(jsBody.contains("</script>"), "JS 문자열에서 </script> 조기종료 유출");
        assertFalse(jsBody.indexOf((char) 0x2028) >= 0, "U+2028 leak(JS)");
    }
}
