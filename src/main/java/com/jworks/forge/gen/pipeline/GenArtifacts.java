package com.jworks.forge.gen.pipeline;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 🔒 아티팩트 매핑 <b>정적 상수</b> (P4-4, 계약 §1.1/§1.2/§4.4).
 *
 * <p>TEMPLATE_KEY·뷰코드·파일 접미사·확장자·basePath 종류를 <b>전부 컴파일 상수/정적 맵</b>으로만
 * 보관한다. DB에서 읽은 문자열(moduleTypeCode 등)을 파일경로로 직접 쓰지 않고 여기 화이트리스트
 * 맵으로 조회해 통과값만 사용한다(문자열 조립·평가 0). 이것이 임의 파일쓰기의 1차 차단이다.
 */
final class GenArtifacts {

    private GenArtifacts() {
    }

    /** basePath 종류(파일이 어떤 프로젝트 메타 basePath 하위에 쓰이는지). */
    enum BaseKind { JSP, JS, CSS }

    /**
     * 아티팩트 1종의 정적 스펙. 전부 상수 — {@code templateKey}(맵조회 화이트리스트),
     * {@code fileSuffix}(정적 접미사, 파일명은 {@code fileSuffix + stem} 아님에 유의:
     * 계약 §1.1은 {@code stem + 정적접미사} 형태다), {@code ext}(확장자 화이트리스트 내), {@code baseKind}.
     *
     * @param artifactKey 로깅/결과용 정적 키
     * @param templateKey 렌더 템플릿 키(맵조회 화이트리스트 값, 계약 §4.4)
     * @param nameSuffix  파일명 정적 접미사(파일명 = {@code stem + nameSuffix + "." + ext})
     * @param ext         확장자(jsp/js/css — PathSafetyService 화이트리스트 내)
     * @param baseKind    basePath 종류
     */
    record ArtifactSpec(
            String artifactKey,
            String templateKey,
            String nameSuffix,
            String ext,
            BaseKind baseKind) {
    }

    /**
     * 🔒 아키타입 → 아티팩트 목록 <b>정적 매핑표</b>(계약 §1.1). MVP는 MGMT_LIST_DETAIL만.
     * 다른 아키타입은 P5(스코프 밖) — 미등록 아키타입은 빈 목록.
     *
     * <p>여기 shell/list 4종(shell + list jsp/js/css)만 정적이며, TABLE_VIEW 3종은 listArea에
     * TABLE_VIEW 인스턴스가 있을 때만 {@link #MODULE_ARTIFACTS}에서 추가한다(계약 §1.1 표 그대로).
     */
    static final Map<String, List<ArtifactSpec>> ARCHETYPE_ARTIFACTS = Map.of(
            "MGMT_LIST_DETAIL", List.of(
                    new ArtifactSpec("shell",
                            "archetype/mgmtListDetail/shell", "", "jsp", BaseKind.JSP),
                    new ArtifactSpec("list",
                            "archetype/mgmtListDetail/list", "List", "jsp", BaseKind.JSP),
                    new ArtifactSpec("listJs",
                            "archetype/mgmtListDetail/listJs", "List", "js", BaseKind.JS),
                    new ArtifactSpec("listCss",
                            "archetype/mgmtListDetail/listCss", "List", "css", BaseKind.CSS)),
            // 원본 MagicIAM의 단순 목록 화면은 관리화면의 목록 3종 구조와 동일하다.
            // 상세 슬롯만 없는 아키타입이므로 검증된 목록 템플릿을 재사용한다.
            "SIMPLE_LIST", List.of(
                    new ArtifactSpec("shell",
                            "archetype/mgmtListDetail/shell", "", "jsp", BaseKind.JSP),
                    new ArtifactSpec("list",
                            "archetype/mgmtListDetail/list", "List", "jsp", BaseKind.JSP),
                    new ArtifactSpec("listJs",
                            "archetype/mgmtListDetail/listJs", "List", "js", BaseKind.JS),
                    new ArtifactSpec("listCss",
                            "archetype/mgmtListDetail/listCss", "List", "css", BaseKind.CSS)),
            // P5-5c: DUAL_LAYOUT 신규 아키타입 add-only(계약 §10.1). iframe 호스트 shell이라 List 세트 없음 —
            // shell/js/css 3종만(전부 nameSuffix "" → {stem}.jsp/.js/.css). MGMT 골든과 완전 독립.
            // P6-2: 듀얼 CSS는 공통추출(commonScreenLayout.css). DUAL은 shell/js 2종만 산출.
            "DUAL_LAYOUT", List.of(
                    new ArtifactSpec("dualShell",
                            "archetype/dualLayout/shell", "", "jsp", BaseKind.JSP),
                    new ArtifactSpec("dualJs",
                            "archetype/dualLayout/shellJs", "", "js", BaseKind.JS)),
            "POPUP", List.of(
                    new ArtifactSpec("popupShell",
                            "archetype/popupForm/shell", "", "jsp", BaseKind.JSP),
                    new ArtifactSpec("popupJs",
                            "archetype/popupForm/shellJs", "", "js", BaseKind.JS),
                    new ArtifactSpec("popupCss",
                            "archetype/popupForm/shellCss", "", "css", BaseKind.CSS)),
            "DASHBOARD", List.of(
                    new ArtifactSpec("dashboardShell",
                            "archetype/dashboard/shell", "", "jsp", BaseKind.JSP)),
            // P13: FREE_CANVAS add-only(계약 §17.4). **항상 3종 고정** — 캔버스 인스턴스 본문은
            // shell 안에 인라인이라 모듈별 파일이 생기지 않는다. 같은 모듈을 N개 놓아도 파일 충돌 0.
            "FREE_CANVAS", List.of(
                    new ArtifactSpec("freeCanvasShell",
                            "archetype/freeCanvas/shell", "", "jsp", BaseKind.JSP),
                    new ArtifactSpec("freeCanvasJs",
                            "archetype/freeCanvas/shellJs", "", "js", BaseKind.JS),
                    new ArtifactSpec("freeCanvasCss",
                            "archetype/freeCanvas/shellCss", "", "css", BaseKind.CSS)));

