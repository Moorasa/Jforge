package com.jworks.forge.gen.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

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
 * 🔒 P4-7 경로안전 회귀 — <b>파이프라인 통합 레벨</b>.
 *
 * <p>{@link ScreenGeneratorTest} 가 이미 (a){@code ..}+드라이브 basePath 주입, (b)packageBase
 * 주입 하드차단을 커버한다. 본 테스트는 남은 벡터를 파이프라인 레벨에서 채운다:
 * <ul>
 *   <li>UNC 경로({@code \\\\host\\share}) basePath 주입</li>
 *   <li>유닉스 절대경로({@code /etc}) basePath 주입</li>
 *   <li>중간 우회({@code a/b/../../../..}) basePath 주입</li>
 * </ul>
 * 각 벡터에서 해당 아티팩트가 {@code PathSafetyException} 으로 차단되고, <b>격리 임시 루트 밖
 * (형제 outside 디렉터리)에 파일이 하나도 생기지 않음</b>을 단언한다.
 *
 * <p>심볼릭링크 조상탈출은 Windows 권한 문제로 생성 불가할 수 있어 {@link PathSafetyServiceTest}
 * 단위 케이스에서 {@code assumeTrue} 로 다루며(현 스위트 skip 관례와 동일), 여기서는 심링크 없이
 * 검증 가능한 벡터만 skip 없이 커버한다.
 */
class PipelinePathSafetyTest {

    private static final String DEFINITION_JSON = """
            {
              "schemaVersion": 1, "archetype": "MGMT_LIST_DETAIL", "stem": "userMgmt", "role": "admin",
              "slots": { "listArea": [
                { "instanceId": "tableView_1", "moduleTypeCode": "TABLE_VIEW",
                  "props": { "columns": [
                    { "name": "userId", "displayName": "사용자ID", "displayYn": true, "sortYn": true } ],
                    "selectMode": "checkbox", "pagingYn": true, "excelYn": false, "csvYn": false } } ] }
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
        generator = new ScreenGenerator(screenService, projectService, contextBuilder, renderer,
                pathSafety, fileWriter,
                new RuntimeSyncer(pathSafety, fileWriter), new StubGenerator(pathSafety, fileWriter));
    }

    private ForgeScreen screen() {
        ForgeScreen s = new ForgeScreen();
        s.setScreenId(10L);
        s.setProjectId(1L);
        s.setStem("userMgmt");
        s.setRoleCode("admin");
        s.setArchetypeCode("MGMT_LIST_DETAIL");
        s.setDefinitionJson(DEFINITION_JSON);
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

    /** 🔒 UNC/유닉스절대/중간우회를 JS·CSS·JSP basePath에 각각 주입 → 화면 아티팩트 전량 경로안전 차단. */
    @Test
    void UNC_유닉스절대_중간우회_basePath주입은_차단되고_루트밖_파일0(@TempDir Path outside)
            throws IOException {
        record Vector(String label, String jsp, String js, String css) {}
        List<Vector> vectors = List.of(
                new Vector("UNC", "\\\\attacker\\share\\views",
                        "\\\\attacker\\share\\js", "\\\\attacker\\share\\css"),
                new Vector("유닉스절대", "/etc/views", "/etc/js", "/etc/css"),
                new Vector("중간우회", "a/b/../../../../" + outside.getFileName() + "/v",
                        "a/b/../../../../" + outside.getFileName() + "/j",
                        "a/b/../../../../" + outside.getFileName() + "/c"));

        for (Vector v : vectors) {
            when(screenService.get(10L)).thenReturn(screen());
            ForgeProject p = project();
            p.setJspBasePath(v.jsp());
            p.setJsBasePath(v.js());
            p.setCssBasePath(v.css());
            when(projectService.get(1L)).thenReturn(p);

            GenResult result = generator.generate(10L);

            // 화면 아티팩트(런타임/stub 제외)는 basePath 주입으로 전량 실패해야 한다.
            List<GenFile> screenArtifacts = result.files().stream()
                    .filter(f -> !f.artifactKey().startsWith("runtime:")
                            && !f.artifactKey().startsWith("stub"))
                    .toList();
            assertTrue(screenArtifacts.stream().noneMatch(GenFile::success),
                    v.label() + ": 화면 아티팩트가 하나라도 성공하면 안 됨");
            for (GenFile f : screenArtifacts) {
                assertTrue(f.reason() != null && f.reason().contains("경로안전"),
                        v.label() + ": 실패 사유는 경로안전 — " + f.reason());
            }
        }

        // 🔒 어떤 벡터에서도 격리 루트 밖(outside)에 파일이 생기지 않았다.
        assertEmptyTree(outside);
    }

    /**
     * 🔒 전 벡터 통틀어 타겟 루트 밖에 화면 산출 유출 0(형제 outside 디렉터리 walk).
     * outside 는 매 벡터에서 재사용되는 격리 임시 디렉터리로, 종료 시 파일 0 이어야 한다.
     */
    private void assertEmptyTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> s = Files.walk(root)) {
            long files = s.filter(Files::isRegularFile).count();
            assertEquals(0, files, "격리 루트 밖에 파일이 생김(경로안전 유출)");
        }
    }
}
