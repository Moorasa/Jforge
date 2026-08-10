package com.jworks.forge.gen.hist;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jworks.forge.gen.pipeline.GenDiffService;
import com.jworks.forge.gen.pipeline.GenPlanner;
import com.jworks.forge.gen.pipeline.GenRestoreService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 생성 트리거 / 이력 조회 REST API (P4-5). 경로는 {@code /api/screens/{id}/...}를 유지하되,
 * 저장(PUT definition, {@link com.jworks.forge.screen.web.ForgeScreenController})과 <b>별도 엔드포인트</b>다.
 * 저장 = DB(P3), 생성 = 파일쓰기(P4).
 *
 * <p>미존재 screenId는 {@link com.jworks.forge.common.web.NotFoundException}(404)이
 * {@link com.jworks.forge.common.web.ApiExceptionHandler}에 의해 규약 오류 응답으로 변환된다.
 */
@RestController
@RequestMapping("/api/screens")
public class GenController {

    private final GenerationService generationService;
    private final GenPlanner genPlanner;
    private final GenDiffService diffService;
    private final GenRestoreService restoreService;

    public GenController(GenerationService generationService,
                         GenPlanner genPlanner,
                         GenDiffService diffService,
                         GenRestoreService restoreService) {
        this.generationService = generationService;
        this.genPlanner = genPlanner;
        this.diffService = diffService;
        this.restoreService = restoreService;
    }

    /**
     * 생성 트리거: 파일 생성 + GEN_HIST 1행 기록. 성공/부분/실패 모두 기록(FAIL/PARTIAL 포함).
     * 미존재 screenId는 404(NotFoundException 전파).
     */
    @PostMapping("/{id}/generate")
    public GenerateResponse generate(@PathVariable("id") Long id) {
        return generationService.generateAndRecord(id);
    }

    /**
     * 생성 dry-run(P7-4): 파일을 쓰지 않고 "어떤 파일이 신규/덮어쓰기 되는지" 계획만 돌려준다.
     * 읽기전용({@link GenPlanner}) — GEN_HIST 기록 없음.
     */
    @GetMapping("/{id}/generate/plan")
    public GenPlanner.GenPlan generatePlan(@PathVariable("id") Long id) {
        return genPlanner.plan(id);
    }

    /** 화면별 생성 이력(최신순, 메타만 + FILE_LIST_JSON 원문). */
    @GetMapping("/{id}/gen-history")
    public List<GenHist> history(@PathVariable("id") Long id) {
        return generationService.history(id);
    }

    /**
     * P12(계약 §16.3): 아티팩트 1건의 "지금 파일 vs 생성 예정 내용" diff. <b>읽기전용.</b>
     * 계획에 없는 artifactKey는 404.
     */
    @GetMapping("/{id}/generate/diff")
    public GenDiffService.DiffView diff(@PathVariable("id") Long id,
                                        @RequestParam("artifactKey") String artifactKey) {
        return diffService.diff(id, artifactKey);
    }

    /** P12(계약 §16.5): 아티팩트 1건의 백업 목록(최신순). <b>읽기전용.</b> */
    @GetMapping("/{id}/generate/backups")
    public List<GenDiffService.BackupEntry> backups(@PathVariable("id") Long id,
                                                    @RequestParam("artifactKey") String artifactKey) {
        return diffService.backups(id, artifactKey);
    }

    /** P12(계약 §16.5): 백업 복원. 클라이언트는 14자리 타임스탬프만 보낸다(경로 문자열 미수신). */
    @PostMapping("/{id}/generate/restore")
    public GenRestoreService.RestoreResult restore(@PathVariable("id") Long id,
                                                   @Valid @RequestBody RestoreRequest request) {
        return restoreService.restore(id, request.artifactKey(), request.timestamp());
    }

    /** 복원 요청 본문. */
    public record RestoreRequest(
            @NotBlank @Size(max = 64) String artifactKey,
            @NotBlank @Size(max = 14) String timestamp) {
    }
}
