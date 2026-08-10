package com.jworks.forge.gen.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * 🔒 P5-1: listArea 뷰 분기 일반화 검증 (계약 §8.1).
 *
 * <p>{@code list.ftl}의 하드코딩 {@code ListTableView} include를 <b>listArea moduleTypeCode →
 * 뷰 세트 정적 매핑 분기</b>로 일반화한 골격을 검증한다:
 * <ul>
 *   <li>(a) TABLE_VIEW 배치 시 include가 정확히 {@code ./{stem}ListTableView.jsp}로 조립(회귀 0).</li>
 *   <li>(b) 미지원 moduleTypeCode를 listArea에 넣으면 include 스킵 + 모듈 아티팩트 0 + 문서 전체 실패 0
 *       (forward-compat).</li>
 *   <li>(c) {@code listAreaViewSuffix} 파생이 🔒정적 화이트리스트 맵({@link GenArtifacts})
 *       경유임(비화이트리스트 코드 → null, 문자열 조립·평가 0).</li>
 * </ul>
 */
class ListAreaViewBranchTest {

    /** listArea에 TABLE_VIEW 1뷰(지원). */
    private static final String TABLEVIEW_JSON = """
            {
              "schemaVersion": 1, "archetype": "MGMT_LIST_DETAIL", "stem": "userMgmt", "role": "admin",
              "slots": { "listArea": [
                { "instanceId": "tableView_1", "moduleTypeCode": "TABLE_VIEW",
                  "props": { "columns": [ { "name": "userId", "displayName": "ID", "displayYn": true, "sortYn": true } ],
                             "selectMode": "checkbox", "pagingYn": true } } ] }
            }
            """;

    /** listArea에 아직 미지원(MODULE_TYPE_WHITELIST/MODULE_ARTIFACTS 미등록) POPUP_VIEW 배치.
     *  (P5-2 CARD_VIEW·P5-3 TREE_VIEW·P5-4 FORM_VIEW 가 지원으로 승격됨 → forward-compat 검증은
     *   여전히 미등록인 임의 코드 POPUP_VIEW 로.) */
    private static final String UNSUPPORTED_VIEW_JSON = """
            {
              "schemaVersion": 1, "archetype": "MGMT_LIST_DETAIL", "stem": "userMgmt", "role": "admin",
              "slots": { "listArea": [
                { "instanceId": "popupView_1", "moduleTypeCode": "POPUP_VIEW",
                  "props": { "labelField": "userName" } } ] }
            }
            """;

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
    // (a) TABLE_VIEW 회귀 무손상: include가 정확히 ./{stem}ListTableView.jsp
    // ------------------------------------------------------------------

    @Test
    void TABLE_VIEW_배치시_listArea_include가_정적접미사로_정확히_조립된다() throws IOException {
        wire(TABLEVIEW_JSON);

        GenResult result = generator.generate(10L);
        assertEquals(GenResult.SUCCESS, result.resultCode(), result.failReason());

        Path listJsp = targetRoot.resolve("WEB-INF/views/admin/userMgmt/userMgmtList.jsp");
        String body = Files.readString(listJsp, StandardCharsets.UTF_8);
        // 🔒 include 경로는 stem + 정적접미사(ListTableView)만 — 자유문자열/원문 조립 0.
        assertTrue(body.contains("<jsp:include page=\"./userMgmtListTableView.jsp\" />"),
                "listArea include가 ./userMgmtListTableView.jsp 로 조립");
        // 모듈 2종(TableView jsp/js)이 산출 목록에 포함(P6-2: 뷰 CSS는 공통추출 → 미산출).
        assertTrue(result.files().stream().anyMatch(f -> f.artifactKey().equals("listTableView")));
        assertTrue(result.files().stream().anyMatch(f -> f.artifactKey().equals("listTableViewJs")));
    }

    // ------------------------------------------------------------------
    // (b) 미지원 moduleTypeCode: include 스킵 + 모듈 아티팩트 0 + 문서 전체 실패 0
    // ------------------------------------------------------------------

    @Test
    void 미지원_moduleTypeCode를_listArea에_넣으면_include스킵되고_모듈아티팩트0_문서실패0() throws IOException {
        wire(UNSUPPORTED_VIEW_JSON);

        GenResult result = generator.generate(10L);

        // forward-compat: 문서 전체는 실패하지 않는다(shell/list 4종 + 런타임/stub 성공).
        assertEquals(GenResult.SUCCESS, result.resultCode(), result.failReason());

        // 화면 아티팩트는 shell + list 3종 = 4파일뿐(뷰 모듈 아티팩트 0).
        long screenFiles = result.files().stream()
                .filter(f -> !f.artifactKey().startsWith("runtime:")
                        && !f.artifactKey().startsWith("stub"))
                .count();
        assertEquals(4, screenFiles, "미지원 뷰는 모듈 아티팩트 0 → shell/list 4종만");

        // list.jsp 본문에 listArea include가 생략됨(뷰 본문 include 0), 그러나 컨테이너 section은 유지.
        Path listJsp = targetRoot.resolve("WEB-INF/views/admin/userMgmt/userMgmtList.jsp");
        String body = Files.readString(listJsp, StandardCharsets.UTF_8);
        assertFalse(body.contains("<jsp:include"), "미지원 뷰는 listArea 본문 include 생략");
        assertTrue(body.contains("<section class=\"list-area\" id=\"list-area\">"),
                "listArea 컨테이너 section은 유지(깨지지 않음)");

        // 타겟에 미지원 뷰의 per-screen 산출(userMgmtListPopupView.*)이 없다(임의 산출 0).
        // ※ 런타임 공통 매니페스트 파일은 §5.4 상시 복사 대상이므로 별개.
        try (var s = Files.walk(targetRoot)) {
            assertTrue(s.filter(Files::isRegularFile)
                            .noneMatch(p -> p.getFileName().toString().startsWith("userMgmtListPopupView")),
                    "미지원 뷰의 per-screen 임의 산출 0");
        }
    }

