package com.jworks.forge.gen.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

/** P9: data/events 선언을 설정한 경우에만 Design.js가 계획·생성되는지 고정한다. */
class DesignMetadataGenerationTest {

    @TempDir
    Path root;

    @Test
    void data_이벤트가_있으면_Design_js를_생성하고_shell에_연결한다() throws Exception {
        ForgeScreenService screens = mock(ForgeScreenService.class);
        ForgeProjectService projects = mock(ForgeProjectService.class);
        ForgeScreen screen = screen();
        ForgeProject project = project();
        when(screens.get(10L)).thenReturn(screen);
        when(projects.get(1L)).thenReturn(project);

        var contextBuilder = new TemplateContextBuilder(new ObjectMapper());
        var pathSafety = new PathSafetyService();
        var writer = new AtomicFileWriter();
        var generator = new ScreenGenerator(screens, projects, contextBuilder,
                new TemplateRenderer(new CodeGenTemplateConfig().codeGenFreemarkerConfiguration()),
                pathSafety, writer, new RuntimeSyncer(pathSafety, writer), new StubGenerator(pathSafety, writer));
        var planner = new GenPlanner(screens, projects, contextBuilder, pathSafety,
                new StubGenerator(pathSafety, writer),
                mock(com.jworks.forge.gen.hist.GenHistMapper.class), new ObjectMapper());

        assertTrue(planner.plan(10L).files().stream()
                        .anyMatch(f -> f.relativePath().equals("static/js/admin/userMgmt/userMgmtDesign.js")),
                "dry-run도 Design.js를 보여야 한다");

        GenResult result = generator.generate(10L);
        assertEquals(GenResult.SUCCESS, result.resultCode());
        Path design = root.resolve("static/js/admin/userMgmt/userMgmtDesign.js");
        assertTrue(Files.exists(design));
        String designText = Files.readString(design, StandardCharsets.UTF_8);
        assertTrue(designText.contains("MagicIAM_Design"));
        assertTrue(designText.contains("openDetail"));
        assertTrue(designText.contains("function load"), "생성 파일이 선언을 실제 런타임으로 연결");
        assertTrue(designText.contains("frg:design:"), "업무 화면이 표준 DOM 이벤트를 구독할 수 있음");
        assertTrue(designText.contains("\\/api\\/users"), "API 경로는 JS 문자열로 이스케이프");
        String shell = Files.readString(root.resolve("WEB-INF/views/admin/userMgmt/userMgmt.jsp"), StandardCharsets.UTF_8);
        assertTrue(shell.contains("userMgmtDesign.js"));
        String table = Files.readString(root.resolve("WEB-INF/views/admin/userMgmt/userMgmtListTableView.jsp"), StandardCharsets.UTF_8);
        assertTrue(table.contains("data-frg-instance-id=\"table_1\""), "런타임이 모듈 루트를 정확히 찾음");
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
                  "listArea":[{"instanceId":"table_1","moduleTypeCode":"TABLE_VIEW","props":{"columns":[]},
                    "data":{"endpoint":"/api/users","method":"GET","resultPath":"items","autoLoad":true},
                    "events":[{"event":"click","action":"openDetail","target":"userId"}]}]}}
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
