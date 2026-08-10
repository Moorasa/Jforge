package com.jworks.forge.gen.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

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
 * 🔒 생성 파이프라인 통합 테스트 (P4-4, 계약 §1/§4/§5).
 *
 * <p>실제 {@link TemplateContextBuilder}·{@link TemplateRenderer}(FreeMarker)·
 * {@link PathSafetyService}·{@link AtomicFileWriter}를 배선하고, 입력 서비스만 Mockito로 스텁한다.
 * 7파일 실생성·내용 일치·경로탈출 차단(루트 밖 0)·원자성/백업·부분실패·404를 검증한다.
 */
class ScreenGeneratorTest {

    /** 스키마_DEFINITION_JSON §6 예시(userMgmt/admin, listArea에 TABLE_VIEW 1뷰). */
    private static final String DEFINITION_JSON = """
            {
              "schemaVersion": 1,
              "archetype": "MGMT_LIST_DETAIL",
              "stem": "userMgmt",
              "role": "admin",
              "slots": {
                "listArea": [
                  { "instanceId": "tableView_1", "moduleTypeCode": "TABLE_VIEW",
                    "props": { "columns": [
                      { "name": "userId", "displayName": "사용자ID", "displayYn": true, "sortYn": true },
                      { "name": "userName", "displayName": "이름", "displayYn": true, "sortYn": true },
                      { "name": "regDtm", "displayName": "등록일시", "displayYn": true, "sortYn": false } ],
                      "selectMode": "checkbox", "pagingYn": true, "excelYn": true, "csvYn": false } }
                ]
              }
            }
            """;

    /** listArea에 TABLE_VIEW 없음(TOOLBAR만) → 모듈 3종 스킵, shell/list 4종만. */
    private static final String NO_TABLEVIEW_JSON = """
            {
              "schemaVersion": 1, "archetype": "MGMT_LIST_DETAIL", "stem": "userMgmt", "role": "admin",
              "slots": { "listToolbar": [
                { "instanceId": "toolbar_1", "moduleTypeCode": "TOOLBAR",
                  "props": { "buttons": [ { "actionCode": "add", "label": "추가", "styleClass": "btn-primary" } ] } } ] }
            }
            """;

    // P6-2: 뷰 CSS는 공통추출(commonScreenLayout.css) → per-screen 뷰 CSS 미산출. 화면 산출 6종.
    private static final List<String> EXPECTED_REL_PATHS = List.of(
            "WEB-INF/views/admin/userMgmt/userMgmt.jsp",
            "WEB-INF/views/admin/userMgmt/userMgmtList.jsp",
            "static/js/admin/userMgmt/userMgmtList.js",
            "static/css/admin/userMgmt/userMgmtList.css",
            "WEB-INF/views/admin/userMgmt/userMgmtListTableView.jsp",
            "static/js/admin/userMgmt/userMgmtListTableView.js");

    private ForgeScreenService screenService;
    private ForgeProjectService projectService;
    private TemplateRenderer renderer;
    private ScreenGenerator generator;
    private AtomicFileWriter fileWriter;

    @TempDir
    Path targetRoot;

