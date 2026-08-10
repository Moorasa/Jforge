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
 * §13(P8) 인스턴스 레이아웃 크기 산출 검증 — {@code listCss.ftl} 조건부 블록.
 *
 * <p>계약 §13 자기점검 항목을 그대로 고정한다:
 * <ol>
 *   <li>유효 숫자(layoutWidthPct 10~100 / layoutHeightPx 40~2000) → {stem}List.css 에 크기 규칙 산출.</li>
 *   <li>🔒 숫자가 아닌 악성 문자열(예: {@code "50;} body{"}) → <b>0바이트</b>(문자열 미삽입 — 인젝션 차단).</li>
 *   <li>범위 밖 숫자 → 0바이트.</li>
 *   <li>layout 키 부재 → 기존과 바이트 동일(골든 무손상은 전체 스위트의 기존 골든이 함께 보증).</li>
 * </ol>
 */
class LayoutCssGenerationTest {

    @TempDir
    Path rootLayout;
    @TempDir
    Path rootPlain;
    @TempDir
    Path rootEvil;
    @TempDir
    Path rootRange;

    private static final String BASE_SLOTS = """
              "slots": {
                "searchArea": [
                  { "instanceId": "search_1", "moduleTypeCode": "SEARCH_FILTER_BAR",
                    "props": { "keywordYn": true%s } } ],
                "listArea": [
                  { "instanceId": "table_1", "moduleTypeCode": "TABLE_VIEW",
                    "props": { "columns": [ { "name": "userId", "displayName": "ID" } ],
                               "selectMode": "checkbox"%s } } ]
              }
            """;

    private static String definition(String searchExtra, String listExtra) {
        return """
                {
                  "schemaVersion": 1, "archetype": "MGMT_LIST_DETAIL", "stem": "userMgmt", "role": "admin",
                """ + BASE_SLOTS.formatted(searchExtra, listExtra) + "}";
    }

    @Test
    void 유효_숫자_layout_props는_perScreen_CSS로_산출된다() throws IOException {
        GenResult r = generate(rootLayout, definition(
                ", \"layoutWidthPct\": 40", ", \"layoutHeightPx\": 480"));
        assertEquals(GenResult.SUCCESS, r.resultCode());
        String css = read(rootLayout);
        assertTrue(css.contains("#userMgmt-list .search {\n\twidth: 40%;\n}"),
                "searchArea 폭 규칙 산출: \n" + css);
        assertTrue(css.contains("#userMgmt-list .list-area {\n\theight: 480px;\n\toverflow-y: auto;\n}"),
                "listArea 높이 규칙 산출: \n" + css);
    }

    @Test
    void 악성_문자열_layout_값은_한_글자도_산출되지_않는다() throws IOException {
        // 숫자가 아니면 ?is_number 게이트에서 탈락 → layout 없음과 바이트 동일(인젝션 원천 차단).
        GenResult plain = generate(rootPlain, definition("", ""));
        GenResult evil = generate(rootEvil, definition(
                ", \"layoutWidthPct\": \"50;} body{background:red}\"",
                ", \"layoutHeightPx\": \"400px;} *{display:none}\""));
        assertEquals(GenResult.SUCCESS, plain.resultCode());
        assertEquals(GenResult.SUCCESS, evil.resultCode());
        assertEquals(read(rootPlain), read(rootEvil), "악성 문자열이 CSS에 새어 나왔다");
        assertFalse(read(rootEvil).contains("body{"), "인젝션 문자열 유출");
    }

    @Test
    void 범위_밖_숫자는_산출되지_않는다() throws IOException {
        GenResult r = generate(rootRange, definition(
                ", \"layoutWidthPct\": 5", ", \"layoutHeightPx\": 99999"));
        assertEquals(GenResult.SUCCESS, r.resultCode());
        String plain = read(rootRange);
        assertFalse(plain.contains("width: 5%"), "10 미만 폭이 산출됨");
        assertFalse(plain.contains("height: 99999px"), "2000 초과 높이가 산출됨");
        assertFalse(plain.contains("overflow-y"), "범위 밖인데 높이 블록이 산출됨");
    }

    // ---------- 픽스처(RoundTripDeterminismTest 와 동형) ----------

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
        s.setScreenId(10L);
        s.setProjectId(1L);
        s.setStem("userMgmt");
        s.setRoleCode("admin");
        s.setArchetypeCode("MGMT_LIST_DETAIL");
        s.setDefinitionJson(definitionJson);

        ForgeProject p = new ForgeProject();
        p.setProjectId(1L);
        p.setTargetRootPath(targetRoot.toString());
        p.setPackageBase("com.jworks.forge");
        p.setJspBasePath("WEB-INF/views");
        p.setJsBasePath("static/js");
        p.setCssBasePath("static/css");
        p.setRuntimeVer("1.0.0");

        when(screenService.get(10L)).thenReturn(s);
        when(projectService.get(1L)).thenReturn(p);
        return new ScreenGenerator(screenService, projectService, contextBuilder, renderer,
                pathSafety, fileWriter, runtimeSyncer, stubGenerator).generate(10L);
    }

    private String read(Path root) throws IOException {
        return Files.readString(root.resolve("static/css/admin/userMgmt/userMgmtList.css"),
                StandardCharsets.UTF_8).replace("\r\n", "\n").replace("\r", "\n");
    }
}
