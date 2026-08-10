package com.jworks.forge.gen.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
 * P10(계약 §14): 서버 바인딩 선언이 stub을 실제 조회 API로 승격시키는지, 게이트 미통과 시
 * <b>기존 빈 stub으로 안전하게 폴백</b>하는지 고정한다.
 */
class ServerBindingGenerationTest {

    @TempDir
    Path root;

    private static final String CONTROLLER = "src/main/java/com/jworks/forge/userMgmt/UserMgmtController.java";
    private static final String MAPPER = "src/main/java/com/jworks/forge/userMgmt/UserMgmtMapper.java";
    private static final String MAPPER_XML = "src/main/resources/mapper/userMgmt/UserMgmtMapper.xml";

    /** 게이트를 전부 통과하면 목록+단건 조회 API가 산출된다. */
    @Test
    void 바인딩_선언이_있으면_실제_조회_API를_산출한다() throws Exception {
        Path out = generate("full", definition("""
                "data":{"endpoint":"/api/users","method":"GET","resultPath":"items","autoLoad":true,
                        "table":"TB_USER","keyColumn":"USER_ID"},
                """));

        String controller = read(out, CONTROLLER);
        assertTrue(controller.contains("@RestController"), "실 컨트롤러로 승격");
        assertTrue(controller.contains("@RequestMapping(\"/api/users\")"), "선언한 경로에 매핑");
        assertTrue(controller.contains("body.put(\"items\", rows);"),
                "resultPath 래핑이 생성 화면 런타임(valueAtPath)과 정합");
        assertTrue(controller.contains("@GetMapping(\"/{key}\")"), "keyColumn이 있으면 단건 핸들러");
        assertFalse(controller.contains("TODO(J-FORGE stub)"), "빈 stub 잔재 0");

        String mapper = read(out, MAPPER);
        assertTrue(mapper.contains("List<Map<String, Object>> selectList();"));
        assertTrue(mapper.contains("Map<String, Object> selectOne(@Param(\"key\") String key);"));

        String xml = read(out, MAPPER_XML);
        assertTrue(xml.contains("USER_ID AS \"USER_ID\","), "컬럼 명시 + 별칭 고정");
        assertTrue(xml.contains("USER_NM AS \"USER_NM\""), "별칭이 화면 컬럼명과 동일");
        assertTrue(xml.contains("FROM TB_USER"));
        assertTrue(xml.contains("WHERE USER_ID = #{key}"), "값은 #{} 바인딩");
        assertFalse(xml.contains("SELECT *"), "SELECT * 금지");
        assertFalse(xml.contains("${"), "${} 금지");
    }

    /** keyColumn이 없으면 목록만 승격하고 단건 조회는 만들지 않는다. */
    @Test
    void keyColumn이_없으면_단건_조회는_산출하지_않는다() throws Exception {
        Path out = generate("listOnly", definition("""
                "data":{"endpoint":"/api/users","method":"GET","resultPath":"","autoLoad":true,
                        "table":"TB_USER"},
                """));

        String controller = read(out, CONTROLLER);
        assertTrue(controller.contains("public List<Map<String, Object>> list()"),
                "resultPath가 없으면 배열을 그대로 반환");
        assertFalse(controller.contains("@PathVariable"), "단건 핸들러 없음");
        assertFalse(read(out, MAPPER).contains("selectOne"));
        assertFalse(read(out, MAPPER_XML).contains("selectOne"));
    }

