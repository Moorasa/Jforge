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
 * 🔒 계약 §19 — LAYOUT_FRAME 의 {@code frameSrc} → iframe src 산출 게이트.
 *
 * <p>게이트는 {@code common/frameSrc.ftl} 단일 소스이며 DUAL_LAYOUT 패인(§10)과
 * FREE_CANVAS 파셜(§17.9)이 함께 쓴다. 통과하지 못한 값은 <b>src 속성 자체가 없다</b>
 * (0바이트 폴백) — 원문이 새어나가는 경로가 없어야 한다.
 */
class FrameSrcGenerationTest {

    @TempDir
    Path rootDualOk;
    @TempDir
    Path rootDualNone;
    @TempDir
    Path rootCanvasOk;
    @TempDir
    Path rootEvil;

    /** 게이트를 통과해선 안 되는 값들 — 전부 src 미산출이어야 한다. */
    private static final String[] EVIL = {
        "javascript:alert(1)",
        "JaVaScRiPt:alert(1)",
        "data:text/html;base64,PHNjcmlwdD4=",
        "//evil.example.com/steal",
        "https://evil.example.com/steal",
        "/ok/../../etc/passwd",
        "/ok path/with space",
        "/ok\"onload=\"alert(1)",
        "/ok?q=1&x=2",
        "/ok%2e%2e/",
        "/ok\\windows\\path",
        "relative/no/slash",
        ""
    };

    // ---------- DUAL_LAYOUT ----------

    @Test
    void 유효한_frameSrc는_같은출처_절대경로로_산출된다() throws IOException {
        GenResult r = generate(rootDualOk, "DUAL_LAYOUT", "orgDual", dualDef("/admin/userMgmt"));
        assertEquals(GenResult.SUCCESS, r.resultCode());

        String jsp = readJsp(rootDualOk, "orgDual");
        assertTrue(jsp.contains("src=\"${ctx}/admin/userMgmt\""), "패인 src 산출 누락:\n" + jsp);
        // 컨텍스트 경로 접두는 템플릿 리터럴이다 — 절대 URL 이 통째로 들어가지 않는다.
        assertFalse(jsp.contains("src=\"/admin/userMgmt\""), "ctx 접두 없이 산출됨:\n" + jsp);
    }

    @Test
    void frameSrc가_없으면_종전대로_src없는_빈_iframe이다() throws IOException {
        GenResult r = generate(rootDualNone, "DUAL_LAYOUT", "orgDual", dualDef(null));
        assertEquals(GenResult.SUCCESS, r.resultCode());

        String frames = iframesOf(readJsp(rootDualNone, "orgDual"));
        assertTrue(frames.contains("class=\"dual-frame\""), "패인 iframe 누락:\n" + frames);
        assertFalse(frames.contains("src="), "frameSrc 가 없는데 src 가 산출됨:\n" + frames);
    }

    @Test
    void 게이트를_통과못한_frameSrc는_한글자도_산출되지_않는다() throws IOException {
        for (String evil : EVIL) {
            Path root = Files.createTempDirectory(rootEvil, "evil");
            GenResult r = generate(root, "DUAL_LAYOUT", "orgDual", dualDef(evil));
            assertEquals(GenResult.SUCCESS, r.resultCode(), "생성 자체는 성공해야 한다: " + evil);

            String jsp = readJsp(root, "orgDual");
            String frames = iframesOf(jsp);
            assertFalse(frames.contains("src="), "src 가 산출됨(payload=" + evil + "):\n" + frames);
            // 원문 조각이 문서 어디에도(주석·data-* 포함) 남지 않는다.
            assertFalse(jsp.contains("javascript:"), "javascript: 유출(payload=" + evil + ")");
            assertFalse(jsp.contains("data:text"), "data: URL 유출(payload=" + evil + ")");
            assertFalse(jsp.contains("evil.example.com"), "외부 호스트 유출(payload=" + evil + ")");
            assertFalse(jsp.contains("onload="), "이벤트 핸들러 유출(payload=" + evil + ")");
            // `..` 는 shell 이 원래 갖는 상대 include 경로에도 나오므로 프레임 태그로만 본다.
            assertFalse(frames.contains(".."), "상위경로 지시 유출(payload=" + evil + "):\n" + frames);
        }
    }

