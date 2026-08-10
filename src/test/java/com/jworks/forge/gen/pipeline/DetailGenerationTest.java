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
 * 🔒 P5.5a/b 상세영역(Detail 슬롯 + AssociateTabs) 생성·인젝션 검증(계약 §9).
 *
 * <p>{@code MGMT_LIST_DETAIL}에 {@code detailBasic}(DETAIL_BASIC)·{@code detailTabs}(ASSOCIATE_TABS)·
 * {@code detailToolbar}(TOOLBAR)를 배치한 화면을 격리 {@code @TempDir}로 실제 파이프라인 생성해:
 * <ul>
 *   <li><b>정상</b>: Detail 3종({stem}Detail.jsp/.js/.css) 산출 + shell 조건부 include + {@code with-detail}
 *       클래스 + commonSection.js 배선(registEventBasicInfo/registEventAssociateInfo) 확인.</li>
 *   <li><b>무손상/forward-compat</b>: detail 슬롯이 없으면 Detail 3종 미산출 + shell에 Detail include 없음.</li>
 *   <li><b>🔒 인젝션</b>: 상세 props(label/name/type/tabClass/frameId)에 악성값 주입 시 산출물이
 *       이스케이프된 리터럴(실행/템플릿 인젝션 0, {@code </script>} 원문 0, {@code ${x}} 미평가,
 *       미허용 type→text 수렴, cssToken 드롭)임을 단언.</li>
 * </ul>
 */
class DetailGenerationTest {

    @TempDir
    Path targetRoot;

    // ------------------------------------------------------------------
    // 정상 픽스처: TABLE_VIEW(list) + detailBasic + detailTabs + detailToolbar
    // ------------------------------------------------------------------
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

    /** detail 슬롯 전무(list만) — 무손상/forward-compat 검증용. */
    private static final String NO_DETAIL_JSON = """
            {
              "schemaVersion": 1, "archetype": "MGMT_LIST_DETAIL", "stem": "userMgmt", "role": "admin",
              "slots": { "listArea": [
                { "instanceId": "tableView_1", "moduleTypeCode": "TABLE_VIEW",
                  "props": { "columns": [ { "name": "userId", "displayName": "ID", "displayYn": true } ],
                             "selectMode": "none" } } ] }
            }
            """;

    private static final String DETAIL_REL = "WEB-INF/views/admin/userMgmt/userMgmtDetail.jsp";
    private static final String DETAIL_JS_REL = "static/js/admin/userMgmt/userMgmtDetail.js";
    // P6-2: 상세 CSS는 공통추출(commonScreenLayout.css) → per-screen Detail CSS 미산출.
    private static final String DETAIL_CSS_REL = "static/css/admin/userMgmt/userMgmtDetail.css";
    private static final String SHELL_REL = "WEB-INF/views/admin/userMgmt/userMgmt.jsp";

    @Test
    void detail_슬롯_배치시_Detail_2종_산출하고_shell이_조건부_include한다() throws IOException {
        GenResult result = newGenerator(DETAIL_JSON).generate(10L);
        assertEquals(GenResult.SUCCESS, result.resultCode(), "정상 입력은 SUCCESS: " + result.failReason());

        assertTrue(Files.exists(targetRoot.resolve(DETAIL_REL)), "Detail JSP 산출");
        assertTrue(Files.exists(targetRoot.resolve(DETAIL_JS_REL)), "Detail JS 산출");
        assertFalse(Files.exists(targetRoot.resolve(DETAIL_CSS_REL)), "Detail CSS는 공통추출로 미산출");

        String shell = read(SHELL_REL);
        assertTrue(shell.contains("userMgmtDetail.jsp"), "shell이 Detail include");
        assertTrue(shell.contains("with-detail"), "shell page-shell에 with-detail 클래스");
    }