    /** 🔒 게이트 미통과(악성/형식위반)는 승격 0 — 기존 빈 stub으로 수렴한다. */
    @Test
    void 악성_식별자와_경로는_승격되지_않고_stub으로_폴백한다() throws Exception {
        // (1) 테이블명에 SQL 조각
        assertFallsBackToStub(generate("badTable", definition("""
                "data":{"endpoint":"/api/users","method":"GET","resultPath":"items","autoLoad":true,
                        "table":"TB_USER; DROP TABLE TB_ADMIN"},
                """)));

        // (2) endpoint에 쿼리스트링/특수문자(§14.2의 좁힌 재검증에서 탈락)
        assertFallsBackToStub(generate("badEndpoint", definition("""
                "data":{"endpoint":"/api/users?x=1","method":"GET","resultPath":"items","autoLoad":true,
                        "table":"TB_USER"},
                """)));

        // (3) 유효 컬럼 0(전부 형식 위반) → SELECT를 만들 수 없으므로 승격 중단
        Path out = generate("badColumns", """
                {"schemaVersion":1,"archetype":"MGMT_LIST_DETAIL","stem":"userMgmt","role":"admin","slots":{
                  "listArea":[{"instanceId":"table_1","moduleTypeCode":"TABLE_VIEW",
                    "props":{"columns":[{"name":"USER_ID; --","displayName":"ID"}]},
                    "data":{"endpoint":"/api/users","method":"GET","resultPath":"items","autoLoad":true,
                            "table":"TB_USER"}}]}}
                """);
        assertFallsBackToStub(out);
    }

    /** 바인딩 선언이 없는 기존 화면은 stub 산출이 <b>바이트 그대로</b>여야 한다(골든 무손상 근거). */
    @Test
    void 바인딩이_없으면_기존_stub과_바이트가_같다() throws Exception {
        Path out = generate("noBinding", """
                {"schemaVersion":1,"archetype":"MGMT_LIST_DETAIL","stem":"userMgmt","role":"admin","slots":{
                  "listArea":[{"instanceId":"table_1","moduleTypeCode":"TABLE_VIEW",
                    "props":{"columns":[{"name":"USER_ID","displayName":"ID"}]}}]}}
                """);

        // P12(계약 §16.4)로 보호구역 마커가 stub에 추가됐다 — 승격이 아닌 stub 형태 자체는 이 한 벌로 고정.
        assertEquals("""
                package com.jworks.forge.userMgmt;

                // TODO(J-FORGE stub): UserMgmt 화면 컨트롤러 — 실 로직은 수작업으로 채운다.
                // (요청 매핑/서비스 주입/뷰 반환 등은 생성 후 개발자가 구현)
                public class UserMgmtController {

                    // TODO: @RequestMapping 핸들러 추가

                    // <j-forge:custom id="body">
                    // 이 사이에 쓴 코드는 재생성해도 보존됩니다.
                    // </j-forge:custom>
                }
                """, read(out, CONTROLLER));
    }

    /** 승격은 파일 '내용'만 바꾼다 — dry-run 경로 계획은 불변(드리프트 회귀망 보존). */
    @Test
    void 승격해도_dry_run_경로_계획은_동일하다() throws Exception {
        var pathSafety = new PathSafetyService();
        var writer = new AtomicFileWriter();
        var contextBuilder = new TemplateContextBuilder(new ObjectMapper());
        var stubGenerator = new StubGenerator(pathSafety, writer);

        List<String> withBinding = stubPaths(planner(contextBuilder, pathSafety, stubGenerator,
                "planBound", definition("""
                        "data":{"endpoint":"/api/users","method":"GET","resultPath":"items","autoLoad":true,
                                "table":"TB_USER","keyColumn":"USER_ID"},
                        """)));
        List<String> withoutBinding = stubPaths(planner(contextBuilder, pathSafety, stubGenerator,
                "planPlain", """
                        {"schemaVersion":1,"archetype":"MGMT_LIST_DETAIL","stem":"userMgmt","role":"admin","slots":{
                          "listArea":[{"instanceId":"table_1","moduleTypeCode":"TABLE_VIEW",
                            "props":{"columns":[{"name":"USER_ID","displayName":"ID"}]}}]}}
                        """));

        assertEquals(withoutBinding, withBinding, "stub 3종의 경로·순서가 승격 여부와 무관해야 한다");
        assertEquals(3, withBinding.size());
    }

    // ---------------------------------------------------------------- helpers

    /** 승격 없이 P4-6 빈 stub이 나왔는지(3종 모두). */
    private void assertFallsBackToStub(Path out) throws Exception {
        assertTrue(read(out, CONTROLLER).contains("TODO(J-FORGE stub)"), "컨트롤러 stub 폴백");
        assertFalse(read(out, CONTROLLER).contains("@RestController"), "승격 산출 0");
        assertTrue(read(out, MAPPER).contains("TODO(J-FORGE stub)"), "매퍼 stub 폴백");
        assertTrue(read(out, MAPPER_XML).contains("TODO(J-FORGE stub)"), "매퍼 XML stub 폴백");
        // 빈 stub의 TODO 주석에도 "SELECT * 금지"·"<select>" 문구가 있으므로 실 구문으로 판정한다.
        assertFalse(read(out, MAPPER_XML).contains("<select id="), "SQL 구문 산출 0");
        assertFalse(read(out, MAPPER_XML).contains("FROM "), "테이블 참조 0");
    }

