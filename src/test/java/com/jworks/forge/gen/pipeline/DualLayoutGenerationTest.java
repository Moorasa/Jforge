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
 * 🔒 P5-5c DUAL_LAYOUT(좌우 2단) 아키타입 생성·인젝션 검증(계약 §10).
 *
 * <p>신규 아키타입 DUAL_LAYOUT을 격리 {@code @TempDir}로 실제 파이프라인 생성해:
 * <ul>
 *   <li><b>정상</b>: shell/js/css 3종({stem}.jsp/.js/.css) 산출 + body.dual-layout + #dual-layout-area
 *       (.layout-left>iframe / .layout-middle.resizer / .layout-right>iframe) commonSection.js DOM 계약 +
 *       postMessageEventListener 배선.</li>
 *   <li><b>fallback frameId</b>: frameId props 없으면 정적 fallback id(dual-left-frame-0 등).</li>
 *   <li><b>🔒 인젝션</b>: LAYOUT_FRAME props(frameId/title/paneClass) 악성값이 이스케이프됨(속성탈출 0,
 *       cssToken 드롭, iframe src 미산출).</li>
 * </ul>
 */
class DualLayoutGenerationTest {

    @TempDir
    Path targetRoot;

    private static final String DUAL_JSON = """
            {
              "schemaVersion": 1, "archetype": "DUAL_LAYOUT", "stem": "orgDual", "role": "admin",
              "slots": {
                "leftArea": [
                  { "instanceId": "left_1", "moduleTypeCode": "LAYOUT_FRAME",
                    "props": { "frameId": "leftListFrame", "title": "조직 목록", "paneClass": "pane-list" } } ],
                "rightArea": [
                  { "instanceId": "right_1", "moduleTypeCode": "LAYOUT_FRAME",
                    "props": { "title": "조직 상세" } } ]
              }
            }
            """;

    private static final String SHELL_REL = "WEB-INF/views/admin/orgDual/orgDual.jsp";
    private static final String JS_REL = "static/js/admin/orgDual/orgDual.js";
    // P6-2: 듀얼 CSS는 공통추출(commonScreenLayout.css) → per-screen 듀얼 CSS 미산출.
    private static final String CSS_REL = "static/css/admin/orgDual/orgDual.css";

    @Test
    void DUAL_LAYOUT_생성시_shell_js_2종을_산출한다() throws IOException {
        GenResult result = newGenerator(DUAL_JSON).generate(10L);
        assertEquals(GenResult.SUCCESS, result.resultCode(), "정상 입력은 SUCCESS: " + result.failReason());
        assertTrue(Files.exists(targetRoot.resolve(SHELL_REL)), "shell JSP 산출");
        assertTrue(Files.exists(targetRoot.resolve(JS_REL)), "shell JS 산출");
        assertFalse(Files.exists(targetRoot.resolve(CSS_REL)), "듀얼 CSS는 공통추출로 미산출");
    }

    @Test
    void shell이_commonSection_dual_DOM계약을_MagicIAM대로_그린다() throws IOException {
        newGenerator(DUAL_JSON).generate(10L);
        String jsp = read(SHELL_REL);

        assertTrue(jsp.contains("<body class=\"dual-layout\">"), "body.dual-layout");
        assertTrue(jsp.contains("id=\"dual-layout-area\""), "#dual-layout-area");
        assertTrue(jsp.contains("class=\"layout-left\""), ".layout-left");
        assertTrue(jsp.contains("class=\"layout-middle resizer\""), ".layout-middle.resizer");
        assertTrue(jsp.contains("class=\"resizer-bar\""), ".resizer-bar");
        assertTrue(jsp.contains("class=\"collapse-left\""), ".collapse-left");
        assertTrue(jsp.contains("class=\"collapse-right\""), ".collapse-right");
        assertTrue(jsp.contains("class=\"expand\""), ".expand");
        assertTrue(jsp.contains("class=\"layout-right\""), ".layout-right");
        // 좌/우 iframe(직계자식) + frameId/title 배선.
        assertTrue(jsp.contains("id=\"leftListFrame\""), "좌측 iframe frameId");
        assertTrue(jsp.contains("title=\"조직 목록\""), "좌측 iframe title");
        assertTrue(jsp.contains("data-module=\"LAYOUT_FRAME\""), "패인 moduleTypeCode 힌트");
        // fallback frameId(우측 frameId 미지정).
        assertTrue(jsp.contains("id=\"dual-right-frame-0\""), "우측 fallback frameId");
        // 🔒 iframe src은 도메인 배선점 — 산출 시 미지정.
        assertFalse(jsp.contains("<iframe") && jsp.matches("(?s).*<iframe[^>]*\\ssrc=.*"), "iframe src 미산출(도메인 배선점)");
    }

    @Test
    void shell_JS가_commonSection_postMessage를_배선하고_NS_IIFE_골격이다() throws IOException {
        newGenerator(DUAL_JSON).generate(10L);
        String js = read(JS_REL);
        assertTrue(js.contains("window.MagicIAM_JSOrgDualAdmin"), "DUAL NS(MagicIAM_JS{Domain}{Role})");
        assertTrue(js.contains("__defined"), "IIFE __defined 골격");
        assertTrue(js.contains("MagicIAM_JSCommonSection.postMessageEventListener"), "프레임 동기화 배선(25행)");
        assertFalse(js.contains("JWORKS"), "JWORKS 배너 0");
    }

    // ------------------------------------------------------------------
    // 🔒 인젝션
    // ------------------------------------------------------------------
    private static final String INJECT_JSON = """
            {
              "schemaVersion": 1, "archetype": "DUAL_LAYOUT", "stem": "orgDual", "role": "admin",
              "slots": {
                "leftArea": [
                  { "instanceId": "left_1", "moduleTypeCode": "LAYOUT_FRAME",
                    "props": { "frameId": "f\\"><script>alert(1)</script>", "title": "<b>x</b>${7*7}", "paneClass": "ok\\"evil" } } ],
                "rightArea": []
              }
            }
            """;

    @Test
    void LAYOUT_FRAME_props_악성값은_이스케이프된다() throws IOException {
        GenResult result = newGenerator(INJECT_JSON).generate(10L);
        assertEquals(GenResult.SUCCESS, result.resultCode(), "인젝션 입력도 렌더 성공(이스케이프됨)");
        String jsp = read(SHELL_REL);

        // 속성탈출: frameId/title 따옴표·꺾쇠 이스케이프, 원문 <script> 삽입 0.
        assertFalse(jsp.contains("<script>alert(1)</script>"), "frameId 원문 스크립트 삽입 0");
        assertFalse(jsp.contains("id=\"f\"><script>"), "frameId 속성탈출 0");
        assertFalse(jsp.contains("<b>x</b>"), "title 원문 태그 삽입 0");
        assertFalse(jsp.contains(">49<"), "EL ${7*7} 미평가");
        // cssToken: 따옴표 포함 paneClass 드롭.
        assertFalse(jsp.contains("ok\"evil"), "paneClass cssToken 위반 토큰 드롭");
        // iframe src 미산출.
        assertFalse(jsp.matches("(?s).*<iframe[^>]*\\ssrc=.*"), "iframe src 미산출");
    }

    // ------------------------------------------------------------------
    // wiring
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
        return Files.readString(targetRoot.resolve(rel), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").replace("\r", "\n");
    }
}
