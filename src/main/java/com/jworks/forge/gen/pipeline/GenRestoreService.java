package com.jworks.forge.gen.pipeline;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.jworks.forge.common.web.NotFoundException;
import com.jworks.forge.gen.pipeline.GenPlanner.GenPlan;
import com.jworks.forge.gen.pipeline.GenPlanner.PlanFile;
import com.jworks.forge.gen.safety.PathSafetyService;
import com.jworks.forge.project.domain.ForgeProject;
import com.jworks.forge.project.service.ForgeProjectService;
import com.jworks.forge.screen.domain.ForgeScreen;
import com.jworks.forge.screen.service.ForgeScreenService;

/**
 * 🔒 백업 복원 (P12, 계약 §16.5). <b>이 서비스만이 P12의 유일한 쓰기 경로다.</b>
 *
 * <p>재생성이 사람 수정을 덮어썼을 때, {@code .bak-yyyyMMddHHmmss}(계약 §5.2)로 되돌린다.
 *
 * <p>안전 요지:
 * <ul>
 *   <li>대상 경로는 {@link GenPlanner#plan} 결과의 {@code relativePath}뿐이다(계획에 없는
 *       artifactKey는 404 — 임의 경로를 만들어낼 수 없다).</li>
 *   <li>클라이언트가 보내는 값은 <b>14자리 숫자 타임스탬프뿐</b>이다. 파일명/경로 문자열을 받지
 *       않으므로 경로 조작 여지가 없다(백업 경로 = 안전 절대경로 + 정적 접미사).</li>
 *   <li>복원 쓰기도 예외 없이 {@link PathSafetyService#resolveSafeWritePath} +
 *       {@link AtomicFileWriter} 경유다 — 즉 <b>복원 자체도 현재 내용을 백업</b>한 뒤 수행된다
 *       (되돌리기를 다시 되돌릴 수 있다).</li>
 * </ul>
 */
@Service
public class GenRestoreService {

    private static final Logger log = LoggerFactory.getLogger(GenRestoreService.class);

    /** 백업 타임스탬프(계약 §5.2). 이 형태 외에는 어떤 문자열도 경로에 닿지 않는다. */
    private static final Pattern STAMP = Pattern.compile("^\\d{14}$");
    /** 복원 대상 최대 크기. */
    private static final long RESTORE_MAX_BYTES = 2L * 1024 * 1024;

    private final ForgeScreenService screenService;
    private final ForgeProjectService projectService;
    private final PathSafetyService pathSafetyService;
    private final AtomicFileWriter fileWriter;
    private final GenPlanner genPlanner;

    public GenRestoreService(ForgeScreenService screenService,
                             ForgeProjectService projectService,
                             PathSafetyService pathSafetyService,
                             AtomicFileWriter fileWriter,
                             GenPlanner genPlanner) {
        this.screenService = screenService;
        this.projectService = projectService;
        this.pathSafetyService = pathSafetyService;
        this.fileWriter = fileWriter;
        this.genPlanner = genPlanner;
    }

    /** 복원 결과. */
    public record RestoreResult(String artifactKey, String relativePath, String timestamp, String contentHash) {
    }

    /**
     * {@code artifactKey}의 {@code timestamp} 백업을 원래 자리로 되돌린다.
     *
     * @throws IllegalArgumentException 타임스탬프 형식 위반·백업 없음·크기 초과
     * @throws NotFoundException        계획에 없는 아티팩트
     */
    public RestoreResult restore(Long screenId, String artifactKey, String timestamp) {
        if (timestamp == null || !STAMP.matcher(timestamp).matches()) {
            throw new IllegalArgumentException("백업 시각 형식이 올바르지 않습니다.");
        }
        ForgeScreen screen = screenService.get(screenId);
        ForgeProject project = projectService.get(screen.getProjectId());
        PlanFile planFile = planned(screenId, artifactKey);

        Path targetRoot = Path.of(project.getTargetRootPath());
        Path canonicalRoot;
        try {
            canonicalRoot = targetRoot.toRealPath();
        } catch (Exception e) {
            throw new IllegalArgumentException("타겟 루트를 확인할 수 없습니다: " + e.getMessage());
        }

        // 🔒 안전 절대경로 + 정적 접미사. 클라이언트 문자열은 숫자 타임스탬프뿐이다.
        Path safeAbs = pathSafetyService.resolveSafeWritePath(targetRoot, planFile.relativePath());
        Path backup = safeAbs.resolveSibling(safeAbs.getFileName().toString() + ".bak-" + timestamp);

        String content;
        try {
            if (!Files.isRegularFile(backup)) {
                throw new IllegalArgumentException("해당 시각의 백업이 없습니다.");
            }
            if (Files.size(backup) > RESTORE_MAX_BYTES) {
                throw new IllegalArgumentException("백업 파일이 너무 큽니다.");
            }
            content = Files.readString(backup, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("백업을 읽지 못했습니다: " + e.getClass().getSimpleName());
        }

        // 복원도 원자적 쓰기 경유 — 현재 내용이 다시 .bak 으로 백업된다(되돌리기의 되돌리기 가능).
        fileWriter.write(safeAbs, canonicalRoot, content);
        log.info("[GenRestore] 복원 완료 — screenId={} artifact={} stamp={}", screenId, artifactKey, timestamp);

        return new RestoreResult(artifactKey, planFile.relativePath(), timestamp, ContentHash.sha256(content));
    }

    private PlanFile planned(Long screenId, String artifactKey) {
        GenPlan plan = genPlanner.plan(screenId);
        if (plan.failReason() != null) {
            throw new IllegalArgumentException(plan.failReason());
        }
        return plan.files().stream()
                .filter(f -> f.artifactKey().equals(artifactKey))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "이 화면의 생성 계획에 없는 아티팩트입니다: " + artifactKey));
    }
}