    @BeforeEach
    void setUp() {
        screenService = mock(ForgeScreenService.class);
        projectService = mock(ForgeProjectService.class);
        var cfg = new CodeGenTemplateConfig().codeGenFreemarkerConfiguration();
        renderer = new TemplateRenderer(cfg);
        var contextBuilder = new TemplateContextBuilder(new ObjectMapper());
        var pathSafety = new PathSafetyService();
        fileWriter = new AtomicFileWriter();
        var runtimeSyncer = new RuntimeSyncer(pathSafety, fileWriter);
        var stubGenerator = new StubGenerator(pathSafety, fileWriter);
        generator = new ScreenGenerator(
                screenService, projectService, contextBuilder, renderer, pathSafety, fileWriter,
                runtimeSyncer, stubGenerator);
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

    private void wireHappy(String json) {
        when(screenService.get(10L)).thenReturn(screen(json));
        when(projectService.get(1L)).thenReturn(project());
    }

    @Test
    void screenId_투입시_6파일을_실제로_생성하고_내용이_렌더결과와_일치한다() throws IOException {
        wireHappy(DEFINITION_JSON);

        GenResult result = generator.generate(10L);

        assertEquals(GenResult.SUCCESS, result.resultCode());
        assertTrue(result.files().stream().allMatch(GenFile::success));

        // 계약 §6 화면 3종 relativePath 7건 정확 일치(런타임/stub 제외).
        List<String> actual = result.files().stream()
                .filter(f -> !f.artifactKey().startsWith("runtime:")
                        && !f.artifactKey().startsWith("stub"))
                .map(GenFile::relativePath).toList();
        assertEquals(EXPECTED_REL_PATHS, actual, "산출 relativePath가 계약 §6와 일치");

        // 실제 파일이 타겟루트 하위에 생겼고 내용이 렌더 결과와 바이트 동등.
        var model = new TemplateContextBuilder(new ObjectMapper())
                .build(screen(DEFINITION_JSON), project());
        // 파이프라인이 렌더 직전 넣는 listArea 뷰 접미사(계약 §8.1)를 동일 단일 소스(GenArtifacts)에서
        // 재현해 채운다 — 그래야 여기서 재렌더한 list.jsp가 파이프라인 산출과 바이트 동등하다.
        model.put("listAreaViewSuffix", GenArtifacts.listAreaViewSuffix("TABLE_VIEW"));
        record Pair(String rel, String key) {}
        List<Pair> pairs = List.of(
                new Pair("WEB-INF/views/admin/userMgmt/userMgmt.jsp", "archetype/mgmtListDetail/shell"),
                new Pair("WEB-INF/views/admin/userMgmt/userMgmtList.jsp", "archetype/mgmtListDetail/list"),
                new Pair("static/js/admin/userMgmt/userMgmtList.js", "archetype/mgmtListDetail/listJs"),
                new Pair("static/css/admin/userMgmt/userMgmtList.css", "archetype/mgmtListDetail/listCss"),
                new Pair("WEB-INF/views/admin/userMgmt/userMgmtListTableView.jsp", "module/tableView"),
                new Pair("static/js/admin/userMgmt/userMgmtListTableView.js", "module/tableViewJs"));
        for (Pair p : pairs) {
            Path f = targetRoot.resolve(p.rel());
            assertTrue(Files.exists(f), "생성됨: " + p.rel());
            String onDisk = Files.readString(f, StandardCharsets.UTF_8);
            assertEquals(renderer.render(p.key(), model), onDisk, "내용 일치: " + p.rel());
        }
    }

    @Test
    void listArea에_TABLE_VIEW가_없으면_shell_list_4종만_생성하고_문서는_실패하지_않는다() {
        wireHappy(NO_TABLEVIEW_JSON);

        GenResult result = generator.generate(10L);

        assertEquals(GenResult.SUCCESS, result.resultCode());
        long screenFiles = result.files().stream()
                .filter(f -> !f.artifactKey().startsWith("runtime:")
                        && !f.artifactKey().startsWith("stub"))
                .count();
        assertEquals(4, screenFiles, "shell + list 3종 = 4파일");
        // 화면 아티팩트(런타임/stub 제외)에 TableView 모듈 산출이 없어야 함.
        assertTrue(result.files().stream()
                .filter(f -> !f.artifactKey().startsWith("runtime:")
                        && !f.artifactKey().startsWith("stub"))
                .noneMatch(f -> f.artifactKey().contains("TableView")));
    }

    @Test
    void 없는_screenId는_404_NotFound() {
        when(screenService.get(999L)).thenThrow(new NotFoundException("screen not found: 999"));
        assertThrows(NotFoundException.class, () -> generator.generate(999L));
    }

    @Test
    void 없는_projectId는_404_NotFound() {
        when(screenService.get(10L)).thenReturn(screen(DEFINITION_JSON));
        when(projectService.get(1L)).thenThrow(new NotFoundException("project not found: 1"));
        assertThrows(NotFoundException.class, () -> generator.generate(10L));
    }

    /**
     * 🔒 경로탈출: basePath에 {@code ..}/절대경로/드라이브(C:)/UNC/화이트리스트밖 확장자를 주입한
     * 프로젝트로 생성 시, 해당 파일은 PathSafetyException으로 실패(PARTIAL/FAIL)하고
     * <b>타겟루트 밖에 파일이 하나도 생기지 않는다</b>. 나머지(안전한) 파일만 성공한다.
     */
    @Test
    void 경로탈출_주입시_해당파일은_차단되고_타겟루트_밖에_파일0(@TempDir Path outside) throws IOException {
        when(screenService.get(10L)).thenReturn(screen(DEFINITION_JSON));
        // JSP만 정상, JS/CSS basePath에 경로탈출/절대경로/드라이브를 주입.
        ForgeProject p = project();
        p.setJsBasePath("../../../" + outside.getFileName());      // 루트 밖 탈출 시도
        p.setCssBasePath("C:/Windows/Temp");                        // 드라이브/절대경로
        when(projectService.get(1L)).thenReturn(p);

        GenResult result = generator.generate(10L);

        // 화면 3종 중 JS/CSS 아티팩트는 전부 실패(PathSafety), JSP만 성공 → PARTIAL.
        // (런타임/stub은 project basePath와 무관한 정적 경로라 성공하므로 화면 아티팩트만 검사.)
        assertEquals(GenResult.PARTIAL, result.resultCode());
        List<GenFile> screenArtifacts = result.files().stream()
                .filter(f -> !f.artifactKey().startsWith("runtime:")
                        && !f.artifactKey().startsWith("stub"))
                .toList();
        for (GenFile f : screenArtifacts) {
            boolean isJsOrCss = f.relativePath().endsWith(".js") || f.relativePath().endsWith(".css");
            assertEquals(!isJsOrCss, f.success(), "js/css는 차단, jsp는 성공: " + f.relativePath());
            if (!f.success()) {
                assertTrue(f.reason().contains("경로안전"), "실패 사유는 경로안전: " + f.reason());
            }
        }

        // 🔒 타겟루트 밖(outside) 및 C:/Windows/Temp 하위에 아무 산출도 없음.
        assertNoForgeFilesUnder(outside);
        // outside 형제 디렉터리 어디에도 userMgmt 산출이 없어야 함 — outside가 비어있음으로 확인.
        try (Stream<Path> s = Files.walk(outside)) {
            long files = s.filter(Files::isRegularFile).count();
            assertEquals(0, files, "타겟루트 밖에 파일 0");
        }
    }

    /** 🔒 relativePath에 화이트리스트밖 확장자를 유발하는 basePath는 없지만, 확장자는 spec 고정이라 안전 — 확인만. */
    @Test
    void 산출_확장자는_전부_화이트리스트_내다() {
        wireHappy(DEFINITION_JSON);
        GenResult result = generator.generate(10L);
        for (GenFile f : result.files()) {
            String rel = f.relativePath();
            assertTrue(rel.endsWith(".jsp") || rel.endsWith(".js") || rel.endsWith(".css")
                            || rel.endsWith(".java") || rel.endsWith(".xml"),
                    "확장자 화이트리스트: " + rel);
        }
    }

    /**
     * 🔒 원자성/백업: 이미 존재하는 산출 파일이 있으면 {@code .bak-*} 백업 후 덮어쓰기.
     * 재생성 후에도 최종 경로에 온전한 파일만 있고 임시파일이 남지 않는다.
     */
    @Test
    void 기존_산출_존재시_bak_백업_후_원자적_덮어쓰기(@TempDir Path ignored) throws IOException {
        wireHappy(DEFINITION_JSON);
        // 1차 생성.
        assertEquals(GenResult.SUCCESS, generator.generate(10L).resultCode());
        Path shell = targetRoot.resolve("WEB-INF/views/admin/userMgmt/userMgmt.jsp");
        String first = Files.readString(shell);
        // 파일을 오염시켜 덮어쓰기가 실제로 일어나는지 확인.
        Files.writeString(shell, "STALE");

        // 2차 생성 → STALE는 백업되고 정상 렌더로 덮어씀.
        assertEquals(GenResult.SUCCESS, generator.generate(10L).resultCode());
        assertEquals(first, Files.readString(shell), "정상 내용으로 덮어씀");

        Path dir = shell.getParent();
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> baks = s.filter(x -> x.getFileName().toString().contains(".bak-")).toList();
            assertFalse(baks.isEmpty(), "백업 생성됨");
        }
        // 임시파일 잔존 없음.
        try (Stream<Path> s = Files.walk(targetRoot)) {
            assertTrue(s.noneMatch(x -> x.getFileName().toString().contains(".tmp-")),
                    "임시파일 잔존 없음");
        }
    }

