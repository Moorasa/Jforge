package com.jworks.forge.gen.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jworks.forge.common.web.NotFoundException;
import com.jworks.forge.gen.context.TemplateContextBuilder;
import com.jworks.forge.gen.hist.GenHist;
import com.jworks.forge.gen.hist.GenHistMapper;
import com.jworks.forge.gen.hist.GenerationService;
import com.jworks.forge.gen.safety.PathSafetyService;
import com.jworks.forge.gen.template.CodeGenTemplateConfig;
import com.jworks.forge.gen.template.TemplateRenderer;
import com.jworks.forge.project.domain.ForgeProject;
import com.jworks.forge.project.service.ForgeProjectService;
import com.jworks.forge.screen.domain.ForgeScreen;
import com.jworks.forge.screen.service.ForgeScreenService;

/**
 * 🔒 P12(계약 §16): 재생성이 사람의 수정을 조용히 삼키지 않는지 — 드리프트 감지 · diff · 보호구역
 * 보존 · 백업 복원을 실제 생성 파이프라인으로 검증한다.
 */
class RegenerationSafetyTest {

    @TempDir
    Path root;

    private static final String SHELL_JSP = "WEB-INF/views/admin/userMgmt/userMgmt.jsp";
    private static final String CONTROLLER = "src/main/java/com/jworks/forge/userMgmt/UserMgmtController.java";

    private final List<GenHist> historyRows = new ArrayList<>();

    private GenerationService generationService;
    private GenPlanner planner;
    private GenDiffService diffService;
    private GenRestoreService restoreService;

    @BeforeEach
    void setUp() {
        ForgeScreenService screens = mock(ForgeScreenService.class);
        ForgeProjectService projects = mock(ForgeProjectService.class);
        when(screens.get(10L)).thenReturn(screen());
        when(projects.get(1L)).thenReturn(project());

        var pathSafety = new PathSafetyService();
        var writer = new AtomicFileWriter();
        var contextBuilder = new TemplateContextBuilder(new ObjectMapper());
        var renderer = new TemplateRenderer(new CodeGenTemplateConfig().codeGenFreemarkerConfiguration());
        var stubGenerator = new StubGenerator(pathSafety, writer);
        var generator = new ScreenGenerator(screens, projects, contextBuilder, renderer,
                pathSafety, writer, new RuntimeSyncer(pathSafety, writer), stubGenerator);

        // 이력 매퍼: insert 를 메모리에 쌓고 selectByScreen 이 최신순으로 돌려준다(실 직렬화 경로 사용).
        GenHistMapper histMapper = mock(GenHistMapper.class);
        doAnswer(invocation -> {
            historyRows.add(0, invocation.getArgument(0));
            return 1;
        }).when(histMapper).insert(any());
        when(histMapper.selectByScreen(10L)).thenAnswer(invocation -> historyRows);

        generationService = new GenerationService(generator, histMapper, new ObjectMapper());
        planner = new GenPlanner(screens, projects, contextBuilder, pathSafety, stubGenerator,
                histMapper, new ObjectMapper());
        diffService = new GenDiffService(screens, projects, contextBuilder, renderer,
                pathSafety, stubGenerator, planner);
        restoreService = new GenRestoreService(screens, projects, pathSafety, writer, planner);
    }

    @Test
    void 생성_직후에는_드리프트가_없다() {
        generationService.generateAndRecord(10L);

        List<GenPlanner.PlanFile> files = planner.plan(10L).files();

        assertFalse(files.isEmpty());
        files.forEach(f -> assertEquals(GenPlanner.DRIFT_UNCHANGED, f.drift(),
                "방금 생성한 파일은 생성물 그대로여야 한다: " + f.relativePath()));
    }

