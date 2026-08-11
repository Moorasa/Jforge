package com.jworks.forge.screen.validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 아키타입별 슬롯 화이트리스트(스키마_DEFINITION_JSON.md §2.1~2.3을 서버측 상수로 인코딩).
 *
 * <p><b>소스오브트루스는 문서 표</b>이며, 이 상수는 클라이언트 팔레트의
 * {@code static/js/admin/studio/palette.js}의 {@code SLOT_WHITELIST}와 <b>동일 데이터</b>다.
 * 두 구현이 갈라지면 안 되므로 문서의 §2.1~2.5 표를 그대로 옮겼다(변경 시 문서·palette.js·이 클래스 3곳을 함께 수정).
 *
 * <p>슬롯별로 (1) 허용 카테고리 집합(§2.4), (2) cardinality(§2.5)를 규정한다.
 * cardinality는 저장 시점 하드 검증에 쓰인다 — 특히 {@code MIN_ONE_MAX_ONE}({@code 1..1}) 위반(0개 또는 2개 이상)은
 * 확정 정책상 하드 실패(400)다.
 */
public final class ArchetypeSlots {

    private ArchetypeSlots() { }

    /** cardinality 종류(§2.5). */
    public enum Cardinality {
        /** 0..1 — 없거나 1개(배열 길이 &le; 1). */
        ZERO_OR_ONE,
        /** 1..1 — 정확히 1개(필수·단일). 0개/2개+ 는 하드 실패. */
        MIN_ONE_MAX_ONE,
        /** 0..N — 여러 개 허용. */
        ZERO_OR_MANY
    }

    /** 슬롯 1개의 규칙: 허용 카테고리 + cardinality. */
    public static final class SlotRule {
        private final Set<String> categories;
        private final Cardinality cardinality;

        SlotRule(Cardinality cardinality, String... categories) {
            this.cardinality = cardinality;
            this.categories = Set.of(categories);
        }

        public Set<String> getCategories() { return categories; }
        public Cardinality getCardinality() { return cardinality; }
    }

    // slotKey → SlotRule (LinkedHashMap: 문서 표 순서 보존).
    private static final Map<String, Map<String, SlotRule>> WHITELIST;

    static {
        Map<String, Map<String, SlotRule>> wl = new LinkedHashMap<>();

        // §2.1 MGMT_LIST_DETAIL — 관리화면(목록+상세)
        Map<String, SlotRule> mgmt = new LinkedHashMap<>();
        mgmt.put("searchArea",    new SlotRule(Cardinality.ZERO_OR_ONE,     "FILTER"));
        mgmt.put("listArea",      new SlotRule(Cardinality.MIN_ONE_MAX_ONE, "VIEW"));            // 1..1 필수·단일
        mgmt.put("listToolbar",   new SlotRule(Cardinality.ZERO_OR_ONE,     "ACTION"));
        mgmt.put("detailBasic",   new SlotRule(Cardinality.ZERO_OR_ONE,     "DETAIL", "VIEW"));
        mgmt.put("detailTabs",    new SlotRule(Cardinality.ZERO_OR_MANY,    "DETAIL", "VIEW"));  // 0..N
        mgmt.put("detailToolbar", new SlotRule(Cardinality.ZERO_OR_ONE,     "ACTION"));
        wl.put("MGMT_LIST_DETAIL", Collections.unmodifiableMap(mgmt));

        // §2.2 SIMPLE_LIST — 단순 목록
        Map<String, SlotRule> simple = new LinkedHashMap<>();
        simple.put("searchArea",  new SlotRule(Cardinality.ZERO_OR_ONE,     "FILTER"));
        simple.put("listArea",    new SlotRule(Cardinality.MIN_ONE_MAX_ONE, "VIEW"));            // 1..1
        simple.put("listToolbar", new SlotRule(Cardinality.ZERO_OR_ONE,     "ACTION"));
        wl.put("SIMPLE_LIST", Collections.unmodifiableMap(simple));

        // §2.3 DUAL_LAYOUT — 좌우 2단. 두 슬롯은 **iframe 패인**이라 프레임만 놓을 수 있다(§10.4).
        // 예전엔 VIEW/FILTER/DETAIL 을 허용해 뷰 모듈 배치가 막히지 않았고, 그러면 슬롯 전제가
        // 어긋난 모듈 템플릿이 렌더 실패해 생성이 조용히 PARTIAL 이 됐다(빈 iframe 만 남음).
        Map<String, SlotRule> dual = new LinkedHashMap<>();
        dual.put("leftArea",  new SlotRule(Cardinality.ZERO_OR_MANY, "FRAME")); // 0..N
        dual.put("rightArea", new SlotRule(Cardinality.ZERO_OR_MANY, "FRAME")); // 0..N
        wl.put("DUAL_LAYOUT", Collections.unmodifiableMap(dual));

        // POPUP — 원본 JWorks overlay-popup 기반 추가/수정 팝업.
        Map<String, SlotRule> popup = new LinkedHashMap<>();
        popup.put("popupBody", new SlotRule(Cardinality.MIN_ONE_MAX_ONE, "VIEW"));
        wl.put("POPUP", Collections.unmodifiableMap(popup));

        Map<String, SlotRule> dashboard = new LinkedHashMap<>();
        dashboard.put("widgetArea", new SlotRule(Cardinality.ZERO_OR_MANY, "WIDGET"));
        wl.put("DASHBOARD", Collections.unmodifiableMap(dashboard));

        // §17.1 FREE_CANVAS — 델파이형 자유 배치(P13). 슬롯 1개(canvasArea) 0..N, 전 카테고리 허용.
        // 배치 제약을 슬롯이 아니라 좌표(props.layoutXPx…)가 대신하므로 카테고리 필터가 없다.
        Map<String, SlotRule> freeCanvas = new LinkedHashMap<>();
        // LAYOUT(§17.8 PANEL 컨테이너)은 canvasArea 에서만 허용 — 다른 아키타입 팔레트에는 없다.
        freeCanvas.put("canvasArea", new SlotRule(Cardinality.ZERO_OR_MANY,
                "VIEW", "FILTER", "ACTION", "DETAIL", "WIDGET", "CONTROL", "LAYOUT"));
        wl.put("FREE_CANVAS", Collections.unmodifiableMap(freeCanvas));

        WHITELIST = Collections.unmodifiableMap(wl);
    }

    /** 해당 아키타입이 화이트리스트에 존재하는지. */
    public static boolean isKnownArchetype(String archetypeCode) {
        return WHITELIST.containsKey(archetypeCode);
    }

    /** 아키타입의 슬롯 규칙 맵(slotKey → SlotRule). 미지원 아키타입이면 null. */
    public static Map<String, SlotRule> slotsOf(String archetypeCode) {
        return WHITELIST.get(archetypeCode);
    }
}