    // ---------- FREE_CANVAS ----------

    @Test
    void 캔버스_LAYOUT_FRAME도_같은_게이트를_쓴다() throws IOException {
        String def = """
                {
                  "schemaVersion": 1, "archetype": "FREE_CANVAS", "stem": "freeFrame", "role": "admin",
                  "canvas": { "widthPx": 1280, "heightPx": 800 },
                  "slots": { "canvasArea": [
                    { "instanceId": "frame_1", "moduleTypeCode": "LAYOUT_FRAME",
                      "props": { "frameId": "mainFrame", "title": "본문",
                                 "frameSrc": "/admin/boardList",
                                 "layoutXPx": 20, "layoutYPx": 20, "layoutWPx": 600, "layoutHPx": 400 } },
                    { "instanceId": "frame_2", "moduleTypeCode": "LAYOUT_FRAME",
                      "props": { "title": "빈 프레임",
                                 "frameSrc": "javascript:alert(1)",
                                 "layoutXPx": 640, "layoutYPx": 20, "layoutWPx": 300, "layoutHPx": 400 } }
                  ] }
                }
                """;
        GenResult r = generate(rootCanvasOk, "FREE_CANVAS", "freeFrame", def);
        assertEquals(GenResult.SUCCESS, r.resultCode());

        String jsp = readJsp(rootCanvasOk, "freeFrame");
        String frames = iframesOf(jsp);
        assertTrue(frames.contains("src=\"${ctx}/admin/boardList\""), "캔버스 프레임 src 누락:\n" + frames);
        assertFalse(jsp.contains("javascript:"), "javascript: 유출:\n" + jsp);
        // 프레임 2개 중 게이트를 통과한 1개만 src 를 갖는다.
        assertEquals(2, countOccurrences(frames, "<iframe"), "프레임 개수:\n" + frames);
        assertEquals(1, countOccurrences(frames, "src=\""), "src 산출 개수:\n" + frames);
    }

    // ---------- §19.4 frameParams ----------

    @Test
    void 파라미터는_생성기가_인코딩해_쿼리로_붙는다() throws IOException {
        Path root = Files.createTempDirectory(rootEvil, "q");
        GenResult r = generate(root, "DUAL_LAYOUT", "orgDual", dualDefWithParams("/admin/boardList",
                "{ \"name\": \"deptId\", \"value\": \"3\" }, "
                        + "{ \"name\": \"useYn\", \"value\": \"Y\" }"));
        assertEquals(GenResult.SUCCESS, r.resultCode());

        String frames = iframesOf(readJsp(root, "orgDual"));
        // HTML 속성이므로 & 는 &amp; 로 나가는 게 정답이다(브라우저가 & 로 되돌려 요청한다).
        assertTrue(frames.contains("src=\"${ctx}/admin/boardList?deptId=3&amp;useYn=Y\""),
                "쿼리 산출:\n" + frames);
    }

    /** 🔒 값이 파라미터 경계를 깨지 못한다 — 밀수 차단의 핵심. */
    @Test
    void 값에_들어간_구분자는_인코딩되어_파라미터를_늘리지_못한다() throws IOException {
        Path root = Files.createTempDirectory(rootEvil, "smuggle");
        GenResult r = generate(root, "DUAL_LAYOUT", "orgDual", dualDefWithParams("/admin/boardList",
                "{ \"name\": \"q\", \"value\": \"A&admin=true\" }"));
        assertEquals(GenResult.SUCCESS, r.resultCode());

        String frames = iframesOf(readJsp(root, "orgDual"));
        assertTrue(frames.contains("q=A%26admin%3Dtrue"), "값이 인코딩돼야 한다:\n" + frames);
        // 인코딩됐으므로 admin 이라는 **별도 파라미터**는 존재하지 않는다.
        assertFalse(frames.contains("&amp;admin=true"), "파라미터 밀수 성공:\n" + frames);
        assertFalse(frames.contains("&admin=true"), "파라미터 밀수 성공:\n" + frames);
    }

