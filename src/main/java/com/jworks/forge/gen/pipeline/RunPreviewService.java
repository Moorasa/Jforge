package com.jworks.forge.gen.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.jworks.forge.common.web.NotFoundException;
import com.jworks.forge.gen.context.TemplateContextBuilder;
import com.jworks.forge.gen.pipeline.GenArtifacts.ArtifactSpec;
import com.jworks.forge.gen.pipeline.GenArtifacts.BaseKind;
import com.jworks.forge.gen.template.TemplateRenderer;
import com.jworks.forge.project.domain.ForgeProject;
import com.jworks.forge.project.service.ForgeProjectService;
import com.jworks.forge.screen.domain.ForgeScreen;
import com.jworks.forge.screen.service.ForgeScreenService;

/**
 * 실행 미리보기 (P9). 타겟 앱을 구동하지 않고 <b>빌더 자신의 톰캣이 생성 화면을 대신 렌더</b>해
 * "스프링에서 뜬 것처럼" 보여준다. <b>읽기전용 — 파일쓰기/파일읽기 0.</b>
 *
 * <p>동작: DB 저장 DEFINITION_JSON → {@link TemplateContextBuilder}(검증 동일) →
 * {@link GenPlanner#planArtifacts}(조립 규칙 단일 미러) → {@link TemplateRenderer}로
 * 아티팩트를 <b>메모리에서만</b> 렌더 → JSP 산출물을 정적 HTML 로 변환(아래 transform) →
 * 빌더 JSP 래퍼(run-preview.jsp)가 번들 런타임 매니페스트와 함께 서빙.
 *
 * <p><b>변환 가능 근거</b>: 생성 JSP 는 계약상 스크립틀릿 0 / EL 은 {@code ${ctx}} 하나 /
 * JSTL 은 {@code c:set ctx} 하나 / include 는 시블링 아티팩트({@code ./{stem}X.jsp})와
 * 매니페스트({@code ../common/header.jsp})뿐이다(§1/§3). 따라서 (1) 시블링 include 는 렌더된
 * 아티팩트 맵으로 인라인 치환, (2) 매니페스트 include 는 래퍼 JSP 의 실제 include 가 대체,
 * (3) 지시자·c:set·JSP 주석 제거, (4) {@code ${ctx}} 를 빌더 컨텍스트패스로 치환하면
 * <b>동일 마크업의 정적 HTML</b>이 된다.
 *
 * <p>🔒 보안 요지:
 * <ul>
 *   <li><b>타겟 폴더의 파일을 읽지 않는다</b> — 항상 DB 설계에서 재렌더(사용자가 타겟 파일을
 *       변조해도 빌더에서 실행되지 않음). 디스크 접근 자체가 없다.</li>
 *   <li>JS/CSS 는 인라인하지 않고 <b>별도 asset 엔드포인트</b>(외부 파일)로 서빙 — 생성물과
 *       동일한 실행 문맥을 유지해 GenEscaper(jsString/cssToken) 이스케이프 계약이 그대로 유효.</li>
 *   <li>asset 키는 {@link GenArtifacts} 정적 스펙의 artifactKey 화이트리스트로만 해석(자유문자열
 *       경로 0), JSP 아티팩트는 asset 으로 서빙 금지.</li>
 *   <li>본문 HTML 은 EL 로 무이스케이프 출력되지만, 값 안의 {@code ${...}} 문자열은 런타임에
 *       재평가되지 않는다(EL 은 템플릿 텍스트에서만 해석) — EL 인젝션 불가.</li>
 * </ul>
 */
@Service
public class RunPreviewService {

