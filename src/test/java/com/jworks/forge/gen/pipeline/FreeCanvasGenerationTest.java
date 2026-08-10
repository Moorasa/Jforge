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
 * §17(P13) FREE_CANVAS 자유 배치 산출 검증.
 *
 * <p>계약 §17 자기점검 항목을 고정한다:
 * <ol>
 *   <li>유효 좌표 4키 → {stem}.css 에 {@code .frg-fc-N} 절대좌표 규칙 산출.</li>
 *   <li>🔒 악성 문자열 좌표 → <b>0바이트</b>(문자열 미삽입 — 인젝션 차단).</li>
 *   <li>범위 밖 숫자 → 0바이트.</li>
 *   <li>좌표 키 부재 → 좌표 규칙 없음(마크업은 정상 산출 — 안전측 폴백).</li>
 *   <li>같은 모듈 타입을 N개 배치해도 <b>산출 파일은 3개 고정</b>(§17.4 파일명 충돌 0).</li>
 * </ol>
 */
class FreeCanvasGenerationTest {

    @TempDir
    Path rootValid;
    @TempDir
    Path rootEvil;
    @TempDir
    Path rootPlain;
    @TempDir
    Path rootRange;
    @TempDir
    Path rootDup;
    @TempDir
    Path rootAll;
    @TempDir
    Path rootNest;
    @TempDir
    Path rootCycle;
    @TempDir
    Path rootBadParent;
    @TempDir
    Path rootWidget;
    @TempDir
    Path rootLayer;
    @TempDir
    Path rootIndent;

    private static String definition(String items, String canvasNode) {
        return """
                {
                  "schemaVersion": 1, "archetype": "FREE_CANVAS", "stem": "freeScreen", "role": "admin",
                """ + canvasNode + """
                  "slots": { "canvasArea": [ """ + items + " ] }\n}";
    }

    private static String item(String instanceId, String moduleTypeCode, String propsExtra) {
        return "{ \"instanceId\": \"" + instanceId + "\", \"moduleTypeCode\": \"" + moduleTypeCode
                + "\", \"props\": { \"text\": \"확인\"" + propsExtra + " } }";
    }

    /** 좌표 4키를 갖춘 인스턴스(부모 지정 가능). */
    private static String placed(String id, String code, int x, int y, String parentId) {
        return item(id, code, ", \"layoutXPx\": " + x + ", \"layoutYPx\": " + y
                + ", \"layoutWPx\": 200, \"layoutHPx\": 100"
                + (parentId == null ? "" : ", \"layoutParentId\": \"" + parentId + "\""));
    }

    @Test
    void 유효_좌표는_절대좌표_CSS로_산출된다() throws IOException {
        GenResult r = generate(rootValid, definition(
                item("button_1", "BUTTON",
                        ", \"layoutXPx\": 24, \"layoutYPx\": 80, \"layoutWPx\": 320, \"layoutHPx\": 40, \"layoutZ\": 3"),
                "  \"canvas\": { \"widthPx\": 1440, \"heightPx\": 900 },\n"));
        assertEquals(GenResult.SUCCESS, r.resultCode());

        String css = readCss(rootValid);
        assertTrue(css.contains("width: 1440px;"), "시트 폭 산출: \n" + css);
        assertTrue(css.contains("height: 900px;"), "시트 높이 산출: \n" + css);
        assertTrue(css.contains("#freeScreen-canvas .frg-fc-1 {\n\tleft: 24px;\n\ttop: 80px;"
                + "\n\twidth: 320px;\n\theight: 40px;\n\tz-index: 3;\n}"),
                "인스턴스 좌표 규칙 산출: \n" + css);

        String jsp = readJsp(rootValid);
        assertTrue(jsp.contains("class=\"frg-fc-item frg-fc-1\""), "위치 클래스 산출: \n" + jsp);
        assertTrue(jsp.contains("확인"), "버튼 문구 산출: \n" + jsp);
        // 🔒 instanceId 는 셀렉터/CSS 어디에도 등장하지 않는다(§17.3).
        assertFalse(css.contains("button_1"), "instanceId 가 CSS 에 유입됨: \n" + css);
    }