    /** listArea[0]에 data 블록을 끼운 표준 정의(컬럼 2개). */
    private String definition(String dataBlock) {
        return """
                {"schemaVersion":1,"archetype":"MGMT_LIST_DETAIL","stem":"userMgmt","role":"admin","slots":{
                  "listArea":[{"instanceId":"table_1","moduleTypeCode":"TABLE_VIEW",
                    "props":{"columns":[{"name":"USER_ID","displayName":"아이디"},
                                        {"name":"USER_NM","displayName":"이름"}]},
                    """ + dataBlock + """
                    "events":[]}]}}
                """;
    }

    /** 케이스별 타겟 루트에 실제 생성 후 그 루트를 돌려준다. */
    private Path generate(String caseName, String definitionJson) throws Exception {
        Path out = Files.createDirectories(root.resolve(caseName));
        ForgeScreenService screens = mock(ForgeScreenService.class);
        ForgeProjectService projects = mock(ForgeProjectService.class);
        when(screens.get(10L)).thenReturn(screen(definitionJson));
        when(projects.get(1L)).thenReturn(project(out));

        var pathSafety = new PathSafetyService();
        var writer = new AtomicFileWriter();
        var generator = new ScreenGenerator(screens, projects, new TemplateContextBuilder(new ObjectMapper()),
                new TemplateRenderer(new CodeGenTemplateConfig().codeGenFreemarkerConfiguration()),
                pathSafety, writer, new RuntimeSyncer(pathSafety, writer), new StubGenerator(pathSafety, writer));

        assertEquals(GenResult.SUCCESS, generator.generate(10L).resultCode());
        return out;
    }

    /** 같은 정의로 dry-run 계획만 뽑는다(파일쓰기 0). */
    private GenPlanner.GenPlan planner(TemplateContextBuilder contextBuilder, PathSafetyService pathSafety,
                                       StubGenerator stubGenerator, String caseName, String definitionJson)
            throws Exception {
        Path out = Files.createDirectories(root.resolve(caseName));
        ForgeScreenService screens = mock(ForgeScreenService.class);
        ForgeProjectService projects = mock(ForgeProjectService.class);
        when(screens.get(10L)).thenReturn(screen(definitionJson));
        when(projects.get(1L)).thenReturn(project(out));
        return new GenPlanner(screens, projects, contextBuilder, pathSafety, stubGenerator,
                mock(com.jworks.forge.gen.hist.GenHistMapper.class), new ObjectMapper()).plan(10L);
    }

    private List<String> stubPaths(GenPlanner.GenPlan plan) {
        return plan.files().stream()
                .map(f -> f.relativePath())
                .filter(p -> p.endsWith("Controller.java") || p.endsWith("Mapper.java") || p.endsWith("Mapper.xml"))
                .toList();
    }

    private String read(Path out, String relativePath) throws Exception {
        return Files.readString(out.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private ForgeScreen screen(String definitionJson) {
        ForgeScreen screen = new ForgeScreen();
        screen.setScreenId(10L);
        screen.setProjectId(1L);
        screen.setStem("userMgmt");
        screen.setRoleCode("admin");
        screen.setArchetypeCode("MGMT_LIST_DETAIL");
        screen.setDefinitionJson(definitionJson);
        return screen;
    }

    private ForgeProject project(Path targetRoot) {
        ForgeProject project = new ForgeProject();
        project.setProjectId(1L);
        project.setTargetRootPath(targetRoot.toString());
        project.setPackageBase("com.jworks.forge");
        project.setJspBasePath("WEB-INF/views");
        project.setJsBasePath("static/js");
        project.setCssBasePath("static/css");
        project.setRuntimeVer("1.0.0");
        return project;
    }
}