    @Test
    void 사람이_손댄_파일만_MODIFIED로_보고한다() throws Exception {
        generationService.generateAndRecord(10L);
        appendLine(root.resolve(SHELL_JSP), "<!-- 사람이 직접 넣은 주석 -->");

        List<GenPlanner.PlanFile> files = planner.plan(10L).files();

        assertEquals(GenPlanner.DRIFT_MODIFIED, driftOf(files, SHELL_JSP),
                "외부 수정은 덮어쓰기 전에 경고돼야 한다");
        files.stream()
                .filter(f -> !f.relativePath().equals(SHELL_JSP))
                .forEach(f -> assertEquals(GenPlanner.DRIFT_UNCHANGED, f.drift(),
                        "손대지 않은 파일까지 경고하면 안 된다: " + f.relativePath()));
    }

    @Test
    void 이력이_없으면_UNKNOWN으로_수렴한다() {
        generationService.generateAndRecord(10L);
        historyRows.clear(); // P12 이전에 생성됐거나 이력이 지워진 상황

        assertEquals(GenPlanner.DRIFT_UNKNOWN, driftOf(planner.plan(10L).files(), SHELL_JSP));
    }

    @Test
    void 파일이_없으면_NEW다() {
        assertEquals(GenPlanner.DRIFT_NEW, driftOf(planner.plan(10L).files(), SHELL_JSP));
    }

    /** 🔒 P12의 핵심: 보호구역에 채운 코드는 재생성해도 살아남는다. */
    @Test
    void 보호구역의_사용자_코드는_재생성에서_보존된다() throws Exception {
        generationService.generateAndRecord(10L);
        Path controller = root.resolve(CONTROLLER);
        String original = Files.readString(controller, StandardCharsets.UTF_8);
        Files.writeString(controller,
                original.replace("    // 이 사이에 쓴 코드는 재생성해도 보존됩니다.\n",
                        "    // 사람이 채운 핸들러\n    void myHandler() { }\n"),
                StandardCharsets.UTF_8);

        generationService.generateAndRecord(10L);

        String regenerated = Files.readString(controller, StandardCharsets.UTF_8);
        assertTrue(regenerated.contains("void myHandler() { }"), "보호구역 코드가 보존돼야 한다");
        assertTrue(regenerated.contains("public class UserMgmtController"), "생성 구간은 그대로 갱신");
        // 병합 결과가 이력 해시의 기준이므로, 재생성 직후 드리프트는 없어야 한다.
        assertEquals(GenPlanner.DRIFT_UNCHANGED, driftOf(planner.plan(10L).files(), CONTROLLER));
    }

    @Test
    void diff는_사람이_바꾼_내용을_보여준다() throws Exception {
        generationService.generateAndRecord(10L);
        appendLine(root.resolve(SHELL_JSP), "<!-- 손으로 넣은 줄 -->");

        GenDiffService.DiffView changed = diffService.diff(10L, "shell");
        assertFalse(changed.identical(), "달라졌으면 identical 이 아니다");
        assertEquals(GenPlanner.DRIFT_MODIFIED, changed.drift());
        assertTrue(changed.hunks().get(0).lines().stream()
                        .anyMatch(line -> line.startsWith("-") && line.contains("손으로 넣은 줄")),
                "사람이 넣은 줄이 '사라질 줄'로 표시돼야 한다");

        GenDiffService.DiffView untouched = diffService.diff(10L, "listTableViewJs");
        assertTrue(untouched.identical(), "손대지 않은 파일은 동일로 나와야 한다");
    }

    /** diff 미리보기는 보호구역 병합까지 반영한 "실제로 쓰일 내용"을 보여준다. */
    @Test
    void diff는_보호구역_병합까지_반영한다() throws Exception {
        generationService.generateAndRecord(10L);
        Path controller = root.resolve(CONTROLLER);
        String original = Files.readString(controller, StandardCharsets.UTF_8);
        Files.writeString(controller,
                original.replace("    // 이 사이에 쓴 코드는 재생성해도 보존됩니다.\n", "    void keepMe() { }\n"),
                StandardCharsets.UTF_8);

        GenDiffService.DiffView view = diffService.diff(10L, "stubController");

        assertTrue(view.identical(),
                "보호구역만 채운 경우 실제로 바뀌는 것이 없으므로 diff 는 '동일'이어야 한다");
    }

