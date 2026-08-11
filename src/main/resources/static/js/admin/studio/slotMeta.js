/* ===============================================================================================
Name : slotMeta.js
Description : 아키타입별 슬롯 메타데이터 단일 소스 (P7-2).
             스튜디오 부모 문서(palette.js)와 캔버스 iframe(previewRenderer.js)이 함께 로드한다
             — 슬롯 화이트리스트/라벨/표시순서가 두 문서에서 어긋나지 않도록 여기 한 곳에만 둔다.
             (스키마_DEFINITION_JSON.md §2 를 JS 상수로 인코딩. 서버 최종검증 P3-5b 를 대체하지 않는
              UX 1차 검증용 — §5 신뢰경계.)

구조:
  WHITELIST[archetype][slotKey] = { cats: 허용 카테고리 배열, multi: 다중 배치 여부(§2.5) }
  LABELS[slotKey] = 한글 표시 라벨
  ORDER = 캔버스 표시 순서(위→아래 조립 순서)
=============================================================================================== */
window.JWorks_JSForgeAdminStudioSlotMeta = window.JWorks_JSForgeAdminStudioSlotMeta || {};
(function (meta) {
    "use strict";
    if (meta.__defined) { return; }
    meta.__defined = true;

    meta.WHITELIST = {
        MGMT_LIST_DETAIL: {
            searchArea:    { cats: ["FILTER"],          multi: false }, // 0..1
            listArea:      { cats: ["VIEW"],            multi: false }, // 1..1 (필수·단일)
            listToolbar:   { cats: ["ACTION"],          multi: false }, // 0..1
            detailBasic:   { cats: ["DETAIL", "VIEW"],  multi: false }, // 0..1
            detailTabs:    { cats: ["DETAIL", "VIEW"],  multi: true  }, // 0..N
            detailToolbar: { cats: ["ACTION"],          multi: false }  // 0..1
        },
        SIMPLE_LIST: {
            searchArea:  { cats: ["FILTER"], multi: false }, // 0..1
            listArea:    { cats: ["VIEW"],   multi: false }, // 1..1
            listToolbar: { cats: ["ACTION"], multi: false }  // 0..1
        },
        // 두 슬롯은 iframe 패인이라 프레임만 놓을 수 있다(§10.4) — 뷰 모듈을 놓으면
        // 생성 시 슬롯 전제가 어긋나 조용히 PARTIAL 이 되던 것을 팔레트 단계에서 막는다.
        DUAL_LAYOUT: {
            leftArea:  { cats: ["FRAME"], multi: true }, // 0..N
            rightArea: { cats: ["FRAME"], multi: true }  // 0..N
        },
        POPUP: {
            popupBody: { cats: ["VIEW"], multi: false } // 1..1
        },
        DASHBOARD: {
            widgetArea: { cats: ["WIDGET"], multi: true }
        },
        // §17.1 FREE_CANVAS(P13) — 자유 배치. 슬롯 1개·전 카테고리·0..N.
        // 배치 제약은 좌표(props.layoutXPx…)가 대신하므로 카테고리 필터가 없다.
        FREE_CANVAS: {
            canvasArea: {
                cats: ["VIEW", "FILTER", "ACTION", "DETAIL", "WIDGET", "CONTROL", "LAYOUT"],
                multi: true
            }
        }
    };

    /** §17.8 중첩 컨테이너: 자식을 담을 수 있는 모듈(현재 PANEL 하나). */
    meta.CONTAINER_TYPES = { PANEL: 1 };

    meta.isContainer = function (moduleTypeCode) {
        return !!meta.CONTAINER_TYPES[moduleTypeCode];
    };

    /** §17.8 중첩 최대 깊이(루트=1). 초과분은 루트로 수렴(안전측). */
    meta.MAX_CANVAS_DEPTH = 3;

    /** 인스턴스의 부모 instanceId(없으면 null). */
    meta.parentIdOf = function (inst) {
        var raw = inst && inst.props ? inst.props.layoutParentId : null;
        return (raw == null || String(raw) === "") ? null : String(raw);
    };

    /**
     * 평면 캔버스 배열 → 중첩 트리. 부모가 없거나·컨테이너가 아니거나·순환이거나·깊이를 넘으면
     * 그 인스턴스는 **루트로 수렴**한다(서버 산출측과 동일 규칙 — 손상된 데이터가 화면을 못 지운다).
     * 각 노드에 seq(깊이우선 전위순회 1부터)를 부여한다 — 생성 CSS 셀렉터와 같은 번호다.
     */
    meta.buildCanvasTree = function (items) {
        var list = Array.isArray(items) ? items : [];
        var byId = {};
        list.forEach(function (inst) {
            if (inst && inst.instanceId != null) { byId[String(inst.instanceId)] = inst; }
        });

        function effectiveParent(inst) {
            var seen = {};
            var current = inst;
            var depth = 0;
            var parentId = meta.parentIdOf(current);
            while (parentId) {
                if (seen[parentId]) { return null; }          // 순환
                seen[parentId] = 1;
                var parent = byId[parentId];
                if (!parent) { return null; }                  // 미존재
                if (!meta.isContainer(parent.moduleTypeCode)) { return null; } // 컨테이너 아님
                if (parent === inst) { return null; }           // 자기 자신
                depth++;
                if (depth >= meta.MAX_CANVAS_DEPTH) { return null; } // 깊이 초과
                current = parent;
                parentId = meta.parentIdOf(current);
            }
            return meta.parentIdOf(inst);
        }

        var nodes = {};
        var roots = [];
        list.forEach(function (inst) {
            if (!inst || typeof inst !== "object" || inst.instanceId == null) { return; }
            nodes[String(inst.instanceId)] = { inst: inst, children: [] };
        });
        list.forEach(function (inst) {
            if (!inst || inst.instanceId == null) { return; }
            var node = nodes[String(inst.instanceId)];
            var parentId = effectiveParent(inst);
            var parentNode = parentId ? nodes[parentId] : null;
            if (parentNode && parentNode !== node) { parentNode.children.push(node); }
            else { roots.push(node); }
        });

        var seq = 0;
        (function assign(list2) {
            list2.forEach(function (node) {
                seq++;
                node.seq = seq;
                assign(node.children);
            });
        })(roots);
        return roots;
    };

    /** 자유 배치 아키타입인가(캔버스/팔레트/속성패널이 좌표 모드로 전환하는 단일 판정). */
    meta.isFreeCanvas = function (archetype) {
        return String(archetype) === "FREE_CANVAS";
    };

    /** §17.2 좌표 예약 키와 유효범위(서버 게이트와 동일 — 클라이언트는 1차 클램프용). */
    meta.CANVAS_KEYS = {
        layoutXPx: { min: 0,  max: 4000 },
        layoutYPx: { min: 0,  max: 8000 },
        layoutWPx: { min: 20, max: 4000 },
        layoutHPx: { min: 20, max: 4000 },
        layoutZ:   { min: 0,  max: 999 }
    };

    /** §17.2 시트 크기 기본값/범위. */
    meta.CANVAS_SHEET = {
        defaultWidthPx: 1280, defaultHeightPx: 800,
        minWidthPx: 320, maxWidthPx: 4000,
        minHeightPx: 240, maxHeightPx: 8000
    };

    /**
     * 캔버스에 처음 놓을 때의 기본 크기(px). 델파이가 컴포넌트마다 기본 크기를 갖는 것과 같다 —
     * 사용자가 크기를 정하기 전에도 "덩어리"로 보여야 하고, §17.2 4키를 항상 채워 두어야
     * 산출 CSS 가 조건부 폴백으로 새지 않는다.
     */
    meta.DEFAULT_ITEM_SIZE = {
        PANEL:             { w: 420, h: 300 },
        BUTTON:            { w: 120, h: 36 },
        LABEL:             { w: 160, h: 28 },
        TEXT_INPUT:        { w: 240, h: 58 },
        IMAGE:             { w: 200, h: 150 },
        TOOLBAR:           { w: 360, h: 44 },
        SEARCH_FILTER_BAR: { w: 720, h: 56 },
        TABLE_VIEW:        { w: 720, h: 360 },
        FORM_VIEW:         { w: 480, h: 320 },
        BAR_CHART:         { w: 320, h: 180 },
        SEMICIRCLE_CHART:  { w: 320, h: 200 },
        EMPTY_STATE:       { w: 320, h: 200 },
        CHAT_WIDGET:       { w: 360, h: 420 }
    };

    meta.defaultItemSize = function (moduleTypeCode) {
        return meta.DEFAULT_ITEM_SIZE[moduleTypeCode] || { w: 320, h: 200 };
    };

    /** 캔버스 이동/리사이즈 스냅 간격(px). 델파이의 그리드 스냅에 해당하는 편집 보조값. */
    meta.CANVAS_SNAP = 8;

    /** 좌표 값 1건 클램프(숫자 아니면 null). */
    meta.clampCanvas = function (key, value) {
        var rule = meta.CANVAS_KEYS[key];
        var n = Number(value);
        if (!rule || !isFinite(n)) { return null; }
        return Math.min(rule.max, Math.max(rule.min, Math.round(n)));
    };

    /**
     * §13 크기 props(layoutWidthPct/layoutHeightPx)가 **실제로 per-screen CSS 로 산출되는** 지점.
     * listCss.ftl 말미의 정적 셀렉터 맵(searchArea/listToolbar/listArea, 각 [0] 인스턴스만)을 그대로
     * 인코딩한 것 — 캔버스가 "조절은 되는데 파일에는 안 나가는" 핸들을 붙이지 않도록 하는 단일 소스다.
     * 여기 없는 슬롯에서 크기를 바꾸면 프리뷰만 변하고 산출은 0바이트이므로 핸들을 노출하지 않는다.
     * (템플릿이 확장되면 여기도 함께 넓힌다 — 산출 지점과 에디터 노출이 갈라지지 않게.)
     */
    meta.SIZE_CSS_EMITTED = {
        MGMT_LIST_DETAIL: { searchArea: 1, listToolbar: 1, listArea: 1 },
        SIMPLE_LIST:      { searchArea: 1, listToolbar: 1, listArea: 1 }
    };

    /** 이 (아키타입, 슬롯, 인덱스) 조합의 크기 조절이 생성 파일에 실제 반영되는가. */
    meta.sizeCssEmitted = function (archetype, slotKey, index) {
        var m = meta.SIZE_CSS_EMITTED[archetype];
        return !!(m && m[slotKey] && Number(index) === 0); // 템플릿이 [0] 인스턴스만 산출
    };

    meta.LABELS = {
        searchArea: "검색영역", listArea: "목록영역", listToolbar: "목록툴바",
        detailBasic: "상세기본", detailTabs: "상세탭", detailToolbar: "상세툴바",
        leftArea: "좌측영역", rightArea: "우측영역", popupBody: "팝업 본문", widgetArea: "위젯 영역",
        canvasArea: "캔버스"
    };

    meta.ORDER = [
        "searchArea", "listToolbar", "listArea",
        "detailToolbar", "detailBasic", "detailTabs",
        "leftArea", "rightArea", "popupBody", "widgetArea", "canvasArea"
    ];

    meta.label = function (slotKey) {
        return meta.LABELS[slotKey] || String(slotKey);
    };

    /** 아키타입의 슬롯키를 표시 순서(ORDER 우선, 그 외 뒤에)로 돌려준다. 미등록 아키타입은 []. */
    meta.orderedSlots = function (archetype) {
        var wl = meta.WHITELIST[archetype];
        if (!wl) { return []; }
        var keys = Object.keys(wl);
        var ordered = [];
        meta.ORDER.forEach(function (k) { if (keys.indexOf(k) !== -1) { ordered.push(k); } });
        keys.forEach(function (k) { if (ordered.indexOf(k) === -1) { ordered.push(k); } });
        return ordered;
    };

    /** categoryCode 가 배치 가능한 슬롯키 배열. */
    meta.placeableSlots = function (archetype, categoryCode) {
        var wl = meta.WHITELIST[archetype];
        var out = [];
        if (!wl) { return out; }
        Object.keys(wl).forEach(function (slotKey) {
            if (wl[slotKey].cats.indexOf(categoryCode) !== -1) { out.push(slotKey); }
        });
        return out;
    };
})(window.JWorks_JSForgeAdminStudioSlotMeta);