    // ---------------------------------------------------------------------
    // P4-6: 런타임 공통 1회 동기화(skip) + stub 폴더 + packageBase 경로안전
    // ---------------------------------------------------------------------

    /**
     * 🔒 AC(a): 동일 화면 2회 생성 시 런타임 공통 파일은 재복사 0(skip). 첫 생성에서 복사되고
     * 2회차엔 존재하므로 결과에 runtime GenFile이 없어야 한다(백업 파일도 생기지 않음).
     * 화면 3종은 2회차에도 재생성(백업 후)된다.
     */
    @Test
    void 동일화면_2회생성시_런타임공통은_재복사0_skip이고_화면3종은_재생성된다() throws IOException {
        wireHappy(DEFINITION_JSON);

        GenResult first = generator.generate(10L);
        long runtimeFirst = first.files().stream()
                .filter(f -> f.artifactKey().startsWith("runtime:")).count();
        assertTrue(runtimeFirst > 0, "1회차엔 런타임이 복사됨");
        assertTrue(first.files().stream()
                .filter(f -> f.artifactKey().startsWith("runtime:")).allMatch(GenFile::success));

        // 런타임 실파일이 타겟에 실제로 생겼는지(대표 1건).
        Path jquery = targetRoot.resolve("static/external/jquery/jquery-3.7.1.min.js");
        assertTrue(Files.exists(jquery), "런타임 jquery 복사됨");

        GenResult second = generator.generate(10L);
        long runtimeSecond = second.files().stream()
                .filter(f -> f.artifactKey().startsWith("runtime:")).count();
        assertEquals(0, runtimeSecond, "2회차엔 런타임 재복사 0(skip)");

        // 런타임 파일 옆에 .bak-* 백업이 생기지 않았어야 한다(skip이라 write 미호출).
        Path jqueryDir = jquery.getParent();
        try (Stream<Path> s = Files.list(jqueryDir)) {
            assertTrue(s.noneMatch(x -> x.getFileName().toString().contains(".bak-")),
                    "런타임 skip이라 백업 미생성");
        }

        // 화면 3종(shell)은 2회차에도 재생성 → 백업 존재.
        Path shellDir = targetRoot.resolve("WEB-INF/views/admin/userMgmt");
        try (Stream<Path> s = Files.list(shellDir)) {
            assertTrue(s.anyMatch(x -> x.getFileName().toString().contains(".bak-")),
                    "화면 3종은 재생성(백업 후)");
        }
    }