    /**
     * 🔒 아키타입 상세영역(Detail) 아티팩트 <b>정적 매핑표</b>(계약 §9.2, P5.5a/b). 조건부 산출:
     * detail 슬롯(detailBasic/detailTabs/detailToolbar)이 하나라도 있으면({@code hasDetail})만
     * {@link ScreenGenerator}가 plan에 추가한다. detail 세트(jsp/js/css)는 List 세트와 동형이며,
     * 파일명 접미사 {@code Detail}도 정적 매핑값(문자열 조립 아님). {@code MGMT_LIST_DETAIL}만 등록 —
     * 다른 아키타입은 상세영역 없음(빈 조회).
     */
    static final Map<String, List<ArtifactSpec>> ARCHETYPE_DETAIL_ARTIFACTS = Map.of(
            // P6-2: 상세 CSS는 공통추출(commonScreenLayout.css). Detail은 jsp/js 2종만 산출.
            "MGMT_LIST_DETAIL", List.of(
                    new ArtifactSpec("detail",
                            "archetype/mgmtListDetail/detail", "Detail", "jsp", BaseKind.JSP),
                    new ArtifactSpec("detailJs",
                            "archetype/mgmtListDetail/detailJs", "Detail", "js", BaseKind.JS)));

    /** P9: data/events가 있는 화면에만 생성하는 선언형 설계 메타(JavaScript) 파일. */
    static final ArtifactSpec DESIGN_ARTIFACT = new ArtifactSpec(
            "design", "common/design", "Design", "js", BaseKind.JS);

    /**
     * 🔒 moduleTypeCode → 모듈 아티팩트 3종 <b>정적 화이트리스트 맵</b>(계약 §4.4, V3 시드 미러링).
     * DB에서 읽은 moduleTypeCode를 이 맵으로 조회해 통과값(TABLE_VIEW)만 사용한다.
     * TEMPLATE_KEY는 V3 {@code TABLE_VIEW→module/tableView}를 그대로 반영. 파일명 접미사
     * {@code ListTableView}의 {@code TableView}도 정적 매핑값(문자열 조립 아님).
     */
    static final Map<String, List<ArtifactSpec>> MODULE_ARTIFACTS = Map.of(
            // P6-2: 뷰 CSS는 공통추출(commonScreenLayout.css, 번들). per-screen CSS 미산출 → jsp/js 2종만.
            "TABLE_VIEW", List.of(
                    new ArtifactSpec("listTableView",
                            "module/tableView", "ListTableView", "jsp", BaseKind.JSP),
                    new ArtifactSpec("listTableViewJs",
                            "module/tableViewJs", "ListTableView", "js", BaseKind.JS)),
            // P5-2: CARD_VIEW add-only(계약 §8.2, V4 시드 미러링). nameSuffix=ListCardView(정적 매핑값).
            "CARD_VIEW", List.of(
                    new ArtifactSpec("listCardView",
                            "module/cardView", "ListCardView", "jsp", BaseKind.JSP),
                    new ArtifactSpec("listCardViewJs",
                            "module/cardViewJs", "ListCardView", "js", BaseKind.JS)),
            // P5-3: TREE_VIEW add-only(계약 §8.2, V4 시드 미러링). nameSuffix=ListTreeView(정적 매핑값).
            "TREE_VIEW", List.of(
                    new ArtifactSpec("listTreeView",
                            "module/treeView", "ListTreeView", "jsp", BaseKind.JSP),
                    new ArtifactSpec("listTreeViewJs",
                            "module/treeViewJs", "ListTreeView", "js", BaseKind.JS)),
            // P5-4: FORM_VIEW add-only(계약 §8.2, V4 시드 미러링). nameSuffix=ListFormView(정적 매핑값).
            "FORM_VIEW", List.of(
                    new ArtifactSpec("listFormView",
                            "module/formView", "ListFormView", "jsp", BaseKind.JSP),
                    new ArtifactSpec("listFormViewJs",
                            "module/formViewJs", "ListFormView", "js", BaseKind.JS)),
            "BAR_CHART", widgetSpecs("BarChart", "barChart"),
            "SEMICIRCLE_CHART", widgetSpecs("SemicircleChart", "semicircleChart"),
            "EMPTY_STATE", widgetSpecs("EmptyState", "emptyState"),
            "CHAT_WIDGET", widgetSpecs("ChatWidget", "chatWidget"));