    // ------------------------------------------------------------------
    // (c) 드리프트 불가능(물리적 단일 소스): 템플릿이 include하는 파일명 == 파이프라인이 산출한 JSP 파일명.
    //     자기참조(맵 vs 맵) 비교가 아니라, "런타임 산출물 간" 교차검증이다.
    // ------------------------------------------------------------------

    @Test
    void 지원뷰_배치시_템플릿_include파일명이_실제_산출_JSP파일명과_일치한다() throws IOException {
        wire(TABLEVIEW_JSON);

        GenResult result = generator.generate(10L);
        assertEquals(GenResult.SUCCESS, result.resultCode(), result.failReason());

        // (1) list.jsp가 실제로 include하는 파일명을 산출물에서 추출한다(정규식 파싱, 하드코딩 아님).
        Path listJsp = targetRoot.resolve("WEB-INF/views/admin/userMgmt/userMgmtList.jsp");
        String body = Files.readString(listJsp, StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("<jsp:include page=\"\\./([^\"]+\\.jsp)\" />").matcher(body);
        assertTrue(m.find(), "listArea 본문 include가 산출됨");
        String includedFile = m.group(1); // 예: userMgmtListTableView.jsp

        // (2) 파이프라인이 실제로 산출한 뷰 JSP 파일명을 결과 목록에서 뽑는다(모듈 JSP 아티팩트).
        //     TABLE_VIEW 모듈의 JSP 산출 relativePath의 파일명.
        String producedViewJsp = result.files().stream()
                .filter(f -> f.artifactKey().equals("listTableView"))
                .map(f -> {
                    String rel = f.relativePath();
                    return rel.substring(rel.lastIndexOf('/') + 1);
                })
                .findFirst().orElseThrow(() -> new AssertionError("TABLE_VIEW JSP 산출 없음"));

        // 🔒 드리프트 불가능 단언: 템플릿 include 파일명 == 실제 산출 JSP 파일명.
        //    두 값이 모두 GenArtifacts.MODULE_ARTIFACTS의 JSP nameSuffix라는 단일 소스에서 나오므로
        //    구조적으로 항상 일치한다(병렬 맵 삭제로 자기참조·드리프트 원천 제거).
        assertEquals(producedViewJsp, includedFile,
                "템플릿 include 파일명과 파이프라인 산출 JSP 파일명이 일치(단일 소스)");

        // 실제로 그 파일이 타겟에 산출되었는지(끊긴 include가 아님).
        Path producedPath = targetRoot.resolve("WEB-INF/views/admin/userMgmt/" + includedFile);
        assertTrue(Files.exists(producedPath), "include된 뷰 JSP가 실제 타겟에 존재: " + includedFile);
    }

    @Test
    void listAreaViewSuffix는_화이트리스트맵_조회값이며_비화이트리스트코드는_null() {
        // 화이트리스트 등록 뷰 → MODULE_ARTIFACTS의 JSP 아티팩트 nameSuffix 그대로(맵 조회, 조립 아님).
        assertEquals("ListTableView", GenArtifacts.listAreaViewSuffix("TABLE_VIEW"));
        // P5-2: CARD_VIEW 승격 → 맵 조회값 ListCardView.
        assertEquals("ListCardView", GenArtifacts.listAreaViewSuffix("CARD_VIEW"));
        // P5-3: TREE_VIEW 승격 → 맵 조회값 ListTreeView.
        assertEquals("ListTreeView", GenArtifacts.listAreaViewSuffix("TREE_VIEW"));
        // P5-4: FORM_VIEW 승격 → 맵 조회값 ListFormView.
        assertEquals("ListFormView", GenArtifacts.listAreaViewSuffix("FORM_VIEW"));

        // 비화이트리스트/미등록/null → 전부 null(임의 파일명 조립 0, forward-compat 스킵).
        assertNull(GenArtifacts.listAreaViewSuffix("POPUP_VIEW"), "미등록 뷰는 null");
        assertNull(GenArtifacts.listAreaViewSuffix("EVIL../../x"), "비화이트리스트 원문은 null");
        assertNull(GenArtifacts.listAreaViewSuffix(""), "빈 코드는 null");
        assertNull(GenArtifacts.listAreaViewSuffix(null), "null 코드는 null");
    }
}