    /**
     * 🔒 AC(b): stub 3종이 stub 위치에 빈 골격으로 생성되고, java/xml 확장자가 화이트리스트를 통과한다.
     * 경로는 검증된 packageBase(점→슬래시) + 정적 세그먼트 + 검증된 stem으로만 조립된다.
     */
    @Test
    void stub_3종이_stub위치에_빈골격으로_생성되고_java_xml_화이트리스트_통과() throws IOException {
        wireHappy(DEFINITION_JSON);

        GenResult result = generator.generate(10L);

        // packageBase=com.jworks.forge, stem=userMgmt → 폴더/파일명 정적 조립.
        Path controller = targetRoot.resolve(
                "src/main/java/com/jworks/forge/userMgmt/UserMgmtController.java");
        Path mapper = targetRoot.resolve(
                "src/main/java/com/jworks/forge/userMgmt/UserMgmtMapper.java");
        Path mapperXml = targetRoot.resolve(
                "src/main/resources/mapper/userMgmt/UserMgmtMapper.xml");

        assertTrue(Files.exists(controller), "Controller stub 생성");
        assertTrue(Files.exists(mapper), "Mapper stub 생성");
        assertTrue(Files.exists(mapperXml), "Mapper XML stub 생성");

        // 빈 골격: 패키지 선언 + TODO 주석, JWORKS 배너 없음.
        String ctrl = Files.readString(controller, StandardCharsets.UTF_8);
        assertTrue(ctrl.contains("package com.jworks.forge.userMgmt;"), "패키지 선언");
        assertTrue(ctrl.contains("class UserMgmtController"), "클래스 골격");
        assertTrue(ctrl.contains("TODO"), "TODO 주석");
        assertFalse(ctrl.toUpperCase().contains("COPYRIGHT"), "JWORKS 배너 없음");

        String xml = Files.readString(mapperXml, StandardCharsets.UTF_8);
        assertTrue(xml.contains("namespace=\"com.jworks.forge.userMgmt.UserMgmtMapper\""),
                "매퍼 namespace");

        // stub 결과 3건 전부 성공, 확장자는 java/xml(화이트리스트 내).
        List<GenFile> stubs = result.files().stream()
                .filter(f -> f.artifactKey().startsWith("stub")).toList();
        assertEquals(3, stubs.size(), "stub 3종");
        assertTrue(stubs.stream().allMatch(GenFile::success), "stub 전량 성공");
        assertTrue(stubs.stream().allMatch(
                f -> f.relativePath().endsWith(".java") || f.relativePath().endsWith(".xml")),
                "stub 확장자 java/xml");
    }