    @Test
    void 백업에서_되돌릴_수_있다() throws Exception {
        generationService.generateAndRecord(10L);
        Path shell = root.resolve(SHELL_JSP);
        appendLine(shell, "<!-- 되살리고 싶은 수정 -->");
        String edited = Files.readString(shell, StandardCharsets.UTF_8);

        generationService.generateAndRecord(10L); // 덮어쓰기 → .bak 생성
        assertNotEquals(edited, Files.readString(shell, StandardCharsets.UTF_8), "덮어써졌다");

        List<GenDiffService.BackupEntry> backups = diffService.backups(10L, "shell");
        assertFalse(backups.isEmpty(), "백업 목록이 보여야 한다");

        restoreService.restore(10L, "shell", backups.get(0).timestamp());

        assertEquals(edited, Files.readString(shell, StandardCharsets.UTF_8), "수정본으로 복원");
    }

    /** 🔒 계획에 없는 아티팩트로는 어떤 경로도 만들어낼 수 없다. */
    @Test
    void 계획에_없는_아티팩트는_404다() {
        generationService.generateAndRecord(10L);

        assertThrows(NotFoundException.class, () -> diffService.diff(10L, "../../etc/passwd"));
        assertThrows(NotFoundException.class, () -> diffService.backups(10L, "nope"));
        assertThrows(NotFoundException.class,
                () -> restoreService.restore(10L, "nope", "20260101000000"));
    }

    /** 🔒 복원은 14자리 타임스탬프만 받는다(경로 문자열 미수신). */
    @Test
    void 잘못된_백업_시각은_거부된다() {
        generationService.generateAndRecord(10L);

        assertThrows(IllegalArgumentException.class, () -> restoreService.restore(10L, "shell", "../x"));
        assertThrows(IllegalArgumentException.class, () -> restoreService.restore(10L, "shell", "2026"));
        assertThrows(IllegalArgumentException.class, () -> restoreService.restore(10L, "shell", null));
        // 형식은 맞지만 없는 백업
        assertThrows(IllegalArgumentException.class,
                () -> restoreService.restore(10L, "shell", "19990101000000"));
    }

    // ---------------------------------------------------------------- helpers

    private String driftOf(List<GenPlanner.PlanFile> files, String relativePath) {
        return files.stream()
                .filter(f -> f.relativePath().equals(relativePath))
                .findFirst()
                .orElseThrow(() -> new AssertionError("계획에 없음: " + relativePath))
                .drift();
    }

    private void appendLine(Path file, String line) throws Exception {
        Files.writeString(file, Files.readString(file, StandardCharsets.UTF_8) + line + "\n",
                StandardCharsets.UTF_8);
    }

    private ForgeScreen screen() {
        ForgeScreen screen = new ForgeScreen();
        screen.setScreenId(10L);
        screen.setProjectId(1L);
        screen.setStem("userMgmt");
        screen.setRoleCode("admin");
        screen.setArchetypeCode("MGMT_LIST_DETAIL");
        screen.setDefinitionJson("""
                {"schemaVersion":1,"archetype":"MGMT_LIST_DETAIL","stem":"userMgmt","role":"admin","slots":{
                  "listArea":[{"instanceId":"table_1","moduleTypeCode":"TABLE_VIEW",
                    "props":{"columns":[{"name":"USER_ID","displayName":"아이디"}]}}]}}
                """);
        return screen;
    }

    private ForgeProject project() {
        ForgeProject project = new ForgeProject();
        project.setProjectId(1L);
        project.setTargetRootPath(root.toString());
        project.setPackageBase("com.jworks.forge");
        project.setJspBasePath("WEB-INF/views");
        project.setJsBasePath("static/js");
        project.setCssBasePath("static/css");
        project.setRuntimeVer("1.0.0");
        return project;
    }
}