    @Test
    void detail_JSP가_basic_info와_associate_info를_MagicIAM_DOM으로_그린다() throws IOException {
        newGenerator(DETAIL_JSON).generate(10L);
        String jsp = read(DETAIL_REL);

        // detailToolbar(TOOLBAR) 액션바.
        assertTrue(jsp.contains("class=\"detail-toolbar\""), "detail-toolbar 렌더");
        assertTrue(jsp.contains("data-action=\"approve\""), "toolbar 버튼 actionCode");
        // basic-info(commonSection.js registEventBasicInfo DOM 계약).
        assertTrue(jsp.contains("id=\"basic-info\""), "section#basic-info");
        assertTrue(jsp.contains("class=\"button-detail-collapse\""), "접기 버튼");
        assertTrue(jsp.contains("class=\"buttons-edit\""), "수정모드 버튼바");
        assertTrue(jsp.contains("attribute-chip-container"), "속성칩 컨테이너(attributeYn)");
        assertTrue(jsp.contains("data-name=\"userId\""), "basic 필드 data-name");
        // associate-info(commonSection.js registEventAssociateInfo DOM 계약).
        assertTrue(jsp.contains("id=\"associate-info\""), "section#associate-info");
        assertTrue(jsp.contains("class=\"tabs\""), "탭 컨테이너");
        assertTrue(jsp.contains("tab-role"), "탭 클래스 토큰");
        assertTrue(jsp.contains("id=\"roleFrame\""), "iframe frameId");
        assertTrue(jsp.contains("<iframe"), "연관 iframe 골격");
        // 🔒 iframe src(tab.location)은 도메인 배선점 — 산출 시 src 미지정.
        assertFalse(jsp.contains("<iframe title=\"권한\" id=\"roleFrame\" class=\"associate-frame tab-role on\" src="),
                "iframe src는 도메인 배선점(산출 시 미지정)");
    }

    @Test
    void detail_JS가_commonSection_배선하고_네임스페이스_IIFE_골격이다() throws IOException {
        newGenerator(DETAIL_JSON).generate(10L);
        String js = read(DETAIL_JS_REL);

        assertTrue(js.contains("window.MagicIAM_JSUserMgmtAdminDetail"), "Detail 네임스페이스");
        assertTrue(js.contains("__defined"), "IIFE __defined 골격");
        assertTrue(js.contains("MagicIAM_JSCommonSection.registEventBasicInfo({"), "기본정보 배선(88행)");
        assertTrue(js.contains("MagicIAM_JSCommonSection.registEventAssociateInfo({"), "연관탭 배선(247행)");
        assertTrue(js.contains("MagicIAM_JSCommonSection.applyAssociateTabsVisibilityByClass"), "탭 가시성(223행)");
        assertTrue(js.contains("tabClass: \"tab-role\""), "탭 config tabClass");
        assertTrue(js.contains("frameId: \"roleFrame\""), "탭 config frameId");
        assertTrue(js.contains("location: \"\""), "탭 location TODO 배선점(빈 문자열)");
        // 배너/스크립트릿 없음.
        assertFalse(js.contains("JWORKS"), "JWORKS 배너 유출 0");
    }

    @Test
    void detail_슬롯_없으면_Detail_3종_미산출하고_shell에_include_없음() throws IOException {
        GenResult result = newGenerator(NO_DETAIL_JSON).generate(10L);
        assertEquals(GenResult.SUCCESS, result.resultCode(), "list-only도 SUCCESS");

        assertFalse(Files.exists(targetRoot.resolve(DETAIL_REL)), "Detail JSP 미산출");
        assertFalse(Files.exists(targetRoot.resolve(DETAIL_JS_REL)), "Detail JS 미산출");
        assertFalse(Files.exists(targetRoot.resolve(DETAIL_CSS_REL)), "Detail CSS 미산출");

        String shell = read(SHELL_REL);
        assertFalse(shell.contains("userMgmtDetail.jsp"), "shell에 Detail include 없음");
        assertFalse(shell.contains("with-detail"), "shell에 with-detail 클래스 없음");
    }