    @Test
    void 한글과_공백도_인코딩된다() throws IOException {
        Path root = Files.createTempDirectory(rootEvil, "ko");
        GenResult r = generate(root, "DUAL_LAYOUT", "orgDual", dualDefWithParams("/admin/boardList",
                "{ \"name\": \"kw\", \"value\": \"공지 사항\" }"));
        assertEquals(GenResult.SUCCESS, r.resultCode());

        String frames = iframesOf(readJsp(root, "orgDual"));
        assertTrue(frames.contains("kw=%EA%B3%B5%EC%A7%80%20%EC%82%AC%ED%95%AD")
                        || frames.contains("kw=%EA%B3%B5%EC%A7%80+%EC%82%AC%ED%95%AD"),
                "UTF-8 퍼센트 인코딩:\n" + frames);
        assertFalse(frames.contains("공지 사항"), "원문이 그대로 나감:\n" + frames);
    }

    /**
     * 🔒 fail-closed — 파라미터가 하나라도 어긋나면 src 전체를 버린다.
     * 어긋난 것만 빼면 빠진 게 필터일 때 **의도보다 넓은 데이터**가 노출된다.
     */
    @Test
    void 파라미터가_하나라도_어긋나면_src를_통째로_버린다() throws IOException {
        String[] badParams = {
            "{ \"name\": \"1bad\", \"value\": \"x\" }",                  // 숫자로 시작
            "{ \"name\": \"a-b\", \"value\": \"x\" }",                   // 하이픈
            "{ \"name\": \"ok\", \"value\": { \"nested\": 1 } }",        // 값이 객체
            "{ \"name\": \"ok\", \"value\": \"" + "x".repeat(201) + "\" }", // 200자 초과
            "{ \"value\": \"x\" }",                                      // 이름 없음
            "\"notAnObject\""                                            // 원소가 객체가 아님
        };
        for (String bad : badParams) {
            Path root = Files.createTempDirectory(rootEvil, "bad");
            GenResult r = generate(root, "DUAL_LAYOUT", "orgDual",
                    dualDefWithParams("/admin/boardList",
                            "{ \"name\": \"deptId\", \"value\": \"3\" }, " + bad));
            assertEquals(GenResult.SUCCESS, r.resultCode(), "생성 자체는 성공: " + bad);

            String frames = iframesOf(readJsp(root, "orgDual"));
            assertFalse(frames.contains("src="),
                    "어긋난 파라미터인데 src 가 남았다(param=" + bad + "):\n" + frames);
            // 정상 파라미터만 붙은 "더 넓은" URL 이 만들어지지 않았는지 확인.
            assertFalse(frames.contains("deptId=3"), "부분 산출됨(param=" + bad + "):\n" + frames);
        }
    }

