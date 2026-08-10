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
 * 🔒 P5-4: FORM_VIEW 뷰 "한 세트" 산출 검증 (계약 §8.2/§8.4/§8.6).
 *
 * <p>AC:
 * <ul>
 *   <li>(a) FORM_VIEW를 listArea에 배치한 화면 생성 시 {@code {stem}ListFormView} 3종(jsp/js/css)이
 *       산출·유효(스크립트릿 0, 배너 0), commonListFormView API 배선(§8.6)이 맞물린다.</li>
 *   <li>(b) 🔒 폼 필드 props 악성 문자열(label/name/type/styleClass에 {@code <script>}/{@code "}/
 *       {@code </script>}/{@code ${x}}/공백 classname/미허용 type)이 이스케이프된 리터럴로만 산출
 *       (HTML {@code &lt;}/JS escape/CSS 토큰 드롭/type 화이트리스트) — 실행/템플릿 인젝션·속성탈출 0.</li>
 * </ul>
 * (c) V4 시드 멱등은 {@link com.jworks.forge.gen.pipeline.FormViewSeedIdempotencyTest} 참조.
 */
class FormViewGenerationTest {

    /** listArea에 FORM_VIEW 정상 배치(설정 props 정상값). */
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

    /** 🔒 FORM_VIEW 폼 필드 props에 인젝션 페이로드 심음(label/name/type/styleClass). */
    private static final String MALICIOUS_JSON = ("""
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
    // (a) FORM_VIEW 3종 산출 + API 배선
    // ------------------------------------------------------------------

    @Test
    void FORM_VIEW_배치시_ListFormView_3종이_산출되고_commonListFormView배선이_맞물린다() throws IOException {
        wire(FORMVIEW_JSON);

        GenResult result = generator.generate(10L);
        assertEquals(GenResult.SUCCESS, result.resultCode(), result.failReason());

        assertTrue(result.files().stream().anyMatch(f -> f.artifactKey().equals("listFormView")));
        assertTrue(result.files().stream().anyMatch(f -> f.artifactKey().equals("listFormViewJs")));
        // P6-2: 뷰 CSS는 공통추출(commonScreenLayout.css) → per-screen CSS 미산출. jsp/js 2종만.

        Path jsp = targetRoot.resolve("WEB-INF/views/admin/userMgmt/userMgmtListFormView.jsp");
        Path js = targetRoot.resolve("static/js/admin/userMgmt/userMgmtListFormView.js");
        assertTrue(Files.exists(jsp) && Files.exists(js), "2종 파일 실제 산출");

        // list.jsp가 FORM_VIEW 뷰 본문을 정적 접미사로 include(단일 소스 정합).
        String list = Files.readString(
                targetRoot.resolve("WEB-INF/views/admin/userMgmt/userMgmtList.jsp"), StandardCharsets.UTF_8);
        assertTrue(list.contains("<jsp:include page=\"./userMgmtListFormView.jsp\" />"),
                "listArea include가 ./userMgmtListFormView.jsp");

        // JSP: commonListFormView 타겟 셀렉터/클래스명(§8.6 근거표) + 폼 필드 + 스크립트릿/배너 0.
        String jspBody = Files.readString(jsp, StandardCharsets.UTF_8);
        assertTrue(jspBody.contains("id=\"form-view\""), "section#form-view");
        assertTrue(jspBody.contains("class=\"layout-body\""), ".layout-body");
        assertTrue(jspBody.contains("id=\"select-all\""), "#select-all(42행, selectionType checkbox)");
        assertTrue(jspBody.contains("class=\"empty-case\""), ".empty-case(76행)");
        // 폼 필드가 라벨 + 입력으로 정적 산출(render 비어있음 → JSP 소관).
        assertTrue(jspBody.contains("사용자 ID"), "field.label htmlText");
        assertTrue(jspBody.contains("name=\"userId\""), "field.name htmlAttr");
        assertTrue(jspBody.contains("type=\"email\""), "type 화이트리스트 매핑(email)");
        assertTrue(jspBody.contains("<textarea"), "textarea 위젯");
        assertTrue(jspBody.contains("<select"), "select 위젯");
        assertTrue(jspBody.contains("required"), "requiredYn → required 속성");
        assertFalse(jspBody.contains("<%") && !jspBody.contains("<%@"), "스크립트릿 0(지시어 제외)");
        assertFalse(jspBody.contains("JWORKS"), "배너 0");

        // JS: NS + commonListFormView.init 배선(§8.6).
        String jsBody = Files.readString(js, StandardCharsets.UTF_8);
        assertTrue(jsBody.contains("window.MagicIAM_JSUserMgmtAdminFormView"),
                "NS = MagicIAM_JS{Domain}{Role}FormView");
        assertTrue(jsBody.contains("view.__defined"), "IIFE __defined 골격");
        assertTrue(jsBody.contains("MagicIAM_JSCommonListFormView.init({"), "번들 런타임 init 배선");
        assertTrue(jsBody.contains("$container:"), "필수 옵션 $container(31행)");
        assertTrue(jsBody.contains("apiInfo:"), "apiInfo(37행)");
        assertTrue(jsBody.contains("renderCallback:"), "apiInfo.renderCallback(124행)");
        assertTrue(jsBody.contains("selectionType:"), "selectionType(38행)");
        assertFalse(jsBody.contains("JWORKS"), "배너 0");
    }

    // ------------------------------------------------------------------
    // (b) 🔒 인젝션: 폼 필드 props 악성 문자열이 이스케이프된 리터럴로만 산출
    // ------------------------------------------------------------------

    @Test
    void FORM_VIEW_폼필드props_인젝션페이로드가_이스케이프되어_실행형유출0() throws IOException {
        wire(MALICIOUS_JSON);

        GenResult result = generator.generate(10L);
        assertEquals(GenResult.SUCCESS, result.resultCode(), result.failReason());

        Path jsp = targetRoot.resolve("WEB-INF/views/admin/userMgmt/userMgmtListFormView.jsp");
        Path js = targetRoot.resolve("static/js/admin/userMgmt/userMgmtListFormView.js");
        String jspBody = Files.readString(jsp, StandardCharsets.UTF_8);
        String jsBody = Files.readString(js, StandardCharsets.UTF_8);

        for (String body : new String[] { jspBody, jsBody }) {
            assertFalse(body.contains("<script>alert(1)</script>"), "<script> 원문 유출");
            assertFalse(body.contains("<script>evil()</script>"), "<script> 원문 유출");
            assertFalse(body.contains("<script>bad()</script>"), "<script> 원문 유출");
            assertFalse(body.contains("<script>x</script>"), "<script> 원문 유출");
            assertFalse(body.contains("49"), "${7*7} 템플릿인젝션 평가됨");
            assertFalse(body.contains("81"), "${9*9} 템플릿인젝션 평가됨");
            assertFalse(body.contains("JWORKS"), "배너 유출");
        }

        // JSP: 속성/텍스트는 htmlAttr/htmlText로 엔티티화 → onerror·속성탈출 차단.
        assertFalse(jspBody.contains("onerror=\"alert(1)\""), "속성 탈출(onerror) 차단");
        assertTrue(jspBody.contains("&lt;script&gt;") || jspBody.contains("&quot;"),
                "HTML 이스케이프 흔적(&lt;/&quot;)");
        // 🔒 input type 화이트리스트: 미허용 type('text"><script>', 'javascript:...')은 기본 text로 수렴.
        //    type 속성엔 오직 허용 리터럴만 나타난다(원문 삽입 0).
        assertFalse(jspBody.contains("type=\"text\"><script>"), "미허용 type 원문 속성탈출 차단");
        assertFalse(jspBody.contains("type=\"javascript:alert(1)\""), "미허용 type 원문 삽입 차단");
        assertTrue(jspBody.contains("type=\"text\""), "미허용 type은 기본 text로 수렴");
        // styleClass/formStyleClass = 'fld a<b> "x"' → cssToken: 유효 토큰만, 위반 드롭.
        assertFalse(jspBody.contains("a<b>"), "cssToken: 위반 토큰 드롭");

        // JS: </script> 조기종료 방지(jsString이 / → \\/), U+2028 미유출.
        assertFalse(jsBody.contains("</script>"), "JS 문자열에서 </script> 조기종료 유출");
        assertFalse(jsBody.indexOf((char) 0x2028) >= 0, "U+2028 leak(JS)");
    }
}
