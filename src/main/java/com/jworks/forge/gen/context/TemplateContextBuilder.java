package com.jworks.forge.gen.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.jworks.forge.project.domain.ForgeProject;
import com.jworks.forge.screen.domain.ForgeScreen;

/**
 * 🔒 TemplateContext 구성기 (P4-2, 계약 §2).
 *
 * <p>{@link ForgeScreen}(DEFINITION_JSON 원문) + {@link ForgeProject}(메타)를 FreeMarker 모델
 * {@code Map<String,Object>}로 변환한다. DEFINITION_JSON은 Jackson {@code readTree}로
 * <b>데이터로만</b> 파싱한다(문자열 조립·코드 평가 0). 값 이스케이프는 하지 않는다 —
 * 신뢰경계상(계약 §5) 빌더는 원문을 보존하고, 소비=템플릿이 {@link GenEscaper}로 이스케이프한다.
 *
 * <p>보안 요지:
 * <ul>
 *   <li><b>stem/role/archetype 재검증</b>: 화면 메타 stem/role/archetype을 §1.1·§5.1 화이트리스트
 *       정규식으로 다시 통과시킨 뒤에만 컨텍스트에 넣는다(위반 시 하드 실패). role/archetype은
 *       템플릿에서 HTML 속성·JS 식별자/문자열·조회 키로 쓰이므로 여기가 생성기 신뢰경계의 재검증선이다.
 *       DEFINITION 내부 stem 불일치 시 메타 우선 + 경고(계약 §4.2).</li>
 *   <li><b>forward-compat</b>: 미지원 slotKey/moduleTypeCode 인스턴스는 스킵 + {@code log.warn}
 *       (문서 전체 실패 금지, DEFINITION_JSON §4).</li>
 *   <li><b>맵 조회 전용</b>: slots/instance/props를 데이터 맵으로만 노출한다(§2.2).</li>
 * </ul>
 *
 * <p>이 클래스는 <b>reviewer 🔒 검수 대상</b>이다.
 */
