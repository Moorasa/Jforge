package com.jworks.forge.gen.pipeline;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jworks.forge.gen.context.TemplateContextBuilder;
import com.jworks.forge.gen.hist.GenHist;
import com.jworks.forge.gen.hist.GenHistMapper;
import com.jworks.forge.gen.pipeline.GenArtifacts.ArtifactSpec;
import com.jworks.forge.gen.safety.PathSafetyService;
import com.jworks.forge.project.domain.ForgeProject;
import com.jworks.forge.project.service.ForgeProjectService;
import com.jworks.forge.screen.domain.ForgeScreen;
import com.jworks.forge.screen.service.ForgeScreenService;

/**
 * 🔒 생성 dry-run 계획기 (P7-4). <b>읽기전용 — 파일쓰기/렌더 0.</b>
 *
 * <p>화면 1건에 대해 "생성 시 어떤 파일이 어디에 쓰이는지(신규/덮어쓰기)"를 계산한다.
 * 보안 심장({@link ScreenGenerator}/{@link AtomicFileWriter}/{@link PathSafetyService})은
 * <b>수정하지 않고 호출만</b> 한다:
 * <ul>
 *   <li>검증/컨텍스트: {@link TemplateContextBuilder#build} <b>동일 경로 재사용</b>(role/stem/
 *       archetype/packageBase 재검증 하드 실패 동일).</li>
 *   <li>아티팩트 목록: {@link GenArtifacts} <b>정적 화이트리스트 맵 조회만</b>. 조립 규칙은
 *       {@link ScreenGenerator#generate}의 planArtifacts(계약 §1.1/§8.1/§9.2)를 <b>미러</b>하며,
 *       드리프트는 {@code GenPlannerTest}(plan == 실산출 파일목록 대조)로 회귀 고정한다.</li>
 *   <li>stub 경로: {@link StubGenerator#planStubs} — 실산출과 <b>물리적 단일 소스</b>.</li>
 *   <li>존재 판정: {@link PathSafetyService#resolveSafeWritePath} <b>호출만</b> 후
 *       {@link Files#exists} 확인. 어떤 경로도 만들거나 쓰지 않는다.</li>
 * </ul>
 *
 * <p>번들 런타임(1층위, RuntimeSyncer)은 §11 버전 정책에 따라 자동 동기화되므로 plan 목록에
 * 포함하지 않는다(30+ 파일 나열은 dry-run 의 판단 노이즈 — 화면 산출물 + stub 만 계획).
 */
@Service
public class GenPlanner {

    private static final Logger log = LoggerFactory.getLogger(GenPlanner.class);

    /** listArea 슬롯키(계약 §2.2 리터럴 상수 조회 — ScreenGenerator 와 동일 값). */
    private static final String SLOT_LIST_AREA = "listArea";
    private static final String KEY_MODULE_TYPE_CODE = "moduleTypeCode";

    /** 상세영역 슬롯키(계약 §9.2 — ScreenGenerator 와 동일 값). */
    private static final List<String> DETAIL_SLOT_KEYS =
            List.of("detailBasic", "detailTabs", "detailToolbar");

    /** P12 드리프트 판정 결과(계약 §16.2). */
    public static final String DRIFT_NEW = "NEW";
    public static final String DRIFT_UNCHANGED = "UNCHANGED";
    public static final String DRIFT_MODIFIED = "MODIFIED";
    public static final String DRIFT_UNKNOWN = "UNKNOWN";

    /** 드리프트 판정을 위해 훑을 최근 이력 행 수(작업량 상한). */
    private static final int DRIFT_HISTORY_DEPTH = 20;
    /** 드리프트 해시 계산 대상 최대 크기(초과 시 UNKNOWN). */
    private static final long DRIFT_MAX_BYTES = 4L * 1024 * 1024;

    private final ForgeScreenService screenService;
    private final ForgeProjectService projectService;
    private final TemplateContextBuilder contextBuilder;
    private final PathSafetyService pathSafetyService;
    private final StubGenerator stubGenerator;
    private final GenHistMapper genHistMapper;
    private final ObjectMapper objectMapper;

