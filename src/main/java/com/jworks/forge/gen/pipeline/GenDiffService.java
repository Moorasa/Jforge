package com.jworks.forge.gen.pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.jworks.forge.common.web.NotFoundException;
import com.jworks.forge.gen.context.TemplateContextBuilder;
import com.jworks.forge.gen.pipeline.GenArtifacts.ArtifactSpec;
import com.jworks.forge.gen.pipeline.GenPlanner.GenPlan;
import com.jworks.forge.gen.pipeline.GenPlanner.PlanFile;
import com.jworks.forge.gen.safety.PathSafetyService;
import com.jworks.forge.gen.template.TemplateRenderer;
import com.jworks.forge.project.domain.ForgeProject;
import com.jworks.forge.project.service.ForgeProjectService;
import com.jworks.forge.screen.domain.ForgeScreen;
import com.jworks.forge.screen.service.ForgeScreenService;

/**
 * 🔒 재생성 diff 미리보기 + 백업 목록 (P12, 계약 §16.3/§16.5). <b>읽기전용 — 파일쓰기 0.</b>
 *
 * <p>"덮어쓰기 n건"만 보고 확정하던 dry-run(P7-4)에, <b>무엇이 어떻게 바뀌는지</b>를 더한다.
 *
 * <p>안전 요지:
 * <ul>
 *   <li>경로는 {@link GenPlanner#plan} 결과에서만 얻는다 — 실산출과 <b>단일 소스</b>(드리프트 불가).</li>
 *   <li>읽기 대상은 경로안전 계층을 통과한 절대경로뿐이다. 백업 파일은 그 경로에
 *       <b>정적 접미사</b>({@code .bak-} + 14자리 숫자)만 붙여 만든다 — 클라이언트 문자열이
 *       경로에 들어가지 않는다.</li>
 *   <li>생성 예정 내용은 <b>메모리 렌더</b>로만 만든다(디스크 접근 0). stub은 실산출과 같은
 *       {@link StubGenerator#contentFor} + {@link CustomRegionMerger} 경로를 태워
 *       <b>실제로 쓰일 내용</b>을 보여준다.</li>
 * </ul>
 */
@Service
public class GenDiffService {

    private static final Logger log = LoggerFactory.getLogger(GenDiffService.class);

    /** stub 아티팩트 키(템플릿 렌더가 아니라 StubGenerator가 만드는 것들). */
    private static final Set<String> STUB_KEYS = Set.of("stubController", "stubMapper", "stubMapperXml");
    /** 백업 파일명 접미사 형태(AtomicFileWriter 계약 §5.2: {@code .bak-yyyyMMddHHmmss}). */
    private static final Pattern BACKUP_STAMP = Pattern.compile("^\\d{14}$");
    /** diff에서 보여줄 변경 주변 동일 줄 수. */
    private static final int DIFF_CONTEXT = 3;
    /** diff 대상 최대 크기. */
    private static final long DIFF_MAX_BYTES = 2L * 1024 * 1024;

    private final ForgeScreenService screenService;
    private final ForgeProjectService projectService;
    private final TemplateContextBuilder contextBuilder;
    private final TemplateRenderer renderer;
    private final PathSafetyService pathSafetyService;
    private final StubGenerator stubGenerator;
    private final GenPlanner genPlanner;

    public GenDiffService(ForgeScreenService screenService,
                          ForgeProjectService projectService,
                          TemplateContextBuilder contextBuilder,
                          TemplateRenderer renderer,
                          PathSafetyService pathSafetyService,
                          StubGenerator stubGenerator,
                          GenPlanner genPlanner) {
        this.screenService = screenService;
        this.projectService = projectService;
        this.contextBuilder = contextBuilder;
        this.renderer = renderer;
        this.pathSafetyService = pathSafetyService;
        this.stubGenerator = stubGenerator;
        this.genPlanner = genPlanner;
    }

    /** diff 응답. */
    public record DiffView(
            String artifactKey,
            String relativePath,
            boolean exists,
            String drift,
            boolean identical,
            boolean tooLarge,
            List<LineDiff.Hunk> hunks) {
    }

    /** 백업 1건. */
    public record BackupEntry(String timestamp, long size) {
    }