@Component
public class TemplateContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(TemplateContextBuilder.class);

    /**
     * stem 화이트리스트 정규식(스키마_DEFINITION_JSON §1.1). 저장 시점 검증은
     * {@code ForgeScreenRequest}의 {@code @Pattern}(annotation이 컴파일상수 문자열을 요구)이
     * 동일 값으로 강제하며, P4는 파일명·컨텍스트에 쓰기 전 여기서 <b>재검증</b>한다(이중 경계).
     */
    static final String STEM_REGEX = "^[a-z][a-zA-Z0-9]*$";
    private static final Pattern STEM_PATTERN = Pattern.compile(STEM_REGEX);

    /**
     * role 화이트리스트 정규식(§5.1). role은 생성 경로(admin/user)·JS 네임스페이스
     * {@code window.JWorks_JS{Domain}{Role}}·HTML 속성에 원문 삽입되므로, 컨텍스트에 넣기 전
     * 이 형태로 <b>재검증</b>한다(위반 시 하드 실패). 소문자 시작 영숫자만 허용 — 인젝션 문자
     * ({@code "}/{@code '}/{@code <}/{@code >}/{@code /}/공백)를 구조적으로 배제한다.
     * 저장 시점 {@code ForgeScreenService.codeExists("ROLE", ...)}·{@code ForgeScreenRequest.@Pattern}이
     * 1차 방어이고 여기가 생성기 경계의 2차 재검증(신뢰경계 §5).
     */
    static final String ROLE_REGEX = "^[a-z][a-zA-Z0-9]*$";
    private static final Pattern ROLE_PATTERN = Pattern.compile(ROLE_REGEX);

    /**
     * archetype 화이트리스트 정규식(§5.1). archetype은 SLOT_WHITELIST 조회 키이자 HTML 속성에
     * 삽입되므로, model.put 전 이 형태(대문자+언더스코어)로 재검증한다(위반 시 하드 실패).
     * 저장 시점 {@code DefinitionValidator.codeExists("ARCHETYPE", ...)}가 1차 방어이고 여기가 2차.
     */
    static final String ARCHETYPE_REGEX = "^[A-Z][A-Z0-9_]*$";
    private static final Pattern ARCHETYPE_PATTERN = Pattern.compile(ARCHETYPE_REGEX);

    /**
     * packageBase 화이트리스트 정규식(P4-6, 계약 §1.2/§4.3). packageBase는 stub 폴더 경로로
     * <b>점→슬래시 변환되어 파일 쓰기 경로 세그먼트</b>가 되므로(예: {@code com.jworks.forge} →
     * {@code com/jworks/forge}), 경로 조립 전 이 앵커드 정규식으로 <b>하드 재검증</b>한다.
     * 소문자 시작 영숫자 라벨(점 구분)만 허용 — {@code ..}/슬래시/역슬래시/특수문자/대문자를 구조적으로
     * 배제해 경로탈출·임의 폴더 생성을 원천 차단한다. 검증 통과값만 stub 경로 세그먼트로 쓴다.
     * (자바 패키지 라벨은 소문자 관례이며, 여기 통과 후 {@code PathSafetyService}가 2차 방어한다.)
     */
    static final String PACKAGE_BASE_REGEX = "^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*$";
    private static final Pattern PACKAGE_BASE_PATTERN = Pattern.compile(PACKAGE_BASE_REGEX);

    /**
     * 아키타입별 슬롯 화이트리스트(DEFINITION_JSON §2.1). 화이트리스트 밖 slotKey는 스킵+경고.
     * MVP는 MGMT_LIST_DETAIL 기준. 다른 아키타입은 확장 시 추가한다(미등록 아키타입은 빈 화이트리스트).
     */
    private static final Map<String, Set<String>> SLOT_WHITELIST = Map.of(
            "MGMT_LIST_DETAIL", Set.of(
                    "searchArea", "listArea", "listToolbar",
                    "detailBasic", "detailTabs", "detailToolbar"),
            "SIMPLE_LIST", Set.of(
                    "searchArea", "listArea", "listToolbar"),
            "DUAL_LAYOUT", Set.of(
                    "leftArea", "rightArea"),
            "POPUP", Set.of("popupBody"),
            "DASHBOARD", Set.of("widgetArea"),
            // P13: FREE_CANVAS add-only(계약 §17.1). 슬롯 1개(canvasArea).
            "FREE_CANVAS", Set.of("canvasArea"));

    /**
     * moduleTypeCode 화이트리스트(계약: 최소 TABLE_VIEW/SEARCH_FILTER_BAR/TOOLBAR).
     * 화이트리스트 밖 인스턴스는 스킵+경고(§4). P5-2: CARD_VIEW add-only(계약 §8.3 (a)).
     * P5-3: TREE_VIEW add-only(계약 §8.3 (a)). P5-4: FORM_VIEW add-only(계약 §8.3 (a)).
     * P5.5: DETAIL_BASIC·ASSOCIATE_TABS add-only(계약 §9.1 — 상세영역 detailBasic/detailTabs 슬롯).
     * 미등록이면 상세 인스턴스가 스킵되어 hasDetail=false → Detail 산출 0(무해한 미지원, §9.2).
     */
    private static final Set<String> MODULE_TYPE_WHITELIST = Set.of(
            "TABLE_VIEW", "SEARCH_FILTER_BAR", "TOOLBAR", "CARD_VIEW", "TREE_VIEW", "FORM_VIEW",
            "DETAIL_BASIC", "ASSOCIATE_TABS",
            // P5-5c: LAYOUT_FRAME add-only(계약 §10.2 — DUAL_LAYOUT leftArea/rightArea iframe 패인).
            "LAYOUT_FRAME", "POPUP_FORM", "BAR_CHART", "SEMICIRCLE_CHART", "EMPTY_STATE", "CHAT_WIDGET",
            // P13: FREE_CANVAS 원자 컨트롤 add-only(계약 §17.1, V10 시드 미러링).
            "BUTTON", "LABEL", "TEXT_INPUT", "IMAGE",
            // P13-7: 중첩 컨테이너 add-only(계약 §17.8, V11 시드 미러링).
            "PANEL");

    /**
     * §17.8 자식을 담을 수 있는 캔버스 모듈 / 최대 중첩 깊이.
     * {@code DefinitionValidator}(저장 검증)·{@code slotMeta.js}(에디터)와 <b>같은 데이터</b>다 —
     * 변경 시 세 곳을 함께 고친다(계약 §17.8). 여기 값은 산출측 심층방어용 재확인이다.
     */
    private static final Set<String> CANVAS_CONTAINER_TYPES = Set.of("PANEL");
    private static final int MAX_CANVAS_DEPTH = 3;

    private final ObjectMapper objectMapper;

    public TemplateContextBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * FreeMarker 모델을 구성한다(계약 §2.1 키).
     *
     * @param screen  화면 메타 + DEFINITION_JSON 원문
     * @param project 프로젝트 메타(target/package/basePath/runtimeVer)
     * @return 계약 §2.1 키를 채운 최상위 모델 맵
     * @throws TemplateContextException stem 검증 실패·DEFINITION_JSON 파싱 실패 등 하드 실패 시
     */
    public Map<String, Object> build(ForgeScreen screen, ForgeProject project) {
        if (screen == null) {
            throw new TemplateContextException("screen이 null이다");
        }
        if (project == null) {
            throw new TemplateContextException("project가 null이다");
        }

        // 🔒 stem 재검증(하드 실패). 파일명·식별자로 쓰이므로 컨텍스트에 넣기 전 통과 필수.
        String stem = validateStem(screen.getStem());

        // 🔒 role 재검증(하드 실패). JS 네임스페이스·경로·HTML 속성에 삽입되므로 §5.1 화이트리스트 재통과.
        String role = validateRole(screen.getRoleCode());

        // 🔒 archetype 재검증(하드 실패). SLOT_WHITELIST 조회 키·HTML 속성 삽입 전 형태 검증.
        String archetype = validateArchetype(screen.getArchetypeCode());

        // 🔒 packageBase 재검증(하드 실패, P4-6). stub 폴더 경로(점→슬래시)로 조립되므로
        //    경로 세그먼트로 넣기 전 §1.2 화이트리스트 재통과 필수(경로탈출·특수문자 차단).
        String packageBase = validatePackageBase(project.getPackageBase());

        // DEFINITION_JSON 파싱(데이터로만) + 내부 stem 불일치 정책(메타 우선 + 경고).
        JsonNode root = parseDefinition(screen.getDefinitionJson());
        reconcileStem(stem, root);

        // slots 변환(forward-compat 스킵+경고).
        Map<String, List<Map<String, Object>>> slots = buildSlots(archetype, root);

        Map<String, Object> model = new HashMap<>();
        model.put("stem", stem);
        model.put("role", role);
        model.put("archetype", archetype);
        model.put("packageBase", packageBase);
        model.put("jspBasePath", defaultBasePath(project.getJspBasePath(), "jsp"));
        model.put("jsBasePath", defaultBasePath(project.getJsBasePath(), "js"));
        model.put("cssBasePath", defaultBasePath(project.getCssBasePath(), "css"));
        model.put("runtimeVer", project.getRuntimeVer());
        model.put("slots", slots);
        // P13(§17.2): FREE_CANVAS 시트 크기. 숫자 노드만 통과시키고, 없으면 키를 넣지 않는다
        //             (템플릿이 기본값 1280×800으로 폴백 → 기존 아키타입 산출에는 영향 0).
        Map<String, Object> canvas = buildCanvas(root);
        if (!canvas.isEmpty()) {
            model.put("canvas", canvas);
        }
        // P13-7(§17.8): 캔버스 중첩 트리. seq(깊이우선 전위순회)를 **여기서 한 번만** 부여해
        //               shell/shellCss 두 템플릿이 같은 번호를 읽게 한다(번호 드리프트 원천 차단).
        List<Map<String, Object>> canvasItems = slots.get("canvasArea");
        if (canvasItems != null) {
            model.put("canvasTree", buildCanvasTree(canvasItems));
            model.put("canvasSoleType", buildCanvasSoleType(canvasItems));
        }
        // ※ listArea 뷰 본문 include 접미사(listAreaViewSuffix, 계약 §8.1)는 여기서 파생하지 않는다.
        //   include 파일명은 파이프라인 산출 아티팩트 파일명과 반드시 일치해야 하므로, 그 정본인
        //   GenArtifacts.MODULE_ARTIFACTS를 아는 ScreenGenerator(pipeline 패키지)가 렌더 직전 model에
        //   put한다(물리적 단일 소스). context→pipeline 패키지 순환을 피하려고 여기서 조회하지 않는다.
        return model;
    }

    /**
     * §17.2 {@code canvas} 노드 → 모델 맵. <b>숫자 노드만</b> 통과시킨다(문자열은 아예 담지 않는다).
     * 범위 검증은 저장 시점({@code DefinitionValidator})과 산출 시점(템플릿 게이트)에서 각각 하므로
     * 여기서는 타입만 좁힌다 — 값이 없거나 숫자가 아니면 키를 넣지 않아 템플릿이 기본값으로 폴백한다.
     */
    private Map<String, Object> buildCanvas(JsonNode root) {
        Map<String, Object> canvas = new HashMap<>();
        JsonNode node = root == null ? null : root.get("canvas");
        if (node == null || !node.isObject()) {
            return canvas;
        }
        putIfNumber(canvas, node, "widthPx");
        putIfNumber(canvas, node, "heightPx");
        return canvas;
    }

    /**
     * §17.13 캔버스에 <b>정확히 한 개만</b> 놓인 모듈 타입 집합(moduleTypeCode → true).
     *
     * <p><b>왜 필요한가</b>: MagicIAM 공통 CSS는 뷰를 <b>ID</b>로 잡는다 —
     * {@code #table-view} 118개, {@code #basic-info} 92개, {@code #tree-view} 86개,
     * {@code #card-view} 73개, {@code #form-view} 24개, {@code #associate-info} 18개(합 411).
     * 클래스({@code .table-view})로 잡는 규칙은 <b>0개</b>다. 슬롯 아키타입의 모듈 템플릿은
     * {@code id}와 {@code class}를 함께 찍지만, FREE_CANVAS 파셜은 같은 뷰를 여러 개 놓을 수 있어
     * ID 중복을 피하려 {@code class}만 찍었다 — 그 결과 <b>모든 자유배치 화면이 무스타일</b>이었다.
     *
     * <p>그래서 <b>그 타입이 캔버스에 하나뿐일 때만</b> ID를 함께 찍는다. 중복이 원천적으로 불가능하고
     * 판정이 결정적이다. 같은 뷰를 2개 이상 놓은 캔버스는 종전대로 클래스만 나간다(알려진 한계 —
     * ID는 문서상 유일해야 하므로 여기서 더 나갈 수 없다).
     *
     * @param items canvasArea 인스턴스 목록(평면 — 중첩은 layoutParentId 로 표현된다)
     * @return 등장 횟수가 1인 moduleTypeCode 만 담긴 맵. 템플릿은 {@code (canvasSoleType["X"])!false} 로 읽는다
     */
    private Map<String, Boolean> buildCanvasSoleType(List<Map<String, Object>> items) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map<String, Object> inst : items) {
            if (inst != null && inst.get("moduleTypeCode") instanceof String code && !code.isEmpty()) {
                counts.merge(code, 1, Integer::sum);
            }
        }
        Map<String, Boolean> sole = new LinkedHashMap<>();
        counts.forEach((code, n) -> {
            if (n == 1) {
                sole.put(code, Boolean.TRUE);
            }
        });
        return Collections.unmodifiableMap(sole);
    }

    /**
     * §17.8 캔버스 평면 배열 → <b>중첩 트리</b>(P13-7). 각 노드에 {@code seq}(깊이우선 전위순회, 1부터)를
     * 부여한다 — 이 번호가 곧 생성 CSS 셀렉터({@code .frg-fc-N})이자 JSP 클래스다. shell/shellCss가
     * <b>같은 트리</b>를 읽으므로 마크업과 CSS의 번호가 갈라질 수 없다.
     *
     * <p>부모 참조가 깨진 인스턴스(미존재·컨테이너 아님·자기참조·순환·깊이 초과)는 <b>루트로 수렴</b>한다.
     * 저장 시점 검증({@code DefinitionValidator})이 이미 거르지만, 손상된 데이터가 들어와도 화면이
     * 통째로 사라지지 않게 하는 안전측 폴백이다.
     *
     * @param items canvasArea 인스턴스 목록(문서 순서)
     * @return 루트 노드 목록. 각 노드는 인스턴스 키 + {@code seq} + {@code children}
     */
    private List<Map<String, Object>> buildCanvasTree(List<Map<String, Object>> items) {
        Map<String, Map<String, Object>> nodeById = new LinkedHashMap<>();
        Map<String, String> parentOf = new HashMap<>();
        Map<String, String> typeOf = new HashMap<>();

        for (Map<String, Object> inst : items) {
            Object idObj = inst.get("instanceId");
            if (!(idObj instanceof String id) || id.isEmpty()) {
                continue;
            }
            Map<String, Object> node = new LinkedHashMap<>(inst);
            node.put("children", new ArrayList<Map<String, Object>>());
            // §17.10 컨테이너 표시 — 템플릿이 래퍼에 frg-fc-container 를 붙여 스태킹 컨텍스트를
            // 항상 열게 한다(자식 z 가 부모 밖으로 새지 않게). 구조값이라 화이트리스트 기준이다.
            node.put("isCanvasContainer", inst.get("moduleTypeCode") instanceof String mt
                    && CANVAS_CONTAINER_TYPES.contains(mt));
            nodeById.put(id, node);
            typeOf.put(id, inst.get("moduleTypeCode") instanceof String c ? c : null);
            if (inst.get("props") instanceof Map<?, ?> props
                    && props.get("layoutParentId") instanceof String parentId
                    && !parentId.isEmpty()) {
                parentOf.put(id, parentId);
            }
        }

        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : nodeById.entrySet()) {
            String parentId = effectiveCanvasParent(entry.getKey(), parentOf, typeOf);
            Map<String, Object> parent = parentId == null ? null : nodeById.get(parentId);
            if (parent == null) {
                roots.add(entry.getValue());
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) parent.get("children");
                children.add(entry.getValue());
            }
        }
        assignCanvasSeq(roots, new int[] { 0 });
        return roots;
    }

    /** 부모 사슬을 검사해 유효한 부모 id를 돌려준다. 하나라도 어긋나면 null(=루트로 수렴). */
    private String effectiveCanvasParent(String id, Map<String, String> parentOf,
                                         Map<String, String> typeOf) {
        String parentId = parentOf.get(id);
        if (parentId == null || parentId.equals(id)) {
            return null;
        }
        if (!CANVAS_CONTAINER_TYPES.contains(typeOf.get(parentId))) {
            return null; // 미존재(typeOf 없음) 또는 컨테이너 아님
        }
        Set<String> seen = new HashSet<>();
        seen.add(id);
        String cursor = parentId;
        int depth = 1;
        while (cursor != null) {
            if (!seen.add(cursor)) {
                return null; // 순환
            }
            depth++;
            if (depth > MAX_CANVAS_DEPTH) {
                return null; // 깊이 초과
            }
            cursor = parentOf.get(cursor);
        }
        return parentId;
    }

    private void assignCanvasSeq(List<Map<String, Object>> nodes, int[] counter) {
        for (Map<String, Object> node : nodes) {
            node.put("seq", ++counter[0]);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
            assignCanvasSeq(children, counter);
        }
    }

    private void putIfNumber(Map<String, Object> target, JsonNode source, String field) {
        JsonNode value = source.get(field);
        if (value != null && value.isNumber()) {
            target.put(field, value.asInt());
        }
    }

    private String defaultBasePath(String configuredPath, String defaultPath) {
        return (configuredPath == null || configuredPath.isBlank()) ? defaultPath : configuredPath;
    }

    /** §1.1 정규식으로 stem을 재검증한다. 위반 시 하드 실패(경로/파일명 오염 차단). */
    private String validateStem(String stem) {
        if (stem == null || !STEM_PATTERN.matcher(stem).matches()) {
            // 위반값은 로그에 원문 노출하지 않고 존재/길이만(로그 인젝션·정보노출 방지).
            throw new TemplateContextException(
                    "stem이 화이트리스트(" + STEM_REGEX + ") 위반 — 컨텍스트 구성 거부 (len="
                            + (stem == null ? -1 : stem.length()) + ")");
        }
        return stem;
    }

    /** §5.1 정규식으로 role을 재검증한다. 위반 시 하드 실패(JS 네임스페이스·경로·속성 오염 차단). */
    private String validateRole(String role) {
        if (role == null || !ROLE_PATTERN.matcher(role).matches()) {
            // 위반값은 로그에 원문 노출하지 않고 존재/길이만(로그 인젝션·정보노출 방지).
            throw new TemplateContextException(
                    "role이 화이트리스트(" + ROLE_REGEX + ") 위반 — 컨텍스트 구성 거부 (len="
                            + (role == null ? -1 : role.length()) + ")");
        }
        return role;
    }

    /** §5.1 정규식으로 archetype을 재검증한다. 위반 시 하드 실패(속성 오염·조회 키 오염 차단). */
    private String validateArchetype(String archetype) {
        if (archetype == null || !ARCHETYPE_PATTERN.matcher(archetype).matches()) {
            // 위반값은 로그에 원문 노출하지 않고 존재/길이만(로그 인젝션·정보노출 방지).
            throw new TemplateContextException(
                    "archetype이 화이트리스트(" + ARCHETYPE_REGEX + ") 위반 — 컨텍스트 구성 거부 (len="
                            + (archetype == null ? -1 : archetype.length()) + ")");
        }
        return archetype;
    }

    /**
     * §1.2 정규식으로 packageBase를 재검증한다(P4-6). 위반 시 하드 실패(stub 폴더 경로 오염·경로탈출 차단).
     * 통과값만 {@code com/jworks/forge} 형태 폴더 경로로 변환되어 파일 쓰기 세그먼트가 된다.
     */
    /** 값이 비어 있는지(미설정) — 형식 위반과 구분해 안내 문구를 다르게 준다. */
    private boolean isBlankPackageBase(String packageBase) {
        return packageBase == null || packageBase.isBlank();
    }

    private String validatePackageBase(String packageBase) {
        // 미설정과 형식 위반을 구분해 안내한다 — 둘 다 여기서 막히지만 사용자가 할 일이 다르다.
        // (어느 경우든 위반값 원문은 노출하지 않는다 — 로그 인젝션·정보노출 방지.)
        if (isBlankPackageBase(packageBase)) {
            throw new TemplateContextException(
                    "프로젝트의 '패키지 베이스'가 비어 있습니다. 생성될 Controller/Mapper 의 패키지 경로라 필수입니다"
                            + " — 프로젝트 화면에서 값을 넣어 주세요 (예: com.acme.app).");
        }
        if (!PACKAGE_BASE_PATTERN.matcher(packageBase).matches()) {
            throw new TemplateContextException(
                    "프로젝트의 '패키지 베이스' 형식이 올바르지 않습니다 — 소문자 자바 패키지여야 합니다"
                            + " (예: com.acme.app). 프로젝트 화면에서 고쳐 주세요. (len="
                            + packageBase.length() + ")");
        }
        return packageBase;
    }

    /** DEFINITION_JSON을 트리로 파싱한다. null/공백은 빈 객체로 취급(slots 없음). */
    private JsonNode parseDefinition(String definitionJson) {
        if (definitionJson == null || definitionJson.isBlank()) {
            log.warn("[TemplateContext] DEFINITION_JSON이 비어 있음 — slots 없이 구성");
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(definitionJson);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new TemplateContextException("DEFINITION_JSON 파싱 실패", e);
        }
    }

    /**
     * DEFINITION_JSON 내부 stem과 메타 stem 불일치 시 <b>메타 우선 + 경고</b>(계약 §4.2).
     * 내부 stem이 없거나 같으면 무해.
     */
    private void reconcileStem(String metaStem, JsonNode root) {
        JsonNode inner = root.get("stem");
        if (inner != null && inner.isTextual() && !metaStem.equals(inner.asText())) {
            log.warn("[TemplateContext] stem 불일치 — 메타(screen.STEM) 우선 사용, "
                    + "DEFINITION_JSON 내부 stem 무시 (meta={}, definitionLen={})",
                    metaStem, inner.asText().length());
        }
    }

    /**
     * {@code slots} 오브젝트를 슬롯키→인스턴스배열 맵으로 변환한다(배열 순서 보존).
     * 미지원 slotKey / 미지원 moduleTypeCode 인스턴스는 스킵 + {@code log.warn}(§4).
     */
    private Map<String, List<Map<String, Object>>> buildSlots(String archetype, JsonNode root) {
        // LinkedHashMap: 슬롯키 등장 순서 보존(템플릿은 리터럴 키로만 조회하나 결정적 출력 위해).
        Map<String, List<Map<String, Object>>> slots = new LinkedHashMap<>();

        JsonNode slotsNode = root.get("slots");
        if (slotsNode == null || !slotsNode.isObject()) {
            return slots;
        }

        Set<String> allowedSlots = SLOT_WHITELIST.getOrDefault(archetype, Set.of());

        Iterator<Map.Entry<String, JsonNode>> it = slotsNode.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            String slotKey = entry.getKey();
            JsonNode arr = entry.getValue();

            // 🔒 forward-compat: 미지원 slotKey는 스킵 + 경고(문서 전체 실패 금지).
            if (!allowedSlots.contains(slotKey)) {
                log.warn("[TemplateContext] unsupported slotKey '{}' for archetype '{}', skipped",
                        slotKey, archetype);
                continue;
            }
            if (arr == null || !arr.isArray()) {
                continue; // 빈 배열/키 생략은 동치(§1.2) — 빈 슬롯으로 취급.
            }

            List<Map<String, Object>> instances = buildInstances((ArrayNode) arr);
            slots.put(slotKey, instances);
        }
        return slots;
    }

    /** 슬롯 배열의 인스턴스들을 변환한다(순서 보존). 미지원 moduleTypeCode는 스킵+경고. */
    private List<Map<String, Object>> buildInstances(ArrayNode arr) {
        List<Map<String, Object>> instances = new ArrayList<>(arr.size());
        for (JsonNode inst : arr) {
            if (inst == null || !inst.isObject()) {
                continue;
            }
            String moduleTypeCode = textOrNull(inst.get("moduleTypeCode"));
            String instanceId = textOrNull(inst.get("instanceId"));

            // 🔒 forward-compat: 미지원 moduleTypeCode 인스턴스는 스킵 + 경고.
            if (moduleTypeCode == null || !MODULE_TYPE_WHITELIST.contains(moduleTypeCode)) {
                log.warn("[TemplateContext] unknown moduleTypeCode '{}' (instanceId={}), skipped",
                        moduleTypeCode, instanceId);
                continue;
            }

            Map<String, Object> instMap = new HashMap<>();
            instMap.put("instanceId", instanceId);
            instMap.put("moduleTypeCode", moduleTypeCode);
            // props는 원문 보존 — 값 이스케이프는 템플릿이 GenEscaper로(신뢰경계 §5).
            instMap.put("props", toPlainValue(inst.get("props")));
            // P9: props와 분리된 선언형 데이터/이벤트 메타. 검증기가 구조를 보장하고 템플릿은
            // jsString()으로만 소비한다. 값이 없으면 키를 넣지 않아 기존 산출물을 바꾸지 않는다.
            if (inst.get("data") != null && inst.get("data").isObject()) {
                instMap.put("data", toPlainValue(inst.get("data")));
            }
            if (inst.get("events") != null && inst.get("events").isArray()) {
                instMap.put("events", toPlainValue(inst.get("events")));
            }
            instances.add(instMap);
        }
        return instances;
    }

    /**
     * JsonNode를 순수 Java 데이터(Map/List/String/Number/Boolean/null)로 변환한다.
     * <b>코드 평가·문자열 조립 없이 데이터로만</b> 노출한다(§2.2). 값은 원문 보존.
     */
    private Object toPlainValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isObject()) {
            // LinkedHashMap: 키 순서 보존(결정적 출력).
            Map<String, Object> map = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                map.put(e.getKey(), toPlainValue(e.getValue()));
            }
            return map;
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>(node.size());
            for (JsonNode child : node) {
                list.add(toPlainValue(child));
            }
            return list;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        // 그 외(문자열 포함): 텍스트 원문 그대로.
        return node.asText();
    }

    private static String textOrNull(JsonNode node) {
        return (node != null && node.isTextual()) ? node.asText() : null;
    }
}
