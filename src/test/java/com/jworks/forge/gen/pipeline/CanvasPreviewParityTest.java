package com.jworks.forge.gen.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
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
 * 🔒 프리뷰 ↔ 산출 구조 대조 (계약 §17.12 회귀망).
 *
 * <p><b>왜 필요했나</b>: 이 프로젝트에는 <em>계획↔산출</em> 드리프트 그물({@code GenPlannerTest})은
 * 있는데 <em>프리뷰↔산출</em> 그물이 없었다. 그래서 §17.12 로 산출 구조를 바꿨을 때
 * {@code previewRenderer.js} 가 옛 모양으로 남은 것을 테스트가 못 잡았다(사람이 눈으로 발견).
 * "캔버스에 보이는 구조 = 나갈 구조"는 이 스튜디오의 계약이므로 기계가 지켜야 한다.
 *
 * <p><b>방식</b>: 같은 DEFINITION_JSON 하나로
 * <ol>
 *   <li>실제 파이프라인으로 JSP 를 생성하고,</li>
 *   <li>{@code src/test/resources/js/canvasSkeleton.js} 를 node 로 돌려 프리뷰 DOM 을 만들어</li>
 *   <li>양쪽에서 <b>표준 어휘 골격</b>(C=컨테이너 / B=내용상자 / I=인스턴스)을 뽑아 비교한다.</li>
 * </ol>
 * 두 렌더러는 태그·클래스가 서로 다르므로(산출 {@code div.frg-fc-item} / 프리뷰
 * {@code section.frg-fc-block}) 이름이 아니라 <b>의미</b>로 환산해 비교한다.
 *
 * <p><b>node 가 없으면 건너뛴다</b> — 생성 파이프라인 검증이 JS 런타임 유무에 묶이면 안 된다.
 */
class CanvasPreviewParityTest {

    @TempDir
    Path targetRoot;
    @TempDir
    Path workDir;

    private static final String STEM = "parityCanvas";

    /** 3단 중첩 + 루트 형제. §17.12 의 "상자 안 상자"가 드러나는 최소 구성. */
    private static final String DEFINITION = """
            {
              "schemaVersion": 1,
              "archetype": "FREE_CANVAS",
              "stem": "parityCanvas",
              "role": "admin",
              "canvas": { "widthPx": 1280, "heightPx": 800 },
              "slots": { "canvasArea": [
                { "instanceId": "panelA", "moduleTypeCode": "PANEL",
                  "props": { "title": "바깥 패널",
                             "layoutXPx": 40, "layoutYPx": 40, "layoutWPx": 520, "layoutHPx": 360,
                             "layoutZ": 1 } },
                { "instanceId": "panelIn", "moduleTypeCode": "PANEL",
                  "props": { "title": "안쪽 패널", "layoutParentId": "panelA",
                             "layoutXPx": 20, "layoutYPx": 40, "layoutWPx": 440, "layoutHPx": 240 } },
                { "instanceId": "btnDeep", "moduleTypeCode": "BUTTON",
                  "props": { "text": "깊은 버튼", "layoutParentId": "panelIn",
                             "layoutXPx": 16, "layoutYPx": 32, "layoutWPx": 140, "layoutHPx": 36 } },
                { "instanceId": "tableIn", "moduleTypeCode": "TABLE_VIEW",
                  "props": { "columns": [ { "name": "a", "displayName": "A" } ],
                             "layoutParentId": "panelA",
                             "layoutXPx": 20, "layoutYPx": 300, "layoutWPx": 440, "layoutHPx": 40 } },
                { "instanceId": "btnOut", "moduleTypeCode": "BUTTON",
                  "props": { "text": "바깥 버튼",
                             "layoutXPx": 600, "layoutYPx": 60, "layoutWPx": 140, "layoutHPx": 36 } }
              ] }
            }
            """;