    private static List<ArtifactSpec> widgetSpecs(String suffix, String templateStem) {
        return List.of(
                new ArtifactSpec(templateStem, "module/" + templateStem, suffix, "jsp", BaseKind.JSP),
                new ArtifactSpec(templateStem + "Js", "module/" + templateStem + "Js", suffix, "js", BaseKind.JS),
                new ArtifactSpec(templateStem + "Css", "module/" + templateStem + "Css", suffix, "css", BaseKind.CSS));
    }

    /**
     * 🔒 아키타입별 <b>모듈 아티팩트 기여 슬롯</b> 정적 매핑표(계약 §1.1/§8.1/§17.4).
     *
     * <p>{@link #MODULE_ARTIFACTS}의 모듈 템플릿들은 <b>자기가 놓인 슬롯을 전제로</b> 쓰였다
     * (예: {@code module/tableView.ftl}은 {@code slots["listArea"][0]}을 읽는다). 따라서 그 슬롯에
     * 놓인 인스턴스만 모듈 파일을 만들어야 한다.
     *
     * <p>예전에는 <b>모든 슬롯</b>을 훑어 모듈 파일을 계획했다. 그래서 iframe 패인(DUAL_LAYOUT의
     * {@code leftArea})이나 상세 슬롯에 뷰 모듈을 놓으면, 슬롯 전제가 어긋난 템플릿이 렌더 실패해
     * <b>생성 결과가 조용히 PARTIAL</b>이 됐다. 여기서 기여 슬롯을 못박아 그 표면을 없앤다.
     *
     * <p>미등록 아키타입({@code DUAL_LAYOUT}·{@code POPUP}·{@code FREE_CANVAS})은 모듈 파일을
     * 만들지 않는다 — 각각 iframe 호스트 / shell 인라인 / 캔버스 인라인이라 본문이 shell 안에 있다.
     */
    static final Map<String, Set<String>> MODULE_ARTIFACT_SLOTS = Map.of(
            "MGMT_LIST_DETAIL", Set.of("listArea"),
            "SIMPLE_LIST", Set.of("listArea"),
            "DASHBOARD", Set.of("widgetArea"));

    /** 이 아키타입에서 모듈 아티팩트를 기여하는 슬롯 집합(없으면 빈 집합 = 모듈 파일 0). */
    static Set<String> moduleArtifactSlots(String archetypeCode) {
        return MODULE_ARTIFACT_SLOTS.getOrDefault(archetypeCode, Set.of());
    }

    /**
     * 🔒 listArea 뷰 본문 include 접미사의 <b>파이프라인 측 정본(single source of truth)</b>(계약 §8.1).
     *
     * <p>{@code moduleTypeCode}를 <b>정적 화이트리스트 맵({@link #MODULE_ARTIFACTS}) 조회</b>로 해석해
     * 뷰 본문 JSP include에 쓸 <b>정적 파일 접미사</b>(예: {@code ListTableView})를 돌려준다. 값은
     * {@code MODULE_ARTIFACTS}의 <b>JSP 아티팩트 nameSuffix 그대로</b>이므로 문자열 조립·평가가 아니라
     * 맵 조회 결과값이다. 화이트리스트 밖(=미지원) moduleTypeCode·null·JSP 아티팩트 부재는 모두
     * {@code null}로 수렴한다(forward-compat).
     *
     * <p><b>물리적 단일 소스</b>: {@code list.ftl}의 include 파일명({@code ./{stem}{suffix}.jsp})과
     * 파이프라인이 실제로 산출하는 뷰 JSP 파일명({@code stem + nameSuffix + ".jsp"})은 <b>모두 이
     * {@code MODULE_ARTIFACTS} JSP 아티팩트 nameSuffix</b>에서 나온다. {@code ScreenGenerator}가 렌더
     * 직전 이 메서드로 {@code listAreaViewSuffix}를 파생해 model에 넣으므로, 접미사 소스와 파일명 소스가
     * 같아 <b>드리프트가 원천 불가능</b>하다. 신규 뷰(P5-2~4)는 {@code MODULE_ARTIFACTS}에만 add-only로
     * 추가하면 include·산출 양쪽이 자동 정합된다(별도 병렬 맵 없음).
     *
     * @param moduleTypeCode listArea 인스턴스의 뷰 모듈타입 코드(DB/DEFINITION 원문)
     * @return 정적 파일 접미사(맵 조회값) 또는 {@code null}(미지원)
     */
    static String listAreaViewSuffix(String moduleTypeCode) {
        if (moduleTypeCode == null) {
            return null;
        }
        List<ArtifactSpec> specs = MODULE_ARTIFACTS.get(moduleTypeCode);
        if (specs == null) {
            return null;
        }
        for (ArtifactSpec spec : specs) {
            if (spec.baseKind() == BaseKind.JSP) {
                return spec.nameSuffix();
            }
        }
        return null;
    }
}
