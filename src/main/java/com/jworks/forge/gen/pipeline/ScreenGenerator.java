package com.jworks.forge.gen.pipeline;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.jworks.forge.gen.context.TemplateContextBuilder;
import com.jworks.forge.gen.pipeline.GenArtifacts.ArtifactSpec;
import com.jworks.forge.gen.pipeline.GenArtifacts.BaseKind;
import com.jworks.forge.gen.safety.PathSafetyException;
import com.jworks.forge.gen.safety.PathSafetyService;
import com.jworks.forge.gen.template.TemplateRenderer;
import com.jworks.forge.project.domain.ForgeProject;
import com.jworks.forge.project.service.ForgeProjectService;
import com.jworks.forge.screen.domain.ForgeScreen;
import com.jworks.forge.screen.service.ForgeScreenService;

/**
 * 🔒 생성 파이프라인 오케스트레이션 (P4-4, 계약 §1/§4/§5). <b>P4 보안의 심장.</b>
 *
 * <p>흐름: screen 로드(없으면 404) → project 로드 → {@link TemplateContextBuilder}로 컨텍스트
 * 구성(role/stem/archetype 재검증 하드 실패) → 아티팩트 목록(계약 §1.1 7종) 순회:
 * 각 아티팩트를 {@link TemplateRenderer}로 렌더 → relativePath 조립(계약 §1.2: 정적 세그먼트 +
 * role + stem만) → {@link PathSafetyService#resolveSafeWritePath}로 안전 절대경로 획득 →
 * {@link AtomicFileWriter}로 원자적 쓰기. 결과({@link GenResult})를 반환한다.
 *
 * <p>보안 요지:
 * <ul>
 *   <li>모든 쓰기는 예외 없이 {@code resolveSafeWritePath} 반환값만 사용(계약 §4.1).</li>
 *   <li>TEMPLATE_KEY·뷰코드·아티팩트 목록은 {@link GenArtifacts} 정적 맵으로만 조회(계약 §4.4).
 *       DB에서 읽은 moduleTypeCode를 파일경로로 직접 쓰지 않는다(화이트리스트 맵 조회).</li>
 *   <li>relativePath 세그먼트는 정적 문자열 + role + stem뿐(계약 §1.2/§4.3). props값·instanceId·
 *       label 등 자유문자열은 경로/파일명에 미포함.</li>
 *   <li>부분실패는 개별 포착 → 성공/실패 목록 수집. 이미 성공한 파일은 롤백하지 않는다(계약 §5.3).</li>
 * </ul>
 *
 * <p>P4-6: 화면 3종 산출 뒤 (b) 런타임 공통(1층위)을 {@link RuntimeSyncer}로 1회 동기화(존재하면 skip)하고
 * (c) Controller/Mapper stub 3종을 {@link StubGenerator}로 생성한다. 둘 다 타겟 쓰기는 예외 없이
 * {@code resolveSafeWritePath} + {@link AtomicFileWriter} 경유이며, stub 패키지 폴더는
 * {@link TemplateContextBuilder}에서 앵커드 정규식으로 검증된 packageBase만 쓴다(자유문자열 0).
 * 화면 반복패턴(2층위) 공통추출은 P6 이관.
 *
 * <p>GEN_HIST 기록·트리거 API/UI는 P4-5(이 태스크 아님). 이 클래스는 <b>reviewer 🔒 검수 대상</b>이다.
 */
@Service
public class ScreenGenerator {

    private static final Logger log = LoggerFactory.getLogger(ScreenGenerator.class);

    /** listArea 슬롯키(계약 §2.2 리터럴 상수 조회). */
    private static final String SLOT_LIST_AREA = "listArea";
    private static final String KEY_MODULE_TYPE_CODE = "moduleTypeCode";

    /**
     * 상세영역 슬롯키(계약 §9.2, P5.5). 이 중 하나라도 인스턴스가 있으면 Detail 세트를 산출한다.
     * 리터럴 상수 조회(§2.2) — model.slots는 이미 화이트리스트 통과 인스턴스만 담는다.
     */
    private static final List<String> DETAIL_SLOT_KEYS =
            List.of("detailBasic", "detailTabs", "detailToolbar");
    private static final String KEY_HAS_DETAIL = "hasDetail";

    private final ForgeScreenService screenService;
    private final ForgeProjectService projectService;
    private final TemplateContextBuilder contextBuilder;
    private final TemplateRenderer renderer;
    private final PathSafetyService pathSafetyService;
    private final AtomicFileWriter fileWriter;
    private final RuntimeSyncer runtimeSyncer;
    private final StubGenerator stubGenerator;