    @Test
    void 프리뷰_캔버스_골격이_생성물과_같다() throws Exception {
        Assumptions.assumeTrue(nodeAvailable(), "node 없음 — 프리뷰 대조 건너뜀");

        GenResult r = generate();
        assertEquals(GenResult.SUCCESS, r.resultCode(), "생성 실패: " + r.failReason());

        String jsp = Files.readString(
                targetRoot.resolve("WEB-INF/views/admin/" + STEM + "/" + STEM + ".jsp"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");

        String fromOutput = skeletonOfJsp(jsp);
        String fromPreview = skeletonOfPreview();

        assertEquals(fromOutput, fromPreview,
                "프리뷰와 산출의 캔버스 골격이 다르다(C=컨테이너 B=내용상자 I=인스턴스).\n"
                        + "--- 산출 ---\n" + fromOutput + "\n--- 프리뷰 ---\n" + fromPreview
                        + "\n\n산출 JSP:\n" + jsp);
    }

    /** 골격이 실제로 §17.12 모양인지도 못박는다(양쪽이 똑같이 틀린 경우를 잡는다). */
    @Test
    void 골격은_컨테이너_내용상자_자식_순서를_갖는다() throws Exception {
        GenResult r = generate();
        assertEquals(GenResult.SUCCESS, r.resultCode());
        String jsp = Files.readString(
                targetRoot.resolve("WEB-INF/views/admin/" + STEM + "/" + STEM + ".jsp"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");

        assertEquals("""
                C
                  B
                    C
                      B
                        I
                    I
                I""", skeletonOfJsp(jsp));
    }

    // ---------- 골격 추출 ----------

    /**
     * 생성 JSP 에서 표준 어휘 골격을 뽑는다. div 스택을 쌓아 중첩을 세되, 골격이 아닌 div
     * (파셜 내부 마크업 등)는 깊이를 늘리지 않는다 — 프리뷰 쪽 추출과 같은 규칙이다.
     */
    private static String skeletonOfJsp(String jsp) {
        List<String> lines = new ArrayList<>();
        Deque<String> stack = new ArrayDeque<>();  // 열려 있는 div 의 종류(골격이면 C/B/I, 아니면 "-")
        int depth = 0;

        int i = 0;
        while (i < jsp.length()) {
            int open = jsp.indexOf("<div", i);
            int close = jsp.indexOf("</div>", i);
            if (open < 0 && close < 0) { break; }

            if (open >= 0 && (close < 0 || open < close)) {
                int end = jsp.indexOf('>', open);
                if (end < 0) { break; }
                String kind = kindOf(jsp.substring(open, end));
                if (kind != null) {
                    lines.add("  ".repeat(depth) + kind);
                    depth++;
                    stack.push(kind);
                } else {
                    stack.push("-");
                }
                i = end + 1;
            } else {
                if (!stack.isEmpty() && !"-".equals(stack.pop())) { depth--; }
                i = close + 6;
            }
        }
        return String.join("\n", lines);
    }

    /** 열린 div 태그 문자열 → 표준 어휘(없으면 null). */
    private static String kindOf(String openTag) {
        if (openTag.contains("frg-fc-panel-body")) { return "B"; }
        if (openTag.contains("frg-fc-item") || openTag.contains("frg-fc-block")) {
            return openTag.contains("frg-fc-container") ? "C" : "I";
        }
        return null;
    }

    /** node 로 실제 previewRenderer.js 를 돌려 골격을 받는다. */
    private String skeletonOfPreview() throws Exception {
        Path defFile = workDir.resolve("definition.json");
        Files.writeString(defFile, DEFINITION, StandardCharsets.UTF_8);

        Path script = Paths.get("src", "test", "resources", "js", "canvasSkeleton.js");
        assertTrue(Files.exists(script), "골격 추출 스크립트 없음: " + script);
        Path jsDir = Paths.get("src", "main", "resources", "static", "js", "admin", "studio");

        ProcessBuilder pb = new ProcessBuilder("node",
                script.toAbsolutePath().toString(),
                jsDir.toAbsolutePath().toString(),
                defFile.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out;
        try (var in = p.getInputStream()) {
            out = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "node 타임아웃");
        assertEquals(0, p.exitValue(), "골격 추출 실패:\n" + out);
        return out.replace("\r\n", "\n").trim();
    }

    private static boolean nodeAvailable() {
        try {
            Process p = new ProcessBuilder("node", "--version").redirectErrorStream(true).start();
            return p.waitFor(20, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) { Thread.currentThread().interrupt(); }
            return false;
        }
    }

    // ---------- 픽스처 ----------

    private GenResult generate() {
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
        s.setScreenId(50L);
        s.setProjectId(1L);
        s.setStem(STEM);
        s.setRoleCode("admin");
        s.setArchetypeCode("FREE_CANVAS");
        s.setDefinitionJson(DEFINITION);

        ForgeProject p = new ForgeProject();
        p.setProjectId(1L);
        p.setTargetRootPath(targetRoot.toString());
        p.setPackageBase("com.jworks.forge");
        p.setJspBasePath("WEB-INF/views");
        p.setJsBasePath("static/js");
        p.setCssBasePath("static/css");
        p.setRuntimeVer("1.0.0");

        when(screenService.get(50L)).thenReturn(s);
        when(projectService.get(1L)).thenReturn(p);
        return new ScreenGenerator(screenService, projectService, contextBuilder, renderer,
                pathSafety, fileWriter, runtimeSyncer, stubGenerator).generate(50L);
    }
}
