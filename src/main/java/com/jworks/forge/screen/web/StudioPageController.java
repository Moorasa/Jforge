package com.jworks.forge.screen.web;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import com.jworks.forge.gen.pipeline.RunPreviewService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 3-pane 스튜디오 셸 도그푸딩 화면 (P3-3). 3종 세트(JSP+JS+CSS)의 JSP 진입.
 * 상단바(프로젝트/화면 선택 + 새 화면/저장 슬롯) + 좌(팔레트)/중(프리뷰)/우(속성) 3-pane 골격.
 * 데이터는 JS가 /api/projects(프로젝트)·/api/screens(화면 목록/단건)를 소비하며,
 * 이 모듈이 하위 컨트롤러(palette/preview/props, P3-4~P3-6)의 오케스트레이션 허브가 된다.
 *
 * <p>P9: 실행 미리보기 — 타겟 앱 없이 생성 화면을 빌더 톰캣이 대신 렌더(새 탭).
 */
@Controller
public class StudioPageController {

    private final RunPreviewService runPreviewService;

    public StudioPageController(RunPreviewService runPreviewService) {
        this.runPreviewService = runPreviewService;
    }

    @GetMapping("/admin/studio")
    public String studio() {
        return "admin/studio/list";
    }

    /**
     * 중앙 라이브 프리뷰 iframe 문서 (P3-6). studio 화면의 iframe src 로 로드되는 별도 문서이며
     * 직접 접근(GET /admin/studio/preview)도 가능하다. 파라미터 없이 번들 매니페스트를 로드하는
     * 최소 셸만 렌더하고, 실제 DEFINITION_JSON 데이터는 부모(studioApp)가 postMessage 로 주입한다.
     * <p>이 프리뷰는 DB 저장된 DEFINITION_JSON 을 브라우저 번들 런타임으로 시각화하는 <b>근사 미리보기</b>이며
     * 최종 생성물(P4 생성 엔진 산출물)과 다를 수 있다.
     */
    @GetMapping("/admin/studio/preview")
    public String preview() {
        return "admin/studio/preview";
    }

    /**
     * 실행 미리보기 페이지 (P9). 저장본(DB) 기준으로 생성 화면을 메모리 렌더해 실제 번들 런타임과
     * 함께 서빙한다 — "스프링에 뜬 것처럼" 확인용(데이터 API 는 미연결 → 빈 목록).
     * 화면/프로젝트 미존재 404, 검증 실패는 사유 메시지 예외.
     */
    @GetMapping("/admin/studio/run-preview/{screenId}")
    public String runPreview(@PathVariable Long screenId, HttpServletRequest request, Model model) {
        RunPreviewService.RunPreview p = runPreviewService.build(screenId, request.getContextPath());
        model.addAttribute("previewScreenId", screenId);
        model.addAttribute("previewScreenName", p.screenName());
        model.addAttribute("previewStem", p.stem());
        model.addAttribute("previewBody", p.bodyHtml());
        model.addAttribute("previewCssKeys", p.cssKeys());
        model.addAttribute("previewJsKeys", p.jsKeys());
        return "admin/studio/run-preview";
    }

    /**
     * 실행 미리보기 자산(per-screen JS/CSS) (P9). 🔒 artifactKey 는 GenArtifacts 정적 스펙
     * 화이트리스트로만 해석(미등록/JSP 는 404), 렌더 원문을 명시적 content-type + nosniff 로 서빙.
     */
    @GetMapping("/admin/studio/run-preview/{screenId}/asset/{artifactKey}")
    @ResponseBody
    public ResponseEntity<String> runPreviewAsset(
            @PathVariable Long screenId, @PathVariable String artifactKey) {
        RunPreviewService.PreviewAsset asset = runPreviewService.asset(screenId, artifactKey);
        MediaType type = "css".equals(asset.ext())
                ? MediaType.valueOf("text/css;charset=UTF-8")
                : MediaType.valueOf("application/javascript;charset=UTF-8");
        return ResponseEntity.ok()
                .contentType(type)
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(asset.content());
    }
}