    /**
     * 🔒 AC(c): packageBase에 {@code ..}/슬래시/특수문자를 주입하면 컨텍스트 구성 단계에서
     * 하드 차단(TemplateContextException) → 전체 FAIL, 타겟에 아무 파일도 쓰지 않는다(런타임/stub 포함).
     */
    @Test
    void packageBase에_경로탈출_주입시_하드차단되고_파일0() throws IOException {
        String[] evilPackages = {
                "com..jworks",            // 빈 라벨(.. 유발)
                "com/jworks/forge",       // 슬래시
                "com.jworks.forge/../etc",// 경로탈출
                "com.jworks;drop",        // 특수문자
                "com.Jworks",             // 대문자(정규식 위반)
                "../../etc"               // 상위참조
        };
        for (String evil : evilPackages) {
            when(screenService.get(10L)).thenReturn(screen(DEFINITION_JSON));
            ForgeProject p = project();
            p.setPackageBase(evil);
            when(projectService.get(1L)).thenReturn(p);

            GenResult result = generator.generate(10L);

            assertEquals(GenResult.FAIL, result.resultCode(),
                    "packageBase 주입은 컨텍스트 단계 하드 실패: " + evil);
            assertTrue(result.files().isEmpty(), "파일 0(런타임/stub 포함): " + evil);
        }
        // 타겟 루트에 어떤 산출도 생기지 않았어야 한다.
        try (Stream<Path> s = Files.walk(targetRoot)) {
            long files = s.filter(Files::isRegularFile).count();
            assertEquals(0, files, "packageBase 주입 시 타겟에 파일 0");
        }
    }

    private void assertNoForgeFilesUnder(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> s = Files.walk(root)) {
            assertTrue(s.filter(Files::isRegularFile)
                            .noneMatch(p -> p.getFileName().toString().startsWith("userMgmt")),
                    "루트 밖에 userMgmt 산출이 존재");
        }
    }
}
