package com.jworks.forge.gen.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
 * P6-3 round-trip(재오픈) 무손상 검증 — 백엔드 층.
 *
 * <p>스튜디오 재오픈 자체는 P3에서 이미 구현됨: {@code studioApp.loadScreen()}이
 * {@code GET /api/screens/{id}}의 {@code definitionJson}(@JsonRawValue 원문)을 캔버스로 하이드레이트하고,
 * 저장은 {@code PUT /api/screens/{id}/definition}이 <b>raw String으로 무가공 영속</b>한다(Jackson 재직렬화 없음).
 * 즉 <b>저장→재오픈 왕복은 DEFINITION_JSON 바이트를 보존</b>한다.
 *
 * <p>이 테스트는 그 위에서 <b>"재오픈→재생성이 결정적(무손상)"</b>임을 <b>풀조합(전 슬롯) 화면</b>으로 고정한다:
 * 동일 DEFINITION_JSON(=저장소가 원문 그대로 돌려주는 값)으로 <b>두 번 독립 생성</b>하면 산출 10종이
 * <b>바이트 동일</b>해야 한다(숨은 상태·비결정성 0). GoldenSnapshotTest의 TABLE_VIEW "2회 생성 동일"을
 * 풀조합으로 확장한 것이다.
 */
class RoundTripDeterminismTest {

    @TempDir
    Path root1;
    @TempDir
    Path root2;

    /** 풀조합(6슬롯) DEFINITION — FullCombinationGoldenSnapshotTest CLEAN과 동일 의미(재오픈 대상). */
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

    private static final List<String> FILES = List.of(
            "WEB-INF/views/admin/userMgmt/userMgmt.jsp",
            "WEB-INF/views/admin/userMgmt/userMgmtList.jsp",
            "static/js/admin/userMgmt/userMgmtList.js",
            "static/css/admin/userMgmt/userMgmtList.css",
            "WEB-INF/views/admin/userMgmt/userMgmtListTableView.jsp",
            "static/js/admin/userMgmt/userMgmtListTableView.js",
            "WEB-INF/views/admin/userMgmt/userMgmtDetail.jsp",
            "static/js/admin/userMgmt/userMgmtDetail.js");
            // P6-2: 뷰/상세 CSS는 공통추출(commonScreenLayout.css) → per-screen CSS 미산출.

    @Test
    void 재오픈_재생성은_풀조합_화면에서_바이트_무손상이다() throws IOException {
        // 1차 생성(최초 편집 후 생성).
        GenResult r1 = newGenerator(root1).generate(10L);
        assertEquals(GenResult.SUCCESS, r1.resultCode(), "1차 생성 SUCCESS");

        // 재오픈: 저장소가 원문 그대로 돌려준 동일 DEFINITION으로 2차 독립 생성(다른 타겟 루트).
        GenResult r2 = newGenerator(root2).generate(10L);
        assertEquals(GenResult.SUCCESS, r2.resultCode(), "재생성 SUCCESS");

        // 산출 10종이 두 생성에서 바이트 동일(개행 정규화) → 재오픈→재생성 결정성.
        for (String rel : FILES) {
            String a = read(root1, rel);
            String b = read(root2, rel);
            assertEquals(a, b, "재오픈→재생성 산출 불일치: " + rel);
        }
    }

    private ScreenGenerator newGenerator(Path targetRoot) {
        ForgeScreenService screenService = mock(ForgeScreenService.class);
        ForgeProjectService projectService = mock(ForgeProjectService.class);
        var cfg = new CodeGenTemplateConfig().codeGenFreemarkerConfiguration();
        var renderer = new TemplateRenderer(cfg);
        var contextBuilder = new TemplateContextBuilder(new ObjectMapper());
        var pathSafety = new PathSafetyService();
        var fileWriter = new AtomicFileWriter();
        var runtimeSyncer = new RuntimeSyncer(pathSafety, fileWriter);
        var stubGenerator = new StubGenerator(pathSafety, fileWriter);
        when(screenService.get(10L)).thenReturn(screen());
        when(projectService.get(1L)).thenReturn(project(targetRoot));
        return new ScreenGenerator(screenService, projectService, contextBuilder, renderer,
                pathSafety, fileWriter, runtimeSyncer, stubGenerator);
    }

    private ForgeScreen screen() {
        ForgeScreen s = new ForgeScreen();
        s.setScreenId(10L);
        s.setProjectId(1L);
        s.setStem("userMgmt");
        s.setRoleCode("admin");
        s.setArchetypeCode("MGMT_LIST_DETAIL");
        s.setDefinitionJson(DEFINITION);
        return s;
    }

    private ForgeProject project(Path targetRoot) {
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

    private String read(Path root, String rel) throws IOException {
        return Files.readString(root.resolve(rel), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").replace("\r", "\n");
    }
}
