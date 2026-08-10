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
 * §10.4 DUAL_LAYOUT 패인 오배치 방어.
 *
 * <p>{@code leftArea}/{@code rightArea}는 iframe 패인이라 {@code LAYOUT_FRAME}만 의미가 있다.
 * 예전에는 뷰 모듈을 놓아도 팔레트가 막지 않았고, 생성하면 (1) 슬롯 전제({@code listArea[0]})가
 * 어긋난 모듈 템플릿이 렌더 실패해 결과가 <b>조용히 PARTIAL</b>이 되고 (2) 화면엔 내용 없는
 * iframe만 남았다. 이 테스트는 그 두 가지가 다시 생기지 않게 고정한다.
 *
 * <p>1차 방어는 저장 검증(카테고리 화이트리스트 FRAME)이지만, 그 이전에 저장된 정의나 외부에서
 * 들어온 정의도 <b>생성 파이프라인이 조용히 깨지지 않아야</b> 한다.
 */
class DualPaneGuardTest {

    @TempDir
    Path root;

    @Test
    void 패인에_뷰_모듈이_있어도_생성은_깨지지_않고_모듈_파일도_만들지_않는다() throws IOException {
        String definition = """
                {
                  "schemaVersion": 1, "archetype": "DUAL_LAYOUT", "stem": "dualScreen", "role": "admin",
                  "slots": {
                    "leftArea": [
                      { "instanceId": "tableView_1", "moduleTypeCode": "TABLE_VIEW",
                        "props": { "columns": [ { "name": "userId", "displayName": "ID" } ] } } ],
                    "rightArea": [
                      { "instanceId": "layoutFrame_1", "moduleTypeCode": "LAYOUT_FRAME",
                        "props": { "frameId": "rightFrame" } } ]
                  }
                }""";

        GenResult r = generate(definition);

        // (1) 조용한 PARTIAL 금지 — 렌더 실패가 없어야 한다.
        assertEquals(GenResult.SUCCESS, r.resultCode(),
                "실패 파일: " + r.files().stream().filter(f -> !f.success()).map(GenFile::relativePath).toList());

        // (2) 패인의 뷰 모듈은 모듈 파일을 만들지 않는다(모듈 템플릿은 listArea 전제).
        // (번들 런타임의 commonListTableView.* 와 헷갈리지 않게 화면 파일명으로 확인한다.)
        assertTrue(r.files().stream().noneMatch(f -> f.relativePath().contains("dualScreenListTableView")),
                "패인 뷰 모듈이 모듈 파일을 만들었다: "
                        + r.files().stream().map(GenFile::relativePath).toList());

        // (3) 빈 iframe 으로 둔갑시키지 않는다 — 건너뛴 사실이 주석으로 남는다.
        String shell = Files.readString(
                root.resolve("WEB-INF/views/admin/dualScreen/dualScreen.jsp"), StandardCharsets.UTF_8);
        assertFalse(shell.contains("data-module=\"TABLE_VIEW\""), "빈 iframe 으로 산출됐다: \n" + shell);
        assertTrue(shell.contains("건너뜀"), "건너뛴 사실이 남아야 한다: \n" + shell);
        // 정상 프레임은 그대로 산출된다.
        assertTrue(shell.contains("id=\"rightFrame\""), "정상 프레임이 사라졌다: \n" + shell);
    }

    private GenResult generate(String definitionJson) {
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
        s.setScreenId(30L);
        s.setProjectId(1L);
        s.setStem("dualScreen");
        s.setRoleCode("admin");
        s.setArchetypeCode("DUAL_LAYOUT");
        s.setDefinitionJson(definitionJson);

        ForgeProject p = new ForgeProject();
        p.setProjectId(1L);
        p.setTargetRootPath(root.toString());
        p.setPackageBase("com.jworks.forge");
        p.setJspBasePath("WEB-INF/views");
        p.setJsBasePath("static/js");
        p.setCssBasePath("static/css");
        p.setRuntimeVer("1.0.0");

        when(screenService.get(30L)).thenReturn(s);
        when(projectService.get(1L)).thenReturn(p);
        return new ScreenGenerator(screenService, projectService, contextBuilder, renderer,
                pathSafety, fileWriter, runtimeSyncer, stubGenerator).generate(30L);
    }
}