    // ------------------------------------------------------------------
    // 🔒 인젝션: 상세 props 악성값 → 이스케이프
    // ------------------------------------------------------------------
    private static final String INJECT_JSON = """
            {
              "schemaVersion": 1, "archetype": "MGMT_LIST_DETAIL", "stem": "userMgmt", "role": "admin",
              "slots": {
                "listArea": [
                  { "instanceId": "tableView_1", "moduleTypeCode": "TABLE_VIEW",
                    "props": { "columns": [ { "name": "id", "displayName": "ID", "displayYn": true } ], "selectMode": "none" } } ],
                "detailBasic": [
                  { "instanceId": "basic_1", "moduleTypeCode": "DETAIL_BASIC",
                    "props": { "editableYn": true, "attributeYn": false, "basicStyleClass": "ok\\"evil",
                               "fields": [
                                 { "name": "a\\" onx=1 ", "label": "<script>alert(1)</script>", "type": "javascript:evil", "requiredYn": true, "styleClass": "x y" } ] } } ],
                "detailTabs": [
                  { "instanceId": "tabs_1", "moduleTypeCode": "ASSOCIATE_TABS",
                    "props": { "tabs": [
                      { "label": "</script>${7*7}", "tabClass": "bad tab\\"class", "frameId": "f\\"><b>" } ] } } ]
              }
            }
            """;

    @Test
    void 상세_props_악성값은_산출물에서_이스케이프된다() throws IOException {
        GenResult result = newGenerator(INJECT_JSON).generate(10L);
        assertEquals(GenResult.SUCCESS, result.resultCode(), "인젝션 입력도 렌더 성공(이스케이프됨)");

        String jsp = read(DETAIL_REL);
        String js = read(DETAIL_JS_REL);

        // HTML 텍스트/속성: 원문 <script>/</script> 없음, EL ${..} 미평가(리터럴).
        assertFalse(jsp.contains("<script>alert(1)</script>"), "basic label 원문 스크립트 삽입 0");
        assertFalse(jsp.contains("</script>${7*7}"), "탭 라벨 원문 삽입 0");
        assertFalse(jsp.contains(">49<"), "EL ${7*7} 미평가(리터럴 유지)");
        assertTrue(jsp.contains("&lt;script&gt;") || jsp.contains("&lt;/script&gt;"), "라벨 HTML 이스케이프");
        // 🔒 input type: 미허용(javascript:evil) → text 수렴(속성 탈출 0).
        assertTrue(jsp.contains("type=\"text\""), "미허용 type은 text로 수렴");
        assertFalse(jsp.contains("type=\"javascript:evil\""), "type 원문 삽입 0");
        // 🔒 cssToken: 화이트리스트 위반 토큰(따옴표 포함) 드롭 → basic-info는 class="view-mode"만.
        assertFalse(jsp.contains("ok\"evil"), "cssToken이 따옴표 포함 클래스 토큰 드롭");
        assertTrue(jsp.contains("id=\"basic-info\" class=\"view-mode\""), "위반 styleClass 드롭 후 view-mode만");
        // 속성탈출: name의 따옴표 이스케이프.
        assertFalse(jsp.contains("data-name=\"a\" onx=1 \""), "name 속성탈출 0");

        // JS 문자열: </script> 원문 0, 미평가 ${}, 따옴표 탈출.
        assertFalse(js.contains("</script>"), "JS에 </script> 원문 0");
        assertFalse(js.contains("frameId: \"f\"><b>\""), "frameId 따옴표 탈출(문자열 브레이크 0)");
    }

    // ------------------------------------------------------------------
    // wiring (ListViewGoldenSnapshotTest 규칙과 동일)
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
        return Files.readString(targetRoot.resolve(rel), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").replace("\r", "\n");
    }
}