    public ScreenGenerator(
            ForgeScreenService screenService,
            ForgeProjectService projectService,
            TemplateContextBuilder contextBuilder,
            TemplateRenderer renderer,
            PathSafetyService pathSafetyService,
            AtomicFileWriter fileWriter,
            RuntimeSyncer runtimeSyncer,
            StubGenerator stubGenerator) {
        this.screenService = screenService;
        this.projectService = projectService;
        this.contextBuilder = contextBuilder;
        this.renderer = renderer;
        this.pathSafetyService = pathSafetyService;
        this.fileWriter = fileWriter;
        this.runtimeSyncer = runtimeSyncer;
        this.stubGenerator = stubGenerator;
    }

    /**
     * {@code screenId} 화면을 생성해 타겟 프로젝트 폴더에 3종 세트를 쓴다.
     *
     * @param screenId 대상 화면 ID
     * @return 파일 단위 성공/실패 + RESULT_CODE
     * @throws com.jworks.forge.common.web.NotFoundException 화면/프로젝트 미존재 시(404)
     */
    public GenResult generate(Long screenId) {
        // 1) 입력 로드. 미존재는 NotFoundException(404) — 파일을 하나도 쓰지 않음.
        ForgeScreen screen = screenService.get(screenId);          // 없으면 404
        ForgeProject project = projectService.get(screen.getProjectId()); // 없으면 404

        // 2) 컨텍스트 구성. role/stem/archetype 재검증 하드 실패는 여기서 예외(파일 0).
        //    입력/컨텍스트 실패는 계약 §5.3상 FAIL(아무 파일도 못 씀).
        Map<String, Object> model;
        try {
            model = contextBuilder.build(screen, project);
        } catch (RuntimeException e) {
            log.error("[Gen] 컨텍스트 구성 실패 — screenId={} : {}", screenId, e.getMessage());
            return GenResult.fail("컨텍스트 구성 실패: " + e.getMessage());
        }

        // 컨텍스트가 재검증·정규화한 값만 사용(경로 세그먼트로 안전).
        String role = (String) model.get("role");
        String stem = (String) model.get("stem");
        String archetype = (String) model.get("archetype");
        // packageBase는 TemplateContextBuilder에서 앵커드 정규식(§1.2)으로 하드 검증된 값 — stub 경로 세그먼트로 안전.
        String packageBase = (String) model.get("packageBase");

        // 2b) 🔒 listArea 뷰 본문 include 접미사(listAreaViewSuffix, 계약 §8.1)를 model에 실는다.
        //     include 파일명은 파이프라인 산출 아티팩트 파일명과 반드시 일치해야 하므로, 그 정본인
        //     GenArtifacts.MODULE_ARTIFACTS(같은 pipeline 패키지)를 직접 조회해 파생한다 → 물리적 단일 소스.
        //     (파일명 조립 로직과 완전히 같은 소스에서 접미사를 얻으므로 드리프트가 원천 불가능.)
        //     미지원/미배치면 null → list.ftl이 본문 include를 조건부 생략(forward-compat, 문서 실패 0).
        model.put("listAreaViewSuffix", resolveListAreaViewSuffix(model));

        // 2c) 🔒 상세영역 존재 여부(hasDetail, 계약 §9.2)를 model.slots에서 1회 계산해 model에 실는다.
        //     이 단일 값으로 (a) Detail 아티팩트 조건부 산출(planArtifacts)과 (b) shell.ftl의 Detail
        //     include(<#if hasDetail>)를 함께 제어한다 → 산출 조건과 include 조건이 동일 소스라
        //     드리프트가 원천 불가능(§8.1 listAreaViewSuffix 단일소스 선례와 동형). model.slots는
        //     화이트리스트 통과 인스턴스만 담으므로, 미등록/미배치 detail은 false로 수렴(forward-compat).
        model.put(KEY_HAS_DETAIL, hasDetailArea(model));
        // P9: data/events가 한 건이라도 있을 때만 설계 메타 JS를 생성·shell에 연결한다.
        // 기존 화면(메타 없음)은 산출 파일 목록과 shell 바이트가 그대로 유지된다.
        model.put("hasDesignMetadata", hasDesignMetadata(model));

        // 3) 아티팩트 목록 결정(정적 맵 조회, 계약 §1.1/§4.4).
        List<ArtifactSpec> plan = planArtifacts(archetype, model);
        if (plan.isEmpty()) {
            log.error("[Gen] 계획된 아티팩트 0 — archetype={} screenId={}", archetype, screenId);
            return GenResult.fail("지원되지 않는 아키타입이거나 아티팩트 없음: " + archetype);
        }

        // 타겟 루트(프로젝트 메타). resolveSafeWritePath의 앵커.
        Path targetRoot = Path.of(project.getTargetRootPath());

        // 🔒 검증 루트 실경로를 1회 계산(쓰기 직전 재확인용, 계약 §5.1). 해석 실패는 전체 FAIL로
        //    처리한다(존재하지 않는/비정상 targetRoot면 아무 파일도 쓰지 않는 게 안전).
        final Path canonicalRoot;
        try {
            canonicalRoot = targetRoot.toRealPath();
        } catch (java.io.IOException e) {
            log.error("[Gen] targetRoot 실경로 해석 실패 — screenId={} root={} : {}",
                    screenId, targetRoot, e.getMessage());
            return GenResult.fail("targetRoot 실경로 해석 실패: " + e.getMessage());
        }

        // 4) 아티팩트별 렌더 → 경로안전 → 원자적 쓰기. 개별 실패 포착(계약 §5.3).
        List<GenFile> results = new ArrayList<>(plan.size());
        for (ArtifactSpec spec : plan) {
            results.add(generateOne(spec, model, project, targetRoot, canonicalRoot, role, stem));
        }

        // 4b) 런타임 공통(1층위) 1회 동기화(존재하면 skip, P4-6). 화면 반복 생성 시 재복사 0.
        //     🔒 복사 경로도 전부 resolveSafeWritePath + AtomicFileWriter 경유(RuntimeSyncer 내부).
        results.addAll(runtimeSyncer.sync(targetRoot, canonicalRoot));

        // 4c) Controller/Mapper stub 3종 생성(빈 골격, P4-6). 패키지 폴더는 검증된 packageBase(점→슬래시)+
        //     정적 세그먼트 + 검증된 stem만. 🔒 stub 경로도 resolveSafeWritePath + AtomicFileWriter 경유.
        //     ※ 화면 반복패턴(2층위) 공통추출은 P6 이관 — P4는 per-screen 산출 + stub 골격만.
        //     P10(계약 §14): model을 함께 넘겨 서버 바인딩(data.table 등)이 선언된 화면이면 stub을
        //     실제 조회 API로 승격시킨다. 경로/파일명/계획은 불변(내용만 달라짐) — 이 호출부의
        //     경로안전·쓰기 경로는 무변경이다.
        results.addAll(stubGenerator.generateStubs(packageBase, stem, targetRoot, canonicalRoot, model));

        GenResult result = GenResult.of(results);
        log.info("[Gen] 완료 — screenId={} result={} ({}/{} 성공)",
                screenId, result.resultCode(),
                results.stream().filter(GenFile::success).count(), results.size());
        return result;
    }

