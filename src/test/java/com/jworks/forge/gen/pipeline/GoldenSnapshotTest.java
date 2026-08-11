package com.jworks.forge.gen.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * P4-7 골든파일 스냅샷 검증 (MGMT_LIST_DETAIL + TABLE_VIEW 7종).
 *
 * <p>고정 입력({@link #DEFINITION_JSON} + 고정 프로젝트 메타)을 격리된 {@code @TempDir} 타겟 루트로
 * 실제 파이프라인({@link ScreenGenerator})으로 생성한 뒤, 산출 7종을
 * {@code src/test/resources/golden/mgmtListDetail_tableView/} 의 기대 산출물과
 * <b>개행 정규화(CRLF→LF) 후</b> 바이트 비교한다. 불일치 시 어느 파일·어느 라인이
 * 어긋났는지 진단 출력하며 실패한다.
 *
 * <h2>결정성(재현성)</h2>
 * <ul>
 *   <li>입력은 고정 상수. 타임스탬프·랜덤·절대경로가 산출 본문에 들어가지 않는다.</li>
 *   <li>백업 파일({@code .bak-{ts}})은 골든 비교 대상에서 제외 — 생성 파일 본문만 비교.</li>
 *   <li>비교 전 CRLF/CR → LF 정규화로 OS 개행 차이를 흡수한다.</li>
 * </ul>
 *
 * <h2>🔒 골든 안전성</h2>
 * 골든은 실제 안전 산출물이어야 한다: JWORKS 배너 0, JSP 스크립트릿 0, jQuery 3.7.1,
 * JS 네임스페이스+IIFE 존재. 본 테스트가 매 실행마다 이 속성을 자동 단언한다.
 *
 * <h2>골든 갱신 절차 (의도된 템플릿 변경 시에만)</h2>
 * <pre>
 *   # 1) 템플릿 변경이 의도된 것인지 반드시 리뷰한 뒤에만 갱신한다(무심코 덮어쓰기 금지).
 *   mvn -Dtest=GoldenSnapshotTest -Dforge.golden.update=true test
 *   # 2) git diff 로 산출 변경을 눈으로 검토(배너/스크립트릿/이스케이프 회귀 없는지).
 *   # 3) 검토 통과 시에만 golden/ 리소스를 커밋한다.
 * </pre>
 * 자세한 내용은 {@code src/test/resources/golden/README.md} 참조.
 */
class GoldenSnapshotTest {

    /** 갱신 모드 스위치. {@code -Dforge.golden.update=true} 일 때만 골든을 다시 쓴다. */
    private static final boolean UPDATE_GOLDEN =
            Boolean.getBoolean("forge.golden.update");

    private static final String GOLDEN_DIR = "golden/mgmtListDetail_tableView";

    /** 고정 입력(계약 §6 예시 상당): MGMT_LIST_DETAIL + listArea TABLE_VIEW 1뷰. */
    private static final String DEFINITION_JSON = """
            {
              "schemaVersion": 1,
              "archetype": "MGMT_LIST_DETAIL",
              "stem": "userMgmt",
              "role": "admin",
              "slots": {
                "searchArea": [
                  { "instanceId": "searchFilterBar_1", "moduleTypeCode": "SEARCH_FILTER_BAR",
                    "props": { "filters": [ { "name": "useYn", "label": "사용여부", "options": "Y:사용,N:미사용" } ],
                               "keywordYn": true, "dateRangeYn": false } }
                ],
                "listToolbar": [
                  { "instanceId": "toolbar_1", "moduleTypeCode": "TOOLBAR",
                    "props": { "buttons": [
                      { "actionCode": "add", "label": "추가", "styleClass": "btn-primary" },
                      { "actionCode": "delete", "label": "삭제", "styleClass": "btn-secondary" } ] } }
                ],
                "listArea": [
                  { "instanceId": "tableView_1", "moduleTypeCode": "TABLE_VIEW",
                    "props": { "columns": [
                      { "name": "userId", "displayName": "사용자ID", "displayYn": true, "sortYn": true },
                      { "name": "userName", "displayName": "이름", "displayYn": true, "sortYn": true },
                      { "name": "regDtm", "displayName": "등록일시", "displayYn": true, "sortYn": false } ],
                      "selectMode": "checkbox", "pagingYn": true, "excelYn": true, "csvYn": false } }
                ],
                "detailBasic": [],
                "detailTabs": []
              }
            }
            """;

    /** 골든으로 고정하는 화면 산출 7종: (relativePath, golden 파일명). */
    private static final Map<String, String> GOLDEN_FILES = new LinkedHashMap<>();
    static {
        GOLDEN_FILES.put("WEB-INF/views/admin/userMgmt/userMgmt.jsp", "userMgmt.jsp");
        GOLDEN_FILES.put("WEB-INF/views/admin/userMgmt/userMgmtList.jsp", "userMgmtList.jsp");
        GOLDEN_FILES.put("static/js/admin/userMgmt/userMgmtList.js", "userMgmtList.js");
        GOLDEN_FILES.put("static/css/admin/userMgmt/userMgmtList.css", "userMgmtList.css");
        GOLDEN_FILES.put("WEB-INF/views/admin/userMgmt/userMgmtListTableView.jsp",
                "userMgmtListTableView.jsp");
        GOLDEN_FILES.put("static/js/admin/userMgmt/userMgmtListTableView.js",
                "userMgmtListTableView.js");
        // P6-2: 뷰 CSS는 공통추출(commonScreenLayout.css) → per-screen 뷰 CSS 미산출(골든 제외).
    }

    @TempDir
    Path targetRoot;

    private ScreenGenerator newGenerator() {
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
        when(projectService.get(1L)).thenReturn(project());
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

    // ------------------------------------------------------------------
    // 스냅샷 비교
    // ------------------------------------------------------------------

    @Test
    void 골든_스냅샷_7종이_현재_산출과_정규화_후_일치한다() throws IOException {
        GenResult result = newGenerator().generate(10L);
        assertEquals(GenResult.SUCCESS, result.resultCode(),
                "고정 입력은 SUCCESS 여야 한다: " + result.failReason());

        if (UPDATE_GOLDEN) {
            updateGolden();
            return; // 갱신 모드에서는 비교하지 않는다(리뷰 후 커밋).
        }

        Path goldenRoot = goldenSourceDir();
        for (Map.Entry<String, String> e : GOLDEN_FILES.entrySet()) {
            String rel = e.getKey();
            Path produced = targetRoot.resolve(rel);
            assertTrue(Files.exists(produced), "산출 파일 없음: " + rel);

            String actual = normalize(Files.readString(produced, StandardCharsets.UTF_8));
            Path goldenFile = goldenRoot.resolve(e.getValue());
            assertTrue(Files.exists(goldenFile),
                    "골든 파일 없음(갱신 모드로 생성 필요): " + goldenFile);
            String expected = normalize(Files.readString(goldenFile, StandardCharsets.UTF_8));

            if (!expected.equals(actual)) {
                fail(diagnose(e.getValue(), expected, actual));
            }
        }
    }

    /** 🔒 골든이 실제 안전 산출물인지(배너 0 / 스크립트릿 0 / jQuery 3.7.1 / JS 네임스페이스+IIFE) 매 실행 단언. */
    @Test
    void 골든은_안전_산출물이다_배너0_스크립트릿0_이스케이프됨() throws IOException {
        // 산출을 생성해 검사(골든 파일이 곧 이 산출의 스냅샷이므로 동치).
        newGenerator().generate(10L);
        Map<String, String> bodies = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : GOLDEN_FILES.entrySet()) {
            Path produced = targetRoot.resolve(e.getKey());
            bodies.put(e.getValue(), normalize(Files.readString(produced, StandardCharsets.UTF_8)));
        }

        for (Map.Entry<String, String> e : bodies.entrySet()) {
            String name = e.getKey();
            String body = e.getValue();
            assertFalse(body.contains("JWORKS"), name + ": JWORKS 배너 유출");
            assertFalse(body.toUpperCase().contains("COPYRIGHT"), name + ": 저작권 배너 유출");
            if (name.endsWith(".jsp")) {
                assertScriptletFree(name, body);
            }
        }

        // jQuery 3.7.1 확정(shell 매니페스트/헤더 경유). 다른 버전 참조 금지.
        String shell = bodies.get("userMgmt.jsp");
        assertFalse(containsOtherJqueryVersion(shell),
                "shell 에 3.7.1 이외 jQuery 버전 참조");

        // JS 네임스페이스 + IIFE 골격.
        String listJs = bodies.get("userMgmtList.js");
        assertTrue(listJs.contains("window.JWorks_JSUserMgmtAdmin"), "listJs 네임스페이스");
        assertTrue(listJs.contains("__defined"), "listJs IIFE 골격");
        String tvJs = bodies.get("userMgmtListTableView.js");
        assertTrue(tvJs.contains("window.JWorks_JSUserMgmtAdminTableView"), "tableViewJs 네임스페이스");
        assertTrue(tvJs.contains("__defined"), "tableViewJs IIFE 골격");
    }

    /** 결정성/멱등: 같은 입력 2회 생성 → 산출 본문 동일(백업 제외). */
    @Test
    void 같은_입력_2회_생성시_산출_본문이_동일하다() throws IOException {
        Map<String, String> first = generateAndReadBodies();
        Map<String, String> second = generateAndReadBodies();
        for (String rel : GOLDEN_FILES.keySet()) {
            assertEquals(first.get(rel), second.get(rel),
                    "2회 생성 본문이 다름(결정성 위반): " + rel);
        }
    }

    private Map<String, String> generateAndReadBodies() throws IOException {
        newGenerator().generate(10L);
        Map<String, String> bodies = new LinkedHashMap<>();
        for (String rel : GOLDEN_FILES.keySet()) {
            Path produced = targetRoot.resolve(rel);
            bodies.put(rel, normalize(Files.readString(produced, StandardCharsets.UTF_8)));
        }
        return bodies;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** CRLF/CR → LF 정규화(OS 개행 차이 흡수). 골든/산출 양쪽에 동일 적용. */
    private static String normalize(String s) {
        return s.replace("\r\n", "\n").replace("\r", "\n");
    }

    /** 3.7.1 이외의 jquery-x.y.z 참조가 있는지. */
    private static boolean containsOtherJqueryVersion(String body) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("jquery-(\\d+\\.\\d+\\.\\d+)").matcher(body);
        while (m.find()) {
            if (!"3.7.1".equals(m.group(1))) {
                return true;
            }
        }
        return false;
    }

    private static void assertScriptletFree(String name, String body) {
        assertFalse(body.matches("(?s).*<%[^@\\-].*"), name + ": 스크립트릿(<% ) 발견");
        assertFalse(body.contains("<%="), name + ": 표현식 스크립트릿(<%=) 발견");
        assertFalse(body.contains("<%!"), name + ": 선언 스크립트릿(<%!) 발견");
    }

    /** 첫 불일치 라인을 짚어 진단 문자열을 만든다. */
    private static String diagnose(String fileName, String expected, String actual) {
        String[] exp = expected.split("\n", -1);
        String[] act = actual.split("\n", -1);
        int n = Math.min(exp.length, act.length);
        StringBuilder sb = new StringBuilder();
        sb.append("골든 불일치: ").append(fileName)
                .append(" (expected ").append(exp.length)
                .append(" lines, actual ").append(act.length).append(" lines)\n");
        for (int i = 0; i < n; i++) {
            if (!exp[i].equals(act[i])) {
                sb.append("  first diff at line ").append(i + 1).append(":\n");
                sb.append("    expected: ").append(exp[i]).append('\n');
                sb.append("    actual  : ").append(act[i]).append('\n');
                return sb.toString();
            }
        }
        if (exp.length != act.length) {
            sb.append("  라인 수가 다름(추가/삭제된 라인). expected=")
                    .append(exp.length).append(", actual=").append(act.length);
        }
        return sb.toString();
    }

    /** {@code src/test/resources/golden/...} 실제 소스 경로(갱신용). */
    private Path goldenSourceDir() {
        Path fromCp = classpathGoldenDir();
        if (fromCp != null) {
            return fromCp;
        }
        // 폴백: 작업 디렉터리 기준 소스 트리.
        return Paths.get("src", "test", "resources", GOLDEN_DIR);
    }

    private Path classpathGoldenDir() {
        var url = getClass().getClassLoader().getResource(GOLDEN_DIR);
        if (url == null) {
            return null;
        }
        try {
            return Paths.get(url.toURI());
        } catch (Exception ex) {
            return null;
        }
    }

    /** 갱신 모드: 산출 7종을 소스 트리 golden 디렉터리에 LF 정규화하여 기록. */
    private void updateGolden() {
        Path dir = Paths.get("src", "test", "resources", GOLDEN_DIR);
        try {
            Files.createDirectories(dir);
            for (Map.Entry<String, String> e : GOLDEN_FILES.entrySet()) {
                Path produced = targetRoot.resolve(e.getKey());
                String body = normalize(Files.readString(produced, StandardCharsets.UTF_8));
                Files.writeString(dir.resolve(e.getValue()), body, StandardCharsets.UTF_8);
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
        List<String> written = GOLDEN_FILES.values().stream().toList();
        System.out.println("[golden-update] wrote " + written.size()
                + " files to " + dir.toAbsolutePath() + ": " + written);
    }
}