    @Test
    void 악성_문자열_좌표는_한_글자도_산출되지_않는다() throws IOException {
        GenResult plain = generate(rootPlain, definition(
                item("button_1", "BUTTON", ""), ""));
        GenResult evil = generate(rootEvil, definition(
                item("button_1", "BUTTON",
                        ", \"layoutXPx\": \"0;} body{background:red}\", \"layoutYPx\": \"0px\","
                                + " \"layoutWPx\": \"100px;} *{display:none}\", \"layoutHPx\": \"40\""),
                ""));
        assertEquals(GenResult.SUCCESS, plain.resultCode());
        assertEquals(GenResult.SUCCESS, evil.resultCode());
        assertEquals(readCss(rootPlain), readCss(rootEvil), "악성 문자열이 CSS 에 새어 나왔다");
        assertFalse(readCss(rootEvil).contains("body{"), "인젝션 문자열 유출");
    }

    @Test
    void 범위_밖_좌표는_산출되지_않는다() throws IOException {
        GenResult r = generate(rootRange, definition(
                item("button_1", "BUTTON",
                        ", \"layoutXPx\": -10, \"layoutYPx\": 0, \"layoutWPx\": 999999, \"layoutHPx\": 40"),
                ""));
        assertEquals(GenResult.SUCCESS, r.resultCode());
        String css = readCss(rootRange);
        assertFalse(css.contains("left: -10px"), "음수 X 가 산출됨");
        assertFalse(css.contains("width: 999999px"), "범위 초과 폭이 산출됨");
        assertFalse(css.contains(".frg-fc-1 {"), "4키 중 하나라도 실패하면 규칙 전체가 없어야 한다");
        // 마크업은 그대로 남는다(안전측 폴백).
        assertTrue(readJsp(rootRange).contains("frg-fc-1"), "마크업까지 사라지면 안 된다");
    }

    @Test
    void 같은_모듈을_여러개_배치해도_산출은_3파일_고정이다() throws IOException {
        GenResult r = generate(rootDup, definition(
                item("button_1", "BUTTON",
                        ", \"layoutXPx\": 10, \"layoutYPx\": 10, \"layoutWPx\": 100, \"layoutHPx\": 40")
                        + ", " + item("button_2", "BUTTON",
                                ", \"layoutXPx\": 10, \"layoutYPx\": 60, \"layoutWPx\": 100, \"layoutHPx\": 40")
                        + ", " + item("button_3", "BUTTON",
                                ", \"layoutXPx\": 10, \"layoutYPx\": 110, \"layoutWPx\": 100, \"layoutHPx\": 40"),
                ""));
        assertEquals(GenResult.SUCCESS, r.resultCode());

        // 생성된 화면 아티팩트는 jsp/js/css 3종뿐이다(모듈별 파일 0 → 파일명 충돌 0).
        long screenFiles = r.files().stream()
                .filter(f -> f.relativePath().replace('\\', '/').contains("admin/freeScreen/"))
                .count();
        assertEquals(3, screenFiles,
                "화면 아티팩트가 3개가 아니다: " + r.files().stream().map(GenFile::relativePath).toList());

        String css = readCss(rootDup);
        assertTrue(css.contains(".frg-fc-1 {") && css.contains(".frg-fc-2 {") && css.contains(".frg-fc-3 {"),
                "인스턴스별 좌표 규칙이 모두 산출되어야 한다: \n" + css);
    }

    @Test
    void 팔레트가_내주는_모든_모듈이_렌더된다() throws IOException {
        // 자유 배치 팔레트는 카탈로그 전체를 노출한다(P13-4 결정). 그러니 **놓을 수 있는 것은
        // 전부 생성돼야** 한다 — 파셜이 없으면 "미지원 모듈" 주석만 남아 빈 상자가 됐다(P13-9).
        // 이 목록은 TB_FRG_MODULE_TYPE 시드(V3~V11) 전체와 같아야 한다.
        String[] codes = { "PANEL", "BUTTON", "LABEL", "TEXT_INPUT", "IMAGE",
                "TABLE_VIEW", "CARD_VIEW", "TREE_VIEW", "FORM_VIEW",
                "DETAIL_BASIC", "ASSOCIATE_TABS", "POPUP_FORM", "LAYOUT_FRAME",
                "TOOLBAR", "SEARCH_FILTER_BAR", "BAR_CHART", "SEMICIRCLE_CHART", "EMPTY_STATE",
                "CHAT_WIDGET" };
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < codes.length; i++) {
            if (i > 0) {
                items.append(", ");
            }
            items.append(item("inst_" + i, codes[i],
                    ", \"layoutXPx\": 20, \"layoutYPx\": " + (20 + i * 40)
                            + ", \"layoutWPx\": 200, \"layoutHPx\": 36"));
        }
        GenResult r = generate(rootAll, definition(items.toString(), ""));
        assertEquals(GenResult.SUCCESS, r.resultCode());