    /**
     * 아키타입 shell/list 4종 + (listArea에 TABLE_VIEW가 있으면) 모듈 3종을 정적 맵에서 조립한다.
     * TABLE_VIEW 인스턴스가 없으면 모듈 3종은 스킵(경고) — 문서 전체 실패 아님(계약 §1.1).
     */
    private List<ArtifactSpec> planArtifacts(String archetype, Map<String, Object> model) {
        List<ArtifactSpec> plan = new ArrayList<>();

        // 아키타입 고정 아티팩트(정적 맵). 미등록 아키타입은 빈 목록 → 상위에서 FAIL.
        List<ArtifactSpec> archetypeSpecs =
                GenArtifacts.ARCHETYPE_ARTIFACTS.getOrDefault(archetype, List.of());
        plan.addAll(archetypeSpecs);

        // 모듈 아티팩트는 **기여 슬롯**(GenArtifacts.MODULE_ARTIFACT_SLOTS)에 놓인 인스턴스에서만
        // 나온다. 모듈 템플릿이 자기 슬롯을 전제로 쓰였기 때문이다(예: tableView.ftl → listArea[0]).
        // 전 슬롯을 훑던 예전 방식은 iframe 패인·상세 슬롯에 뷰 모듈을 놓았을 때 렌더 실패 →
        // 조용한 PARTIAL 을 만들었다.
        for (String moduleTypeCode : moduleTypeCodesIn(model, GenArtifacts.moduleArtifactSlots(archetype))) {
            List<ArtifactSpec> moduleSpecs = GenArtifacts.MODULE_ARTIFACTS.get(moduleTypeCode);
            if (moduleSpecs == null) {
                // TABLE_VIEW 외 모듈(SEARCH_FILTER_BAR/TOOLBAR 등)은 별도 파일 산출 아님(shell/list 내부 조건부).
                log.debug("[Gen] listArea 모듈 '{}'은 별도 파일 산출 아님 — 스킵", moduleTypeCode);
                continue;
            }
            plan.addAll(moduleSpecs);
        }

        // 상세영역(Detail) 세트 조건부 추가(계약 §9.2). detail 슬롯이 하나라도 있으면(hasDetail) List와
        // 동형의 Detail 3종을 산출한다. 없으면 추가하지 않는다(기존 골든 무손상의 근거). archetype에
        // 상세영역이 정의된 경우(ARCHETYPE_DETAIL_ARTIFACTS 등록)에만 조회 — MGMT_LIST_DETAIL만 해당.
        List<ArtifactSpec> detailSpecs = GenArtifacts.ARCHETYPE_DETAIL_ARTIFACTS.get(archetype);
        if (detailSpecs != null && Boolean.TRUE.equals(model.get(KEY_HAS_DETAIL))) {
            plan.addAll(detailSpecs);
        }

        if (Boolean.TRUE.equals(model.get("hasDesignMetadata"))) {
            plan.add(GenArtifacts.DESIGN_ARTIFACT);
        }

        if (archetypeSpecs.isEmpty()) {
            return List.of();
        }
        // listArea 슬롯이 있는데 지원되는(=MODULE_ARTIFACTS 등록) 뷰 인스턴스가 하나도 없으면 경고(계약 §8.1
        // forward-compat). TABLE_VIEW 리터럴에 의존하지 않고 화이트리스트 맵 조회로 판정한다.
        // listArea 슬롯 자체가 없는 아키타입(예: DUAL_LAYOUT — leftArea/rightArea만)은 경고 대상이 아니다.
        if (hasListAreaSlot(model) && !hasSupportedListAreaView(model)) {
            log.warn("[Gen] listArea에 지원 뷰 인스턴스 없음 — 모듈 3종 스킵(shell/list만 생성)");
        }
        return plan;
    }