    /** 시블링 아티팩트 include: {@code <jsp:include page="./userMgmtList.jsp" />} */
    private static final Pattern SIBLING_INCLUDE =
            Pattern.compile("<jsp:include\\s+page=\"\\./([A-Za-z0-9]+\\.jsp)\"\\s*/>");
    /** 남은 모든 jsp:include(매니페스트 등) 제거용. */
    private static final Pattern ANY_INCLUDE = Pattern.compile("<jsp:include[^>]*/>");
    private static final Pattern DIRECTIVE = Pattern.compile("<%@.*?%>", Pattern.DOTALL);
    private static final Pattern JSP_COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);
    private static final Pattern C_SET = Pattern.compile("<c:set[^>]*/>");
    /** per-screen 자산 참조(빌더에 없는 경로 → 404 방지, 자산은 래퍼가 asset 엔드포인트로 주입). */
    private static final Pattern SCREEN_SCRIPT =
            Pattern.compile("<script[^>]*src=\"\\$\\{ctx}[^\"]*\"[^>]*>\\s*</script>");
    private static final Pattern SCREEN_LINK =
            Pattern.compile("<link[^>]*href=\"\\$\\{ctx}[^\"]*\"[^>]*>");

    private final ForgeScreenService screenService;
    private final ForgeProjectService projectService;
    private final TemplateContextBuilder contextBuilder;
    private final TemplateRenderer renderer;
    private final GenPlanner genPlanner;

    public RunPreviewService(
            ForgeScreenService screenService,
            ForgeProjectService projectService,
            TemplateContextBuilder contextBuilder,
            TemplateRenderer renderer,
            GenPlanner genPlanner) {
        this.screenService = screenService;
        this.projectService = projectService;
        this.contextBuilder = contextBuilder;
        this.renderer = renderer;
        this.genPlanner = genPlanner;
    }

    /** 페이지 모델: 변환된 본문 + 래퍼가 주입할 asset 키 목록(CSS/JS, 정적 artifactKey). */
    public record RunPreview(String screenName, String stem, String bodyHtml,
                             List<String> cssKeys, List<String> jsKeys) {
    }

    /** asset 1건(렌더 원문 + 확장자 — 컨트롤러가 content-type 결정). */
    public record PreviewAsset(String content, String ext) {
    }

    /**
     * 화면 1건의 실행 미리보기 본문을 만든다.
     *
     * @param contextPath 빌더 컨텍스트패스(생성물의 {@code ${ctx}} 치환값)
     * @throws NotFoundException 화면/프로젝트 미존재(404)
     * @throws IllegalStateException 컨텍스트 검증 실패/미지원 아키타입(사유 메시지 포함)
     */
    public RunPreview build(Long screenId, String contextPath) {
        Rendered r = renderAll(screenId);
        String html = transform(r.shell(), r.jspByFileName(), contextPath);
        return new RunPreview(r.screen().getScreenName(), r.stem(), extractBody(html),
                r.cssKeys(), r.jsKeys());
    }

    /**
     * JS/CSS asset 1건을 렌더해 돌려준다. 🔒 artifactKey 는 정적 스펙 화이트리스트로만 해석 —
     * 미등록 키/JSP 아티팩트는 404.
     */
    public PreviewAsset asset(Long screenId, String artifactKey) {
        Rendered r = renderAll(screenId);
        ArtifactSpec spec = r.specByKey().get(artifactKey);
        if (spec == null || spec.baseKind() == BaseKind.JSP) {
            throw new NotFoundException("preview asset not found: " + artifactKey);
        }
        return new PreviewAsset(renderer.render(spec.templateKey(), r.model()), spec.ext());
    }

    // ---------------------------------------------------------------------------------

    private record Rendered(ForgeScreen screen, String stem, Map<String, Object> model,
                            String shell, Map<String, String> jspByFileName,
                            Map<String, ArtifactSpec> specByKey,
                            List<String> cssKeys, List<String> jsKeys) {
    }

    private Rendered renderAll(Long screenId) {
        ForgeScreen screen = screenService.get(screenId);                 // 없으면 404
        ForgeProject project = projectService.get(screen.getProjectId()); // 없으면 404

        Map<String, Object> model;
        try {
            model = contextBuilder.build(screen, project);
        } catch (RuntimeException e) {
            throw new IllegalStateException("미리보기 컨텍스트 구성 실패: " + e.getMessage(), e);
        }
        genPlanner.enrichRenderModel(model); // §8.1/§9.2/design 파생 플래그(ScreenGenerator 미러)

        String stem = (String) model.get("stem");
        String archetype = (String) model.get("archetype");
        List<ArtifactSpec> specs = genPlanner.planArtifacts(archetype, model);
        if (specs.isEmpty()) {
            throw new IllegalStateException("지원되지 않는 아키타입이거나 아티팩트 없음: " + archetype);
        }

        Map<String, String> jspByFileName = new LinkedHashMap<>();
        Map<String, ArtifactSpec> specByKey = new LinkedHashMap<>();
        List<String> cssKeys = new ArrayList<>();
        List<String> jsKeys = new ArrayList<>();
        String shell = null;
        for (ArtifactSpec spec : specs) {
            specByKey.put(spec.artifactKey(), spec);
            if (spec.baseKind() == BaseKind.JSP) {
                String content = renderer.render(spec.templateKey(), model);
                jspByFileName.put(stem + spec.nameSuffix() + "." + spec.ext(), content);
                // shell = 접미사 없는 JSP({stem}.jsp — MGMT shell / DUAL dualShell 공통 규칙).
                if (spec.nameSuffix().isEmpty() && shell == null) {
                    shell = content;
                }
            } else if (spec.baseKind() == BaseKind.CSS) {
                cssKeys.add(spec.artifactKey());
            } else {
                jsKeys.add(spec.artifactKey());
            }
        }
        if (shell == null) {
            throw new IllegalStateException("shell 아티팩트를 찾을 수 없음: " + archetype);
        }
        return new Rendered(screen, stem, model, shell, jspByFileName, specByKey, cssKeys, jsKeys);
    }

    /**
     * 생성 JSP → 정적 HTML 변환. 순서: 시블링 include 인라인 확장(깊이 가드) → 잔여 include 제거
     * → per-screen 자산 참조 제거 → 지시자/JSP주석/c:set 제거 → {@code ${ctx}} 치환.
     */
    private String transform(String jsp, Map<String, String> files, String contextPath) {
        String out = jsp;
        // 1) 시블링 include 확장(중첩 include 대비 반복, 최대 10패스 — 순환 방어).
        for (int pass = 0; pass < 10; pass++) {
            Matcher m = SIBLING_INCLUDE.matcher(out);
            if (!m.find()) {
                break;
            }
            StringBuilder sb = new StringBuilder();
            m.reset();
            while (m.find()) {
                String fileName = m.group(1);
                String replacement = files.getOrDefault(fileName, "");
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            m.appendTail(sb);
            out = sb.toString();
        }
        // 2) 잔여 include(매니페스트 ../common/header.jsp 등) 제거 — 래퍼 JSP 가 실제 include 로 대체.
        out = ANY_INCLUDE.matcher(out).replaceAll("");
        // 3) per-screen 자산 참조 제거(${ctx} 치환 전, 리터럴 매치).
        out = SCREEN_SCRIPT.matcher(out).replaceAll("");
        out = SCREEN_LINK.matcher(out).replaceAll("");
        // 4) 지시자/JSP 주석/c:set 제거.
        out = DIRECTIVE.matcher(out).replaceAll("");
        out = JSP_COMMENT.matcher(out).replaceAll("");
        out = C_SET.matcher(out).replaceAll("");
        // 5) ${ctx} → 빌더 컨텍스트패스(문자열 치환 — 재평가 없음).
        return out.replace("${ctx}", contextPath == null ? "" : contextPath);
    }

    /** shell 문서에서 body 내부만 추출(래퍼 JSP 가 자체 head/manifest 를 제공). */
    private String extractBody(String html) {
        int bodyOpen = html.indexOf("<body");
        if (bodyOpen < 0) {
            return html;
        }
        int start = html.indexOf('>', bodyOpen);
        int end = html.lastIndexOf("</body>");
        if (start < 0 || end < 0 || end <= start) {
            return html;
        }
        return html.substring(start + 1, end);
    }
}
