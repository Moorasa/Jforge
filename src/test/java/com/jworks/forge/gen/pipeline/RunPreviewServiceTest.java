package com.jworks.forge.gen.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jworks.forge.common.web.NotFoundException;
import com.jworks.forge.gen.context.TemplateContextBuilder;
import com.jworks.forge.gen.safety.PathSafetyService;
import com.jworks.forge.gen.template.CodeGenTemplateConfig;
import com.jworks.forge.gen.template.TemplateRenderer;
import com.jworks.forge.project.domain.ForgeProject;
import com.jworks.forge.project.service.ForgeProjectService;
import com.jworks.forge.screen.domain.ForgeScreen;
import com.jworks.forge.screen.service.ForgeScreenService;

/**
 * P9 실행 미리보기({@link RunPreviewService}) 검증.
 *
 * <p>고정 계약: (1) 생성 JSP 의 시블링 include 가 본문에 인라인 해소되고 JSP 지시자/주석/c:set/
 * {@code ${ctx}} 원문이 남지 않는다, (2) per-screen 자산 참조(script src)는 본문에서 제거되고
 * asset 키 목록으로 분리된다, (3) asset 은 정적 artifactKey 화이트리스트로만 서빙(JSP/미등록 404),
 * (4) 전 과정 파일시스템 접근 0(디스크에 아무것도 만들지 않음 — 메모리 렌더).
 */
class RunPreviewServiceTest {

    @TempDir
    Path unusedRoot; // 프로젝트 메타 채움용 — 실제 접근 없음

    /** 풀조합(검색+툴바+테이블+상세기본+연관탭) DEFINITION — GenPlannerTest 와 동형. */
    private static final String DEFINITION = """
            {
              "schemaVersion": 1, "archetype": "MGMT_LIST_DETAIL", "stem": "userMgmt", "role": "admin",
              "slots": {
                "searchArea": [
                  { "instanceId": "search_1", "moduleTypeCode": "SEARCH_FILTER_BAR",
                    "props": { "filters": [ { "name": "status", "label": "상태", "options": "A:활성,I:비활성" } ],
                               "keywordYn": true, "dateRangeYn": true } } ],
                "listToolbar": [
                  { "instanceId": "toolbar_1", "moduleTypeCode": "TOOLBAR",
                    "props": { "buttons": [ { "actionCode": "add", "label": "추가", "styleClass": "btn-primary" } ] } } ],
                "listArea": [
                  { "instanceId": "table_1", "moduleTypeCode": "TABLE_VIEW",
                    "props": { "columns": [ { "name": "userId", "displayName": "ID", "displayYn": true, "sortYn": true } ],
                               "selectMode": "checkbox", "pagingYn": true } } ],
                "detailBasic": [
                  { "instanceId": "basic_1", "moduleTypeCode": "DETAIL_BASIC",
                    "props": { "editableYn": true, "attributeYn": true, "basicStyleClass": "basic-compact",
                               "fields": [ { "name": "userId", "label": "사용자 ID", "type": "text", "requiredYn": true, "styleClass": "fld-id" } ] } } ],
                "detailTabs": [
                  { "instanceId": "tabs_1", "moduleTypeCode": "ASSOCIATE_TABS",
                    "props": { "tabs": [ { "label": "권한", "tabClass": "tab-role", "frameId": "roleFrame" } ] } } ]
              }
            }
            """;

    private RunPreviewService service;