    /** 아티팩트 1건의 "지금 파일 vs 생성 예정 내용" 비교. */
    public DiffView diff(Long screenId, String artifactKey) {
        ForgeScreen screen = screenService.get(screenId);
        ForgeProject project = projectService.get(screen.getProjectId());

        PlanFile planFile = findPlanned(screenId, artifactKey);
        Path targetRoot = Path.of(project.getTargetRootPath());

        String current = planFile.exists() ? readTarget(targetRoot, planFile.relativePath()) : "";
        String next = renderNext(screen, project, artifactKey);
        // stub은 실제 쓰기 때 보호구역 병합을 거치므로, 미리보기도 같은 결과를 보여야 정직하다.
        if (STUB_KEYS.contains(artifactKey) && !current.isEmpty()) {
            next = CustomRegionMerger.merge(next, current);
        }

        LineDiff.Result result = LineDiff.diff(current, next, DIFF_CONTEXT);
        return new DiffView(artifactKey, planFile.relativePath(), planFile.exists(), planFile.drift(),
                result.identical(), result.tooLarge(), result.hunks());
    }

    /** 아티팩트 1건의 백업 목록(최신순). {@code .bak-yyyyMMddHHmmss} 형태만 인식한다. */
    public List<BackupEntry> backups(Long screenId, String artifactKey) {
        ForgeScreen screen = screenService.get(screenId);
        ForgeProject project = projectService.get(screen.getProjectId());
        PlanFile planFile = findPlanned(screenId, artifactKey);

        Path targetRoot = Path.of(project.getTargetRootPath());
        List<BackupEntry> entries = new ArrayList<>();
        try {
            Path safeAbs = pathSafetyService.resolveSafeWritePath(targetRoot, planFile.relativePath());
            Path dir = safeAbs.getParent();
            if (dir == null || !Files.isDirectory(dir)) {
                return entries;
            }
            String prefix = safeAbs.getFileName().toString() + ".bak-";
            try (Stream<Path> siblings = Files.list(dir)) {
                siblings.forEach(candidate -> {
                    String name = candidate.getFileName().toString();
                    if (!name.startsWith(prefix)) {
                        return;
                    }
                    String stamp = name.substring(prefix.length());
                    if (!BACKUP_STAMP.matcher(stamp).matches() || !Files.isRegularFile(candidate)) {
                        return;
                    }
                    try {
                        entries.add(new BackupEntry(stamp, Files.size(candidate)));
                    } catch (IOException ignored) {
                        // 크기 조회 실패 항목은 목록에서 제외(복원 대상으로도 부적합).
                    }
                });
            }
        } catch (Exception e) {
            log.warn("[GenDiff] 백업 목록 조회 실패 — {} : {}", artifactKey, e.getClass().getSimpleName());
            return List.of();
        }
        entries.sort(Comparator.comparing(BackupEntry::timestamp).reversed());
        return entries;
    }

    /** 계획에 없는 artifactKey는 404 — 임의 키로 경로를 만들어내지 못하게 한다. */
    private PlanFile findPlanned(Long screenId, String artifactKey) {
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

    /** 🔒 경로안전 통과값만 읽는다. 실패/과대는 빈 문자열(=전체 추가로 보임). */
    private String readTarget(Path targetRoot, String rel) {
        try {
            Path safeAbs = pathSafetyService.resolveSafeWritePath(targetRoot, rel);
            if (!Files.isRegularFile(safeAbs) || Files.size(safeAbs) > DIFF_MAX_BYTES) {
                return "";
            }
            return Files.readString(safeAbs, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[GenDiff] 현재 내용 읽기 실패 — {} : {}", rel, e.getClass().getSimpleName());
            return "";
        }
    }

    /** 생성 예정 내용을 메모리에서 만든다(디스크 쓰기 0). */
    private String renderNext(ForgeScreen screen, ForgeProject project, String artifactKey) {
        Map<String, Object> model = contextBuilder.build(screen, project);
        genPlanner.enrichRenderModel(model);

        if (STUB_KEYS.contains(artifactKey)) {
            return stubGenerator.contentFor(artifactKey,
                    (String) model.get("packageBase"), (String) model.get("stem"), model);
        }
        String archetype = (String) model.get("archetype");
        for (ArtifactSpec spec : genPlanner.planArtifacts(archetype, model)) {
            if (spec.artifactKey().equals(artifactKey)) {
                return renderer.render(spec.templateKey(), model);
            }
        }
        throw new NotFoundException("렌더할 수 없는 아티팩트입니다: " + artifactKey);
    }
}