    public GenPlanner(
            ForgeScreenService screenService,
            ForgeProjectService projectService,
            TemplateContextBuilder contextBuilder,
            PathSafetyService pathSafetyService,
            StubGenerator stubGenerator,
            GenHistMapper genHistMapper,
            ObjectMapper objectMapper) {
        this.screenService = screenService;
        this.projectService = projectService;
        this.contextBuilder = contextBuilder;
        this.pathSafetyService = pathSafetyService;
        this.stubGenerator = stubGenerator;
        this.genHistMapper = genHistMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 계획 파일 1건.
     *
     * @param exists 타겟 존재 여부(true=덮어쓰기 예정)
     * @param drift  P12: 마지막 생성 이후 외부 수정 여부 — {@code NEW}(파일 없음) /
     *               {@code UNCHANGED}(생성물 그대로) / {@code MODIFIED}(<b>사람이 손댐</b>) /
     *               {@code UNKNOWN}(이력에 해시 없음·읽기 실패)
     */
    public record PlanFile(String artifactKey, String relativePath, boolean exists, String drift) {
    }

    /**
     * dry-run 계획 결과.
     *
     * @param targetRootExists 타겟 루트 디렉터리 존재 여부(없으면 생성 시 전체 FAIL 예정)
     * @param files            계획 파일 목록(화면 산출물 + stub 3종). 실패 시 빈 목록.
     * @param failReason       계획 자체가 불가한 사유(컨텍스트 검증 실패 등). 정상이면 null.
     */
    public record GenPlan(boolean targetRootExists, List<PlanFile> files, String failReason) {

        static GenPlan fail(String reason) {
            return new GenPlan(false, List.of(), reason);
        }
    }

    /**
     * {@code screenId} 화면의 생성 계획을 계산한다. <b>파일쓰기 0.</b>
     *
     * @throws com.jworks.forge.common.web.NotFoundException 화면/프로젝트 미존재 시(404)
     */
    public GenPlan plan(Long screenId) {
        ForgeScreen screen = screenService.get(screenId);                 // 없으면 404
        ForgeProject project = projectService.get(screen.getProjectId()); // 없으면 404

        // 컨텍스트 구성(검증 동일 경로). 실패 사유는 생성 시에도 동일하게 실패한다.
        Map<String, Object> model;
        try {
            model = contextBuilder.build(screen, project);
        } catch (RuntimeException e) {
            log.warn("[GenPlan] 컨텍스트 구성 실패 — screenId={} : {}", screenId, e.getMessage());
            return GenPlan.fail("컨텍스트 구성 실패: " + e.getMessage());
        }

        String role = (String) model.get("role");
        String stem = (String) model.get("stem");
        String archetype = (String) model.get("archetype");
        String packageBase = (String) model.get("packageBase");

        List<ArtifactSpec> specs = planArtifacts(archetype, model);
        if (specs.isEmpty()) {
            return GenPlan.fail("지원되지 않는 아키타입이거나 아티팩트 없음: " + archetype);
        }

        Path targetRoot = Path.of(project.getTargetRootPath());
        boolean rootExists = Files.isDirectory(targetRoot);

        // P12(계약 §16): 마지막 생성 때 기록한 내용 해시. 디스크와 대조해 외부 수정을 판정한다.
        Map<String, String> lastHashes = lastRecordedHashes(screenId);

        List<PlanFile> files = new ArrayList<>(specs.size() + 3);
        for (ArtifactSpec spec : specs) {
            String rel = buildRelativePath(spec, project, role, stem);
            files.add(planFile(spec.artifactKey(), rel, targetRoot, rootExists, lastHashes));
        }
        // stub 3종 — StubGenerator.planStubs 와 물리적 단일 소스(실산출 경로와 동일).
        for (StubGenerator.StubPlanEntry stub : stubGenerator.planStubs(packageBase, stem)) {
            files.add(planFile(stub.artifactKey(), stub.relativePath(), targetRoot, rootExists, lastHashes));
        }
        return new GenPlan(rootExists, files, null);
    }

    /** 계획 1건 + 드리프트 판정. */
    private PlanFile planFile(String artifactKey, String rel, Path targetRoot,
                              boolean rootExists, Map<String, String> lastHashes) {
        boolean exists = rootExists && targetExists(targetRoot, rel);
        return new PlanFile(artifactKey, rel, exists, driftOf(targetRoot, rel, exists, lastHashes));
    }

    /**
     * 🔒 드리프트 판정(P12). 읽기 전용 — 경로안전 통과값만 읽고, 어떤 실패도
     * {@code UNKNOWN}으로 수렴시킨다(판정 불가가 생성 계획 실패로 번지지 않는다).
     */
    private String driftOf(Path targetRoot, String rel, boolean exists, Map<String, String> lastHashes) {
        if (!exists) {
            return DRIFT_NEW;
        }
        String recorded = lastHashes.get(rel);
        if (recorded == null) {
            return DRIFT_UNKNOWN; // 이력이 없거나 해시 이전(P12 이전)에 생성된 파일
        }
        try {
            Path safeAbs = pathSafetyService.resolveSafeWritePath(targetRoot, rel);
            if (Files.size(safeAbs) > DRIFT_MAX_BYTES) {
                return DRIFT_UNKNOWN;
            }
            String actual = ContentHash.sha256(Files.readAllBytes(safeAbs));
            return recorded.equals(actual) ? DRIFT_UNCHANGED : DRIFT_MODIFIED;
        } catch (Exception e) {
            log.debug("[GenPlan] 드리프트 판정 불가 — {} : {}", rel, e.getClass().getSimpleName());
            return DRIFT_UNKNOWN;
        }
    }

    /**
     * 최근 생성 이력에서 {@code relativePath → 내용 해시}를 모은다(최신 우선).
     * 이력 조회/파싱 실패는 빈 맵으로 수렴한다 — 드리프트 판정만 못 할 뿐 계획은 정상 동작한다.
     */
    private Map<String, String> lastRecordedHashes(Long screenId) {
        Map<String, String> hashes = new HashMap<>();
        try {
            List<GenHist> history = genHistMapper.selectByScreen(screenId); // 최신순
            if (history == null) {
                return hashes;
            }
            int depth = Math.min(history.size(), DRIFT_HISTORY_DEPTH);
            for (int i = 0; i < depth; i++) {
                String json = history.get(i).getFileListJson();
                if (json == null || json.isBlank()) {
                    continue;
                }
                JsonNode arr = objectMapper.readTree(json);
                if (!arr.isArray()) {
                    continue;
                }
                for (JsonNode node : arr) {
                    JsonNode path = node.get("relativePath");
                    JsonNode hash = node.get("hash");
                    if (path != null && path.isTextual() && hash != null && hash.isTextual()) {
                        hashes.putIfAbsent(path.asText(), hash.asText()); // 최신 행 우선
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[GenPlan] 생성 이력 해시 조회 실패(드리프트 판정 생략): {}", e.getClass().getSimpleName());
            return Map.of();
        }
        return hashes;
    }

    /**
     * 🔒 존재 판정도 경로안전 계층 통과값으로만 수행(임의 경로 프로브 차단). 해석 실패(경로안전
     * 위반 등)는 "존재하지 않음"으로 수렴 — 실제 생성 시 해당 파일은 개별 FAIL 로 드러난다.
     */
    private boolean targetExists(Path targetRoot, String rel) {
        try {
            return Files.exists(pathSafetyService.resolveSafeWritePath(targetRoot, rel));
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ==================================================================================
    // 이하 조립 규칙은 ScreenGenerator(계약 §1.1/§8.1/§9.2) 미러 — GenArtifacts 정적 맵 조회만.
    // 드리프트 방지: GenPlannerTest 가 plan 파일목록 == 실제 generate 파일목록(런타임 제외)을 고정.
    // ==================================================================================

    /**
     * 렌더 파생 플래그를 model 에 주입 — ScreenGenerator 2b~2d(§8.1 listAreaViewSuffix /
     * §9.2 hasDetail / design 메타) 미러. RunPreviewService(P9 실행 미리보기)가 렌더 전에 호출한다.
     * 패키지 전용 — 외부 노출 없음.
     */
    void enrichRenderModel(Map<String, Object> model) {
        model.put("listAreaViewSuffix", resolveListAreaViewSuffix(model));
        model.put("hasDetail", hasDetailArea(model));
        model.put("hasDesignMetadata", hasDesignMetadata(model));
    }

    /** §8.1: 첫 지원 listArea 뷰의 include 접미사(GenArtifacts 정본 조회). 미지원/미배치 null. */
    private String resolveListAreaViewSuffix(Map<String, Object> model) {
        for (String moduleTypeCode : listAreaModuleTypeCodes(model)) {
            String suffix = GenArtifacts.listAreaViewSuffix(moduleTypeCode);
            if (suffix != null) {
                return suffix;
            }
        }
        return null;
    }

    /** 패키지 전용(RunPreviewService 공용): 아티팩트 조립 규칙 단일 미러 지점. */
    List<ArtifactSpec> planArtifacts(String archetype, Map<String, Object> model) {
        List<ArtifactSpec> plan = new ArrayList<>();
        List<ArtifactSpec> archetypeSpecs =
                GenArtifacts.ARCHETYPE_ARTIFACTS.getOrDefault(archetype, List.of());
        plan.addAll(archetypeSpecs);

        // 모듈 아티팩트 기여 슬롯 제한 — ScreenGenerator 와 **동일 게이트**를 써야 dry-run 계획과
        // 실제 산출 경로가 갈라지지 않는다(§16 드리프트 판정도 이 계획을 쓴다).
        for (String moduleTypeCode : moduleTypeCodesIn(model, GenArtifacts.moduleArtifactSlots(archetype))) {
            List<ArtifactSpec> moduleSpecs = GenArtifacts.MODULE_ARTIFACTS.get(moduleTypeCode);
            if (moduleSpecs != null) {
                plan.addAll(moduleSpecs);
            }
        }

        List<ArtifactSpec> detailSpecs = GenArtifacts.ARCHETYPE_DETAIL_ARTIFACTS.get(archetype);
        if (detailSpecs != null && hasDetailArea(model)) {
            plan.addAll(detailSpecs);
        }

        if (hasDesignMetadata(model)) {
            plan.add(GenArtifacts.DESIGN_ARTIFACT);
        }

        if (archetypeSpecs.isEmpty()) {
            return List.of();
        }
        return plan;
    }

    private boolean hasDetailArea(Map<String, Object> model) {
        Object slotsObj = model.get("slots");
        if (!(slotsObj instanceof Map<?, ?> slots)) {
            return false;
        }
        for (String slotKey : DETAIL_SLOT_KEYS) {
            if (slots.get(slotKey) instanceof List<?> instances && !instances.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** ScreenGenerator와 같은 조건으로 선언형 설계 메타 파일을 dry-run에 포함한다. */
    private boolean hasDesignMetadata(Map<String, Object> model) {
        Object slotsObj = model.get("slots");
        if (!(slotsObj instanceof Map<?, ?> slots)) {
            return false;
        }
        for (Object instancesObj : slots.values()) {
            if (!(instancesObj instanceof List<?> instances)) { continue; }
            for (Object instObj : instances) {
                if (!(instObj instanceof Map<?, ?> inst)) { continue; }
                if (inst.get("data") instanceof Map<?, ?>) { return true; }
                if (inst.get("events") instanceof List<?> events && !events.isEmpty()) { return true; }
            }
        }
        return false;
    }

    private List<String> listAreaModuleTypeCodes(Map<String, Object> model) {
        List<String> codes = new ArrayList<>();
        Object slotsObj = model.get("slots");
        if (!(slotsObj instanceof Map<?, ?> slots)) {
            return codes;
        }
        Object listAreaObj = slots.get(SLOT_LIST_AREA);
        if (!(listAreaObj instanceof List<?> instances)) {
            return codes;
        }
        for (Object instObj : instances) {
            if (instObj instanceof Map<?, ?> inst) {
                Object code = inst.get(KEY_MODULE_TYPE_CODE);
                if (code instanceof String s) {
                    codes.add(s);
                }
            }
        }
        return codes;
    }

    /** 지정한 슬롯들에 놓인 moduleTypeCode 목록(ScreenGenerator.moduleTypeCodesIn 미러). */
    private List<String> moduleTypeCodesIn(Map<String, Object> model, Set<String> slotKeys) {
        List<String> codes = new ArrayList<>();
        if (slotKeys.isEmpty()) { return codes; }
        Object slotsObj = model.get("slots");
        if (!(slotsObj instanceof Map<?, ?> slots)) { return codes; }
        for (Map.Entry<?, ?> entry : slots.entrySet()) {
            if (!(entry.getKey() instanceof String slotKey) || !slotKeys.contains(slotKey)) { continue; }
            if (!(entry.getValue() instanceof List<?> instances)) { continue; }
            for (Object instObj : instances) {
                if (instObj instanceof Map<?, ?> inst && inst.get(KEY_MODULE_TYPE_CODE) instanceof String code) {
                    codes.add(code);
                }
            }
        }
        return codes;
    }

    /** 계약 §1.2 relativePath 조립(ScreenGenerator.buildRelativePath 미러): 자유문자열 0. */
    private String buildRelativePath(ArtifactSpec spec, ForgeProject project,
                                     String role, String stem) {
        String basePath = switch (spec.baseKind()) {
            case JSP -> defaultBasePath(project.getJspBasePath(), "jsp");
            case JS -> defaultBasePath(project.getJsBasePath(), "js");
            case CSS -> defaultBasePath(project.getCssBasePath(), "css");
        };
        String fileName = stem + spec.nameSuffix() + "." + spec.ext();
        return basePath + "/" + role + "/" + stem + "/" + fileName;
    }

    private String defaultBasePath(String configuredPath, String defaultPath) {
        return (configuredPath == null || configuredPath.isBlank()) ? defaultPath : configuredPath;
    }
}