    @BeforeEach
    void setUp() {
        ForgeScreenService screenService = mock(ForgeScreenService.class);
        ForgeProjectService projectService = mock(ForgeProjectService.class);
        var contextBuilder = new TemplateContextBuilder(new ObjectMapper());
        var renderer = new TemplateRenderer(new CodeGenTemplateConfig().codeGenFreemarkerConfiguration());
        var pathSafety = new PathSafetyService();
        var stubGenerator = new StubGenerator(pathSafety, new AtomicFileWriter());
        var planner = new GenPlanner(screenService, projectService, contextBuilder, pathSafety, stubGenerator,
                mock(com.jworks.forge.gen.hist.GenHistMapper.class), new ObjectMapper());

        ForgeScreen s = new ForgeScreen();
        s.setScreenId(10L);
        s.setProjectId(1L);
        s.setScreenName("사용자 관리");
        s.setStem("userMgmt");
        s.setRoleCode("admin");
        s.setArchetypeCode("MGMT_LIST_DETAIL");
        s.setDefinitionJson(DEFINITION);

        ForgeProject p = new ForgeProject();
        p.setProjectId(1L);
        p.setTargetRootPath(unusedRoot.toString());
        p.setPackageBase("com.jworks.forge");
        p.setJspBasePath("WEB-INF/views");
        p.setJsBasePath("static/js");
        p.setCssBasePath("static/css");
        p.setRuntimeVer("1.0.0");

        when(screenService.get(10L)).thenReturn(s);
        when(projectService.get(1L)).thenReturn(p);
        service = new RunPreviewService(screenService, projectService, contextBuilder, renderer, planner);
    }

    @Test
    void 본문은_include가_해소된_정적_HTML이고_JSP_잔재가_없다() {
        RunPreviewService.RunPreview p = service.build(10L, "/forge");

        assertEquals("사용자 관리", p.screenName());
        assertEquals("userMgmt", p.stem());
        String body = p.bodyHtml();

        // shell 컨테이너 + include 로 들어온 list/view/detail 마크업이 한 문서에 인라인.
        assertTrue(body.contains("userMgmt-shell"), "shell 컨테이너");
        assertTrue(body.contains("userMgmt-list"), "list include 해소");
        assertTrue(body.contains("table-view"), "뷰 include 해소");
        assertTrue(body.contains("basic-info"), "detail include 해소");

        // JSP 잔재 0.
        assertFalse(body.contains("<jsp:include"), "include 잔재");
        assertFalse(body.contains("<%@"), "지시자 잔재");
        assertFalse(body.contains("<%--"), "JSP 주석 잔재");
        assertFalse(body.contains("<c:set"), "c:set 잔재");
        assertFalse(body.contains("${ctx}"), "ctx EL 잔재");
        // per-screen 자산 참조는 본문에서 제거(래퍼가 asset 으로 주입).
        assertFalse(body.contains("<script"), "본문 내 script src 잔재");
    }

    @Test
    void 자산_키는_CSS_JS_로_분리되고_asset_으로_렌더된다() {
        RunPreviewService.RunPreview p = service.build(10L, "");
        assertTrue(p.cssKeys().contains("listCss"), "per-screen CSS 키: " + p.cssKeys());
        assertTrue(p.jsKeys().contains("listJs"), "list JS 키: " + p.jsKeys());
        assertTrue(p.jsKeys().contains("listTableViewJs"), "뷰 JS 키: " + p.jsKeys());
        assertTrue(p.jsKeys().contains("detailJs"), "detail JS 키: " + p.jsKeys());

        RunPreviewService.PreviewAsset js = service.asset(10L, "listJs");
        assertEquals("js", js.ext());
        // List.js 네임스페이스는 MagicIAM_JS{Domain}{Role}(stem 대문자화) — 골든과 동일 규칙.
        assertTrue(js.content().contains("MagicIAM_JSUserMgmtAdmin"), "렌더된 JS 내용: " + js.content());

        RunPreviewService.PreviewAsset css = service.asset(10L, "listCss");
        assertEquals("css", css.ext());
        assertTrue(css.content().contains("#userMgmt-list"), "렌더된 CSS 내용");
    }

    @Test
    void asset_화이트리스트_밖과_JSP_아티팩트는_404() {
        assertThrows(NotFoundException.class, () -> service.asset(10L, "shell"), "JSP 는 asset 금지");
        assertThrows(NotFoundException.class, () -> service.asset(10L, "no-such-key"));
        assertThrows(NotFoundException.class, () -> service.asset(10L, "../etc/passwd"));
    }
}