    /**
     * listArea 인스턴스 중 <b>{@link GenArtifacts#MODULE_ARTIFACTS}에 등록된(지원되는)</b> 뷰가
     * 하나라도 있는지(계약 §8.1). moduleTypeCode 리터럴에 의존하지 않고 화이트리스트 맵 조회로만 판정.
     */
    private boolean hasSupportedListAreaView(Map<String, Object> model) {
        for (String moduleTypeCode : listAreaModuleTypeCodes(model)) {
            if (GenArtifacts.MODULE_ARTIFACTS.containsKey(moduleTypeCode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * listArea 단일 인스턴스 전제(MVP, 계약 §8.1): 첫 번째로 <b>지원되는</b> 뷰 인스턴스의
     * moduleTypeCode를 {@link GenArtifacts#listAreaViewSuffix}(=아티팩트 파일명 정본 {@code MODULE_ARTIFACTS}
     * 조회)로 해석해 include 파일 접미사를 돌려준다. 접미사 소스와 아티팩트 파일명 소스가 <b>동일</b>하므로
     * include 파일명과 산출 JSP 파일명이 구조적으로 일치한다(드리프트 불가능). 지원 뷰가 없으면 {@code null}.
     */
    private String resolveListAreaViewSuffix(Map<String, Object> model) {
        for (String moduleTypeCode : listAreaModuleTypeCodes(model)) {
            String suffix = GenArtifacts.listAreaViewSuffix(moduleTypeCode);
            if (suffix != null) {
                return suffix; // 첫 지원 뷰만 사용(초과분은 P6 뷰 전환).
            }
        }
        return null;
    }

    /**
     * 상세영역 슬롯(detailBasic/detailTabs/detailToolbar) 중 <b>인스턴스가 하나라도 있는지</b>(계약 §9.2).
     * model.slots는 {@link TemplateContextBuilder}가 화이트리스트 통과 인스턴스만 담은 값이므로,
     * 미지원 detail 모듈타입·미배치는 자연히 false로 수렴한다(forward-compat). 리터럴 슬롯키 조회만.
     */
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

    /** 선언형 데이터 바인딩 또는 이벤트 처리기가 하나라도 있으면 설계 메타 파일을 생성한다. */
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

    /** model.slots에 listArea 슬롯 키가 존재하는지(DUAL_LAYOUT 등 listArea 미보유 아키타입 구분용). */
    private boolean hasListAreaSlot(Map<String, Object> model) {
        return model.get("slots") instanceof Map<?, ?> slots && slots.containsKey(SLOT_LIST_AREA);
    }

    /** listArea 슬롯의 인스턴스들에서 moduleTypeCode를 순서대로 뽑는다(리터럴 키 조회, 계약 §2.2). */
    @SuppressWarnings("unchecked")
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

    /** 지정한 슬롯들에 놓인 moduleTypeCode 목록(문서 순서). 슬롯 집합이 비면 빈 목록. */
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

    /**
     * 아티팩트 1건: 렌더 → relativePath 조립 → resolveSafeWritePath → 원자적 쓰기.
     * 렌더/경로안전/쓰기 실패는 개별 포착해 {@link GenFile#fail}로 반환(계약 §5.3).
     * PathSafetyException·렌더 오류 파일은 즉시 실패 — 타겟 루트 밖에 아무것도 쓰지 않는다.
     */
    private GenFile generateOne(
            ArtifactSpec spec, Map<String, Object> model, ForgeProject project,
            Path targetRoot, Path canonicalRoot, String role, String stem) {

        // relativePath는 항상 정적 세그먼트 + role + stem만으로 조립(계약 §1.2/§4.3).
        String relativePath = buildRelativePath(spec, project, role, stem);
        try {
            String content = renderer.render(spec.templateKey(), model);
            // 🔒 모든 쓰기는 resolveSafeWritePath 반환값만 사용(계약 §4.1). 위반 시 PathSafetyException.
            Path safeAbs = pathSafetyService.resolveSafeWritePath(targetRoot, relativePath);
            // 🔒 canonicalRoot 전달 — writer가 createDirectories 직후 부모 실경로 재확인(계약 §5.1).
            fileWriter.write(safeAbs, canonicalRoot, content);
            // P12(계약 §16): 쓴 내용의 해시를 결과에 실어 GEN_HIST에 남긴다 —
            // 다음 생성 때 디스크와 대조해 외부 수정(드리프트)을 판정하는 기준. 쓰기 로직은 무변경.
            return GenFile.ok(spec.artifactKey(), relativePath, ContentHash.sha256(content));
        } catch (PathSafetyException e) {
            // 즉시 실패 — 타겟 루트 밖에 아무것도 안 씀.
            log.error("[Gen] 경로안전 위반 — artifact={} relPath={} : {}",
                    spec.artifactKey(), relativePath, e.getMessage());
            return GenFile.fail(spec.artifactKey(), relativePath, "경로안전 위반: " + e.getMessage());
        } catch (RuntimeException e) {
            // 렌더 오류·쓰기 실패 등 — 개별 실패로 수집(다른 아티팩트는 계속).
            log.error("[Gen] 아티팩트 생성 실패 — artifact={} relPath={} : {}",
                    spec.artifactKey(), relativePath, e.getMessage());
            return GenFile.fail(spec.artifactKey(), relativePath,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 계약 §1.2 relativePath 조립: {@code {basePath}/{role}/{stem}/{stem}{정적접미사}.{ext}}.
     * basePath는 프로젝트 메타(정적 설정), role/stem은 컨텍스트 재검증 통과값. 자유문자열 0.
     */
    private String buildRelativePath(ArtifactSpec spec, ForgeProject project,
                                     String role, String stem) {
        String basePath = basePathFor(spec.baseKind(), project);
        String fileName = stem + spec.nameSuffix() + "." + spec.ext();
        // 세그먼트 사이 슬래시. basePath 끝 슬래시는 정규화가 흡수(PathSafetyService가 normalize).
        return basePath + "/" + role + "/" + stem + "/" + fileName;
    }

    private String basePathFor(BaseKind kind, ForgeProject project) {
        return switch (kind) {
            case JSP -> defaultBasePath(project.getJspBasePath(), "jsp");
            case JS -> defaultBasePath(project.getJsBasePath(), "js");
            case CSS -> defaultBasePath(project.getCssBasePath(), "css");
        };
    }

    private String defaultBasePath(String configuredPath, String defaultPath) {
        return (configuredPath == null || configuredPath.isBlank()) ? defaultPath : configuredPath;
    }
}