    @Test
    void 파라미터_20쌍_초과는_거부된다() throws IOException {
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 21; i++) {
            if (i > 0) { many.append(", "); }
            many.append("{ \"name\": \"p").append(i).append("\", \"value\": \"1\" }");
        }
        Path root = Files.createTempDirectory(rootEvil, "many");
        GenResult r = generate(root, "DUAL_LAYOUT", "orgDual",
                dualDefWithParams("/admin/boardList", many.toString()));
        assertEquals(GenResult.SUCCESS, r.resultCode());
        assertFalse(iframesOf(readJsp(root, "orgDual")).contains("src="), "20쌍 초과가 통과됨");
    }

    @Test
    void 파라미터가_없으면_종전과_같다() throws IOException {
        Path root = Files.createTempDirectory(rootEvil, "noparam");
        GenResult r = generate(root, "DUAL_LAYOUT", "orgDual", dualDef("/admin/boardList"));
        assertEquals(GenResult.SUCCESS, r.resultCode());
        String frames = iframesOf(readJsp(root, "orgDual"));
        assertTrue(frames.contains("src=\"${ctx}/admin/boardList\""), "경로만 산출:\n" + frames);
        assertFalse(frames.contains("?"), "쿼리가 붙으면 안 된다:\n" + frames);
    }

    // ---------- helpers ----------

    /**
     * iframe 태그만 뽑는다. shell 은 자기 스크립트를 {@code <script src=...>} 로 싣기 때문에
     * 문서 전체에서 "src=" 를 세면 그것까지 걸린다 — 검사 대상은 프레임뿐이다.
     */
    private static String iframesOf(String jsp) {
        StringBuilder sb = new StringBuilder();
        for (int i = jsp.indexOf("<iframe"); i >= 0; i = jsp.indexOf("<iframe", i + 7)) {
            int end = jsp.indexOf('>', i);
            sb.append(jsp, i, end < 0 ? jsp.length() : end + 1).append('\n');
        }
        return sb.toString();
    }

    private static int countOccurrences(String body, String needle) {
        int n = 0;
        for (int i = body.indexOf(needle); i >= 0; i = body.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }

    /** frameSrc + frameParams(원소 JSON 나열)를 가진 DUAL 정의. */
    private static String dualDefWithParams(String frameSrc, String paramsJson) {
        return """
                {
                  "schemaVersion": 1, "archetype": "DUAL_LAYOUT", "stem": "orgDual", "role": "admin",
                  "slots": {
                    "leftArea": [
                      { "instanceId": "layoutFrame_1", "moduleTypeCode": "LAYOUT_FRAME",
                        "props": { "frameId": "leftFrame", "title": "좌측",
                                   "frameSrc": "%s",
                                   "frameParams": [ %s ] } }
                    ],
                    "rightArea": []
                  }
                }
                """.formatted(frameSrc, paramsJson);
    }

    /** frameSrc 가 null 이면 키 자체를 넣지 않는다(§19 이전 정의와 동형). */
    private static String dualDef(String frameSrc) {
        String srcProp = frameSrc == null ? ""
                : ", \"frameSrc\": \"" + frameSrc.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        return """
                {
                  "schemaVersion": 1, "archetype": "DUAL_LAYOUT", "stem": "orgDual", "role": "admin",
                  "slots": {
                    "leftArea": [
                      { "instanceId": "layoutFrame_1", "moduleTypeCode": "LAYOUT_FRAME",
                        "props": { "frameId": "leftFrame", "title": "좌측"%s } }
                    ],
                    "rightArea": []
                  }
                }
                """.formatted(srcProp);
    }

    private String readJsp(Path root, String stem) throws IOException {
        Path p = root.resolve("WEB-INF/views/admin/" + stem + "/" + stem + ".jsp");
        assertTrue(Files.exists(p), "산출 JSP 없음: " + p);
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private GenResult generate(Path targetRoot, String archetype, String stem, String definitionJson) {
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
        s.setScreenId(40L);
        s.setProjectId(1L);
        s.setStem(stem);
        s.setRoleCode("admin");
        s.setArchetypeCode(archetype);
        s.setDefinitionJson(definitionJson);

        ForgeProject p = new ForgeProject();
        p.setProjectId(1L);
        p.setTargetRootPath(targetRoot.toString());
        p.setPackageBase("com.jworks.forge");
        p.setJspBasePath("WEB-INF/views");
        p.setJsBasePath("static/js");
        p.setCssBasePath("static/css");
        p.setRuntimeVer("1.0.0");

        when(screenService.get(40L)).thenReturn(s);
        when(projectService.get(1L)).thenReturn(p);
        return new ScreenGenerator(screenService, projectService, contextBuilder, renderer,
                pathSafety, fileWriter, runtimeSyncer, stubGenerator).generate(40L);
    }
}