        String jsp = readJsp(rootAll);
        // 파셜이 하나라도 없으면 "미지원 모듈" 주석으로 떨어진다(§17.4 폴백) — 전부 렌더되어야 한다.
        assertFalse(jsp.contains("미지원 모듈"), "파셜이 없는 모듈이 있다: \n" + jsp);
        for (int i = 1; i <= codes.length; i++) {
            assertTrue(jsp.contains("frg-fc-" + i + "\""), "위치 클래스 " + i + " 누락: \n" + jsp);
        }
    }

    @Test
    void 위젯_숫자값은_포매터_객체가_아니라_숫자로_산출된다() throws IOException {
        // ?string 을 메서드 인자로 넘기면 값이 아니라 FreeMarker 포매터 객체가 찍혔다
        // (data-value="...NumberFormatter@43208e9a"). ?c 로 바꾼 뒤의 회귀 고정.
        GenResult r = generate(rootWidget, definition(
                item("chart_1", "SEMICIRCLE_CHART",
                        ", \"value\": 72, \"layoutXPx\": 10, \"layoutYPx\": 10,"
                                + " \"layoutWPx\": 320, \"layoutHPx\": 200"),
                ""));
        assertEquals(GenResult.SUCCESS, r.resultCode());

        String jsp = readJsp(rootWidget);
        assertFalse(jsp.contains("NumberFormatter"), "포매터 객체가 산출됐다: \n" + jsp);
        assertFalse(jsp.contains("freemarker.core"), "FreeMarker 내부 클래스명이 새어 나왔다: \n" + jsp);
        assertTrue(jsp.contains("data-value=\"72\""), "숫자 값이 그대로 나와야 한다: \n" + jsp);
        assertTrue(jsp.contains("<strong>72%</strong>"), "표시 값이 숫자여야 한다: \n" + jsp);
    }

    // ---------- §17.8 중첩(P13-7) ----------

    @Test
    void 패널_안의_모듈은_DOM이_중첩되고_좌표는_부모_기준이다() throws IOException {
        GenResult r = generate(rootNest, definition(
                placed("panel_1", "PANEL", 40, 40, null) + ", "
                        + placed("tableView_1", "TABLE_VIEW", 16, 24, "panel_1"),
                ""));
        assertEquals(GenResult.SUCCESS, r.resultCode());

        String jsp = readJsp(rootNest);
        // 패널(seq 1)의 div 안에 자식(seq 2)이 들어 있어야 한다 — 닫히기 전에 등장.
        // §17.10: 컨테이너 래퍼는 frg-fc-container 를 함께 단다(자식은 달지 않는다).
        int panelStart = jsp.indexOf("frg-fc-item frg-fc-container frg-fc-1");
        int childStart = jsp.indexOf("frg-fc-item frg-fc-2");
        assertTrue(panelStart >= 0 && childStart > panelStart, "자식이 패널 뒤에 와야 한다: \n" + jsp);
        assertTrue(jsp.contains("frg-fc-panel"), "패널 마크업 누락: \n" + jsp);
        assertTrue(jsp.contains("table-view"), "패널 안 테이블 마크업 누락: \n" + jsp);

        String css = readCss(rootNest);
        // 자식 좌표는 변환 없이 그대로 — 부모가 absolute 라 컨테이닝 블록이 성립한다.
        assertTrue(css.contains("#freeScreen-canvas .frg-fc-2 {\n\tleft: 16px;\n\ttop: 24px;"),
                "자식 좌표가 부모 기준으로 그대로 나와야 한다: \n" + css);
        assertTrue(css.contains(".frg-fc-panel {"), "패널 컨테이너 규칙 누락: \n" + css);
    }

    // ---------- §17.10 레이어 규칙 ----------

    @Test
    void 컨테이너는_z값_유무와_무관하게_자기_레이어_공간을_연다() throws IOException {
        // panel_z 는 layoutZ 있음 / panel_noz 는 없음. 예전에는 이 차이로 자식 z 가 부모 밖으로
        // 새는지 여부가 갈렸다(같은 구조, 다른 그림). 이제 둘 다 컨테이너 표시를 달아야 한다.
        String items = item("panel_z", "PANEL",
                ", \"layoutXPx\": 10, \"layoutYPx\": 10, \"layoutWPx\": 300, \"layoutHPx\": 200"
                        + ", \"layoutZ\": 1")
                + ", " + item("btn_z", "BUTTON",
                        ", \"layoutXPx\": 10, \"layoutYPx\": 10, \"layoutWPx\": 100, \"layoutHPx\": 40"
                                + ", \"layoutZ\": 99, \"layoutParentId\": \"panel_z\"")
                + ", " + item("panel_noz", "PANEL",
                        ", \"layoutXPx\": 10, \"layoutYPx\": 250, \"layoutWPx\": 300, \"layoutHPx\": 200")
                + ", " + item("btn_noz", "BUTTON",
                        ", \"layoutXPx\": 10, \"layoutYPx\": 10, \"layoutWPx\": 100, \"layoutHPx\": 40"
                                + ", \"layoutZ\": 99, \"layoutParentId\": \"panel_noz\"");
        GenResult r = generate(rootLayer, definition(items, ""));
        assertEquals(GenResult.SUCCESS, r.resultCode());

        String jsp = readJsp(rootLayer);
        // 컨테이너 둘 다 표시가 붙는다 — z 를 적었는지와 무관.
        assertTrue(jsp.contains("frg-fc-item frg-fc-container frg-fc-1"), "z 있는 패널: \n" + jsp);
        assertTrue(jsp.contains("frg-fc-item frg-fc-container frg-fc-3"), "z 없는 패널: \n" + jsp);
        // 컨테이너가 아닌 버튼에는 붙지 않는다.
        assertTrue(jsp.contains("frg-fc-item frg-fc-2"), "버튼 래퍼: \n" + jsp);
        assertFalse(jsp.contains("frg-fc-container frg-fc-2"), "버튼에 컨테이너 표시가 붙었다: \n" + jsp);
        assertFalse(jsp.contains("frg-fc-container frg-fc-4"), "버튼에 컨테이너 표시가 붙었다: \n" + jsp);

        String css = readCss(rootLayer);
        // 스태킹 컨텍스트를 항상 연다 → layoutZ 는 "형제 사이의 순서"로 확정된다.
        assertTrue(css.contains("#freeScreen-canvas .frg-fc-container {\n\tisolation: isolate;\n}"),
                "컨테이너 격리 규칙 누락: \n" + css);
        // 🔒 데이터는 여전히 CSS 로 새지 않는다.
        assertFalse(css.contains("panel_z") || css.contains("btn_noz"), "instanceId 유입: \n" + css);
    }

    @Test
    void 중첩_깊이만큼_들여쓰기가_밀린다() throws IOException {
        GenResult r = generate(rootIndent, definition(
                placed("panel_1", "PANEL", 40, 40, null) + ", "
                        + placed("panel_2", "PANEL", 10, 10, "panel_1") + ", "
                        + placed("button_1", "BUTTON", 5, 5, "panel_2"),
                ""));
        assertEquals(GenResult.SUCCESS, r.resultCode());

        String jsp = readJsp(rootIndent);
        // §17.12 로 컨테이너마다 내용 상자가 한 겹 들어간다:
        // 컨테이너 8 → 내용 상자 12 → 자식 컨테이너 16 → 내용 상자 20 → 자식 24.
        assertTrue(jsp.contains("\n        <div class=\"frg-fc-item frg-fc-container frg-fc-1\""),
                "루트 들여쓰기 8칸: \n" + jsp);
        assertTrue(jsp.contains("\n            <div class=\"frg-fc-panel-body\">"),
                "내용 상자 12칸: \n" + jsp);
        assertTrue(jsp.contains("\n                <div class=\"frg-fc-item frg-fc-container frg-fc-2\""),
                "1단 들여쓰기 16칸: \n" + jsp);
        assertTrue(jsp.contains("\n                        <div class=\"frg-fc-item frg-fc-3\""),
                "2단 들여쓰기 24칸: \n" + jsp);
        // 닫는 태그도 같은 깊이로 돌아와야 한다(재귀가 부모 값을 덮지 않는다는 회귀).
        assertTrue(jsp.contains("\n                        </div>\n                    </div>\n"
                        + "                </div>\n            </div>\n        </div>"),
                "닫는 태그가 깊이 역순으로 돌아와야 한다: \n" + jsp);
    }

    @Test
    void 순환_부모는_루트로_수렴해_화면이_사라지지_않는다() throws IOException {
        // a→b, b→a 순환. 저장 검증이 1차로 막지만 산출측도 안전측으로 버텨야 한다.
        String items = item("panel_a", "PANEL",
                ", \"layoutXPx\": 10, \"layoutYPx\": 10, \"layoutWPx\": 200, \"layoutHPx\": 100"
                        + ", \"layoutParentId\": \"panel_b\"")
                + ", " + item("panel_b", "PANEL",
                        ", \"layoutXPx\": 20, \"layoutYPx\": 20, \"layoutWPx\": 200, \"layoutHPx\": 100"
                                + ", \"layoutParentId\": \"panel_a\"");
        GenResult r = generate(rootCycle, definition(items, ""));
        assertEquals(GenResult.SUCCESS, r.resultCode());

        String jsp = readJsp(rootCycle);
        assertTrue(jsp.contains("frg-fc-1") && jsp.contains("frg-fc-2"),
                "순환이어도 두 인스턴스가 모두 남아야 한다: \n" + jsp);
        String css = readCss(rootCycle);
        assertTrue(css.contains(".frg-fc-1 {") && css.contains(".frg-fc-2 {"),
                "좌표 규칙도 둘 다 나와야 한다: \n" + css);
    }

    @Test
    void 컨테이너가_아닌_부모는_무시된다() throws IOException {
        GenResult r = generate(rootBadParent, definition(
                placed("button_1", "BUTTON", 10, 10, null) + ", "
                        + placed("label_1", "LABEL", 30, 30, "button_1"),
                ""));
        assertEquals(GenResult.SUCCESS, r.resultCode());
        String jsp = readJsp(rootBadParent);
        // 버튼은 자식을 담을 수 없으므로 라벨은 루트로 수렴 — 버튼 div 가 라벨보다 먼저 닫힌다.
        int buttonEnd = jsp.indexOf("</button>");
        int labelStart = jsp.indexOf("frg-fc-label");
        assertTrue(buttonEnd >= 0 && labelStart > buttonEnd, "라벨이 버튼 안에 들어가면 안 된다: \n" + jsp);
    }

    // ---------- 픽스처(LayoutCssGenerationTest 와 동형) ----------

    private GenResult generate(Path targetRoot, String definitionJson) {
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
        s.setScreenId(20L);
        s.setProjectId(1L);
        s.setStem("freeScreen");
        s.setRoleCode("admin");
        s.setArchetypeCode("FREE_CANVAS");
        s.setDefinitionJson(definitionJson);

        ForgeProject p = new ForgeProject();
        p.setProjectId(1L);
        p.setTargetRootPath(targetRoot.toString());
        p.setPackageBase("com.jworks.forge");
        p.setJspBasePath("WEB-INF/views");
        p.setJsBasePath("static/js");
        p.setCssBasePath("static/css");
        p.setRuntimeVer("1.0.0");

        when(screenService.get(20L)).thenReturn(s);
        when(projectService.get(1L)).thenReturn(p);
        return new ScreenGenerator(screenService, projectService, contextBuilder, renderer,
                pathSafety, fileWriter, runtimeSyncer, stubGenerator).generate(20L);
    }

    private String readCss(Path root) throws IOException {
        return normalize(root.resolve("static/css/admin/freeScreen/freeScreen.css"));
    }

    private String readJsp(Path root) throws IOException {
        return normalize(root.resolve("WEB-INF/views/admin/freeScreen/freeScreen.jsp"));
    }

    private String normalize(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n").replace("\r", "\n");
    }
}
