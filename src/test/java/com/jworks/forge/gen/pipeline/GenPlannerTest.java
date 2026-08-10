package com.jworks.forge.gen.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
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
 * P7-4 생성 dry-run({@link GenPlanner}) 검증.
 *
 * <p>핵심 계약 2가지를 고정한다:
 * <ol>
 *   <li><b>드리프트 0</b>: plan 파일 목록(artifactKey/relativePath)이 실제
 *       {@link ScreenGenerator#generate} 산출(런타임 {@code runtime:*} 제외)과 <b>순서까지 동일</b>.
 *       GenPlanner 는 ScreenGenerator 의 planArtifacts 미러이므로 이 테스트가 회귀망이다.</li>
 *   <li><b>쓰기 0</b>: plan 은 타겟 루트에 아무 파일/폴더도 만들지 않는다(읽기전용).</li>
 * </ol>
 */
class GenPlannerTest {

    @TempDir
    Path root;

    /** 풀조합(6슬롯) DEFINITION — RoundTripDeterminismTest 와 동일 의미(최대 아티팩트 조합). */
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

    @Test
    void plan은_실산출_파일목록과_동일하고_아무것도_쓰지_않는다() throws IOException {
        GenPlanner planner = newPlanner(root);

        // 1) 생성 전 plan: 전부 신규(exists=false) + 타겟 루트 존재.
        GenPlanner.GenPlan before = planner.plan(10L);
        assertNull(before.failReason(), "정상 화면은 failReason 없음");
        assertTrue(before.targetRootExists(), "TempDir 루트는 존재");
        assertFalse(before.files().isEmpty(), "계획 파일이 있어야 함");
        before.files().forEach(f ->
                assertFalse(f.exists(), "생성 전인데 exists=true: " + f.relativePath()));

        // 2) 쓰기 0: plan 호출이 타겟 루트에 어떤 항목도 만들지 않았다.
        try (var entries = Files.list(root)) {
            assertEquals(0L, entries.count(), "dry-run 이 타겟 루트에 무언가를 썼다");
        }

        // 3) 드리프트 0: plan 목록 == 실산출(런타임 제외) — artifactKey/relativePath 순서까지 동일.
        GenResult result = newGenerator(root).generate(10L);
        assertEquals(GenResult.SUCCESS, result.resultCode(), "실산출 SUCCESS 전제");
        List<String> actual = result.files().stream()
                .filter(f -> !f.artifactKey().startsWith("runtime:"))
                .map(f -> f.artifactKey() + "|" + f.relativePath())
                .toList();
        List<String> planned = before.files().stream()
                .map(f -> f.artifactKey() + "|" + f.relativePath())
                .toList();
        assertEquals(actual, planned, "plan 과 실산출 파일 목록이 다르다(드리프트)");

        // 4) 생성 후 재plan: 전부 덮어쓰기(exists=true) 판정.
        GenPlanner.GenPlan after = planner.plan(10L);
        after.files().forEach(f ->
                assertTrue(f.exists(), "생성 후인데 exists=false: " + f.relativePath()));
    }

    @Test
    void 타겟_루트가_없으면_targetRootExists_false_로_계획만_돌려준다() {
        Path missing = root.resolve("no-such-dir");
        GenPlanner planner = newPlanner(missing);

        GenPlanner.GenPlan plan = planner.plan(10L);
        assertNull(plan.failReason(), "루트 부재는 계획 실패가 아니라 경고 신호");
        assertFalse(plan.targetRootExists(), "루트 부재 신호");
        assertFalse(plan.files().isEmpty(), "파일 목록은 여전히 계획된다");
        plan.files().forEach(f -> assertFalse(f.exists(), "루트가 없으면 전부 신규 판정"));
    }

    // ---------- 픽스처(RoundTripDeterminismTest 와 동형) ----------

    private GenPlanner newPlanner(Path targetRoot) {
        ForgeScreenService screenService = mock(ForgeScreenService.class);
        ForgeProjectService projectService = mock(ForgeProjectService.class);
        var contextBuilder = new TemplateContextBuilder(new ObjectMapper());
        var pathSafety = new PathSafetyService();
        var stubGenerator = new StubGenerator(pathSafety, new AtomicFileWriter());
        when(screenService.get(10L)).thenReturn(screen());
        when(projectService.get(1L)).thenReturn(project(targetRoot));
        return new GenPlanner(screenService, projectService, contextBuilder, pathSafety, stubGenerator,
                mock(com.jworks.forge.gen.hist.GenHistMapper.class), new ObjectMapper());
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
}
