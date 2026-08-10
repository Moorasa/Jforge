/* ===============================================================================================
Name : propsPanel.js
Description : 우측 속성패널 컨트롤러 (P3-4, P7-2/3 개편). schemaFormRenderer(순수 렌더러)와
             MagicIAM_JSForgeAdminStudio(허브)를 잇는 얇은 어댑터.

동작:
 1) 인스턴스 선택(브리지 단일 소유 — previewBridge.setSelected → 여기 selectInstance 호출) 시:
    선택 헤더(모듈명·슬롯·instanceId·삭제 버튼) + PROP_SCHEMA_JSON 폼 렌더.
 2) 폼 change 시: schemaFormRenderer.collect() → 해당 인스턴스 props 갱신한 새 DEFINITION_JSON
    → studio.updateDefinitionJson(newDef, {reason:'edit'}).
 3) 선택 없음/화면 전환 시 안내 메시지.

P7-3: 임시 자동선택(autoSelectFirst) 제거 — 선택은 캔버스/팔레트발 브리지 경유로만 진입한다.
      정의 변경(palette/preview/saved/undo 등) 시 현재 선택이 살아있으면 재렌더, 사라졌으면 해제.

XSS: 모든 표시 문자열은 schemaFormRenderer/이 컨트롤러 모두 textContent/createElement 로만.
=============================================================================================== */
window.MagicIAM_JSForgeAdminStudioProps = window.MagicIAM_JSForgeAdminStudioProps || {};
(function (mod) {
    "use strict";
    if (mod.__defined) { return; }
    mod.__defined = true;

    var studio = window.MagicIAM_JSForgeAdminStudio;
    var renderer = window.MagicIAM_JSForgeSchemaRenderer;
    var slotMeta = window.MagicIAM_JSForgeAdminStudioSlotMeta;
    var ctx = (window.MagicIAM_JSForge && window.MagicIAM_JSForge.contextPath) || "";
    var apiModuleTypes = ctx + "/api/module-types";

    // 팔레트(모듈명 조회)/브리지(삭제 진입점)는 사용 시점에 조회(로드 순서 무관).
    function palette() { return window.MagicIAM_JSForgeAdminStudioPalette; }
    function bridge() { return window.MagicIAM_JSForgeAdminStudioPreviewBridge; }

    // moduleTypeCode → PROP_SCHEMA_JSON(파싱된 객체) 캐시.
    var schemaCache = {};

    // 현재 선택 상태.
    var selectedInstanceId = null;
    var currentSchema = null;   // collect 에 동일 스키마 필요
    var currentFormRoot = null; // collect 의 rootEl
    var LIVE_INPUT_DELAY = 220;
    var liveInputTimer = null;
    var liveInputTab = "props";
    var editHistoryOpen = false;
    var activeTab = "props";

    // 모듈 props와 분리하는 인스턴스 공통 설계 메타다.
    var DATA_SCHEMA = {
        title: "데이터 바인딩",
        fields: [
            { key: "enabled", label: "데이터 바인딩 사용", type: "boolean", default: false },
            { key: "endpoint", label: "API 경로", type: "text", default: "", placeholder: "/api/users" },
            { key: "method", label: "요청 방식", type: "select", default: "GET", options: [
                { value: "GET", label: "GET" }, { value: "POST", label: "POST" }
            ] },
            { key: "resultPath", label: "결과 경로", type: "text", default: "items" },
            { key: "autoLoad", label: "화면 진입 시 자동 조회", type: "boolean", default: true },
            // 서버 바인딩(계약 §14). 채우면 생성 시 Controller/Mapper/SQL이 빈 stub 대신 실제 조회 API로 산출된다.
            { key: "table", label: "테이블 (선택)", type: "text", default: "", placeholder: "TB_USER" },
            { key: "keyColumn", label: "키 컬럼 (선택)", type: "text", default: "", placeholder: "USER_ID" }
        ]
    };

    var EVENTS_SCHEMA = {
        title: "이벤트 연결",
        fields: [
            { key: "handlers", label: "처리기", type: "columns", default: [], columns: [
                { key: "event", label: "이벤트", type: "select", default: "click", options: [
                    { value: "click", label: "클릭" }, { value: "change", label: "값 변경" }, { value: "select", label: "선택" }
                ] },
                { key: "action", label: "동작", type: "select", default: "", options: [
                    { value: "", label: "선택..." }, { value: "reload", label: "다시 조회" },
                    { value: "openDetail", label: "상세 열기" }, { value: "submit", label: "저장" },
                    { value: "custom", label: "사용자 정의" }
                ] },
                { key: "target", label: "대상/키", type: "text", default: "" }
            ] }
        ]
    };

    // ---------- DOM 헬퍼 ----------
    function el(tag, className, text) {
        var node = document.createElement(tag);
        if (className) { node.className = className; }
        if (text != null) { node.textContent = String(text); } // textContent 로만
        return node;
    }

    function pane() { return document.getElementById("frg-pane-props"); }

    function bodyContainer() {
        var p = pane();
        if (!p) { return null; }
        var body = p.querySelector(".frg-pane-body");
        if (!body) {
            body = el("div", "frg-pane-body");
            p.appendChild(body);
        }
        return body;
    }

    function clear(node) {
        while (node.firstChild) { node.removeChild(node.firstChild); }
    }

    function showMessage(text) {
        var body = bodyContainer();
        if (!body) { return; }
        clear(body);
        body.className = "frg-pane-body frg-empty";
        body.appendChild(el("p", null, text));
        currentSchema = null;
        currentFormRoot = null;
    }

    // ---------- 인스턴스 탐색 ----------
    function listInstances(def) {
        var out = [];
        if (!def || typeof def !== "object" || !def.slots || typeof def.slots !== "object") { return out; }
        Object.keys(def.slots).forEach(function (slotKey) {
            var arr = def.slots[slotKey];
            if (!Array.isArray(arr)) { return; }
            arr.forEach(function (inst, idx) {
                if (inst && typeof inst === "object" && inst.instanceId != null) {
                    out.push({ slotKey: slotKey, index: idx, instance: inst });
                }
            });
        });
        return out;
    }

    function findInstance(def, instanceId) {
        var all = listInstances(def);
        for (var i = 0; i < all.length; i++) {
            if (all[i].instance.instanceId === instanceId) { return all[i]; }
        }
        return null;
    }

    // ---------- 스키마 조회 ----------
    function fetchSchema(moduleTypeCode) {
        if (schemaCache[moduleTypeCode]) {
            return Promise.resolve(schemaCache[moduleTypeCode]);
        }
        var url = apiModuleTypes + "/" + encodeURIComponent(moduleTypeCode);
        return fetch(url, { headers: { "Accept": "application/json" } })
            .then(function (r) { if (!r.ok) { throw new Error("http " + r.status); } return r.json(); })
            .then(function (mt) {
                var schema = mt ? mt.propSchemaJson : null;
                if (typeof schema === "string") {
                    try { schema = JSON.parse(schema); } catch (e) { schema = null; }
                }
                schemaCache[moduleTypeCode] = schema;
                return schema;
            });
    }

    // ---------- 선택 헤더(P7-2): 모듈명·슬롯·instanceId·삭제 ----------
    function buildHeader(found) {
        var head = el("div", "frg-props-head");
        var pal = palette();
        var m = (pal && typeof pal.getModule === "function")
            ? pal.getModule(found.instance.moduleTypeCode) : null;

        var titleRow = el("div", "frg-props-title-row");
        titleRow.appendChild(el("span", "frg-props-title",
            (m && m.moduleName) ? m.moduleName : found.instance.moduleTypeCode));
        var del = el("button", "frg-props-del", "삭제");
        del.type = "button";
        del.title = "이 모듈을 화면에서 제거";
        del.setAttribute("data-del-instance", String(found.instance.instanceId));
        titleRow.appendChild(del);
        head.appendChild(titleRow);

        var meta = el("div", "frg-props-meta");
        meta.appendChild(el("span", "frg-props-slot",
            slotMeta ? slotMeta.label(found.slotKey) : found.slotKey));
        meta.appendChild(el("span", "frg-props-id", found.instance.instanceId));
        head.appendChild(meta);
        head.appendChild(buildTabs());
        return head;
    }

    function buildTabs() {
        var tabs = el("div", "frg-props-tabs");
        [["props", "속성"], ["data", "데이터"], ["events", "이벤트"]].forEach(function (entry) {
            var button = el("button", "frg-props-tab" + (activeTab === entry[0] ? " is-active" : ""), entry[1]);
            button.type = "button";
            button.setAttribute("data-props-tab", entry[0]);
            button.setAttribute("aria-selected", activeTab === entry[0] ? "true" : "false");
            tabs.appendChild(button);
        });
        return tabs;
    }

    // ---------- 크기 입력(P8, §13 layout props) ----------
    // 캔버스 리사이즈 핸들과 같은 키(layoutWidthPct/layoutHeightPx)를 편집한다. 빈 값 = 자동(키 제거).
    function sizeInput(placeholder, min, max, value) {
        var input = document.createElement("input");
        input.type = "number";
        input.className = "frg-input frg-size-input";
        input.min = String(min);
        input.max = String(max);
        input.placeholder = placeholder;
        if (typeof value === "number") { input.value = String(value); }
        return input;
    }

    /** 현재 화면의 아키타입 코드(없으면 null). */
    function archetypeCode() {
        var screen = (typeof studio.getScreen === "function") ? studio.getScreen() : null;
        return screen ? screen.archetypeCode : null;
    }

    // ---------- 좌표 입력(P13, §17.2 layout 키) ----------
    // 캔버스의 이동·리사이즈 핸들과 같은 키를 편집한다. 자유 배치는 4키가 항상 채워져 있으므로
    // (배치 시점에 seed) 여기서는 "자동" 개념이 없다 — 빈 값이면 기존 값을 유지한다.
    var CANVAS_FIELDS = [
        ["layoutXPx", "X"], ["layoutYPx", "Y"],
        ["layoutWPx", "W"], ["layoutHPx", "H"], ["layoutZ", "Z"]
    ];

    function buildCanvasRow(found) {
        var props = found.instance.props || {};
        var row = el("div", "frg-props-size frg-props-canvas");
        row.appendChild(el("span", "frg-props-size-label", "위치·크기"));
        var inputs = {};
        CANVAS_FIELDS.forEach(function (entry) {
            var key = entry[0];
            var rule = slotMeta.CANVAS_KEYS[key];
            var wrap = el("label", "frg-size-field");
            var input = sizeInput("", rule.min, rule.max,
                (typeof props[key] === "number") ? props[key] : undefined);
            inputs[key] = input;
            wrap.appendChild(input);
            wrap.appendChild(el("span", "frg-size-unit", entry[1]));
            row.appendChild(wrap);
            input.addEventListener("change", function () {
                applyCanvasLayout(found.instance.instanceId, inputs);
            });
        });
        return row;
    }

    /** 좌표 입력 반영: 기존 props 보존 + §17.2 키만 갱신(빈 값은 건드리지 않는다). */
    function applyCanvasLayout(instanceId, inputs) {
        var def = studio.getDefinitionJson();
        var found = findInstance(def, instanceId);
        if (!found) { return; }
        var newDef = shallowCloneDef(def);
        var arr = newDef.slots[found.slotKey].slice();
        var oldInst = arr[found.index];
        var props = {};
        Object.keys(oldInst.props || {}).forEach(function (k) { props[k] = oldInst.props[k]; });

        Object.keys(inputs).forEach(function (key) {
            var raw = inputs[key].value;
            if (String(raw).trim() === "") { return; }
            var value = slotMeta.clampCanvas(key, raw);
            if (value != null) { props[key] = value; }
        });

        arr[found.index] = copyInstance(oldInst);
        arr[found.index].props = props;
        newDef.slots[found.slotKey] = arr;
        studio.updateDefinitionJson(newDef, { reason: "edit", history: "new" });
    }

    function buildSizeRow(found) {
        // §17: 자유 배치는 폭%/높이px 대신 X/Y/W/H/Z 절대좌표를 편집한다.
        if (slotMeta && slotMeta.isFreeCanvas(archetypeCode())) {
            return buildCanvasRow(found);
        }
        // P13-0: 크기가 실제로 생성 CSS 에 나가는 슬롯에서만 입력을 제공한다(캔버스 핸들과 동일 게이트).
        // 산출되지 않는 자리에 입력을 두면 "고쳤는데 파일은 그대로"가 되어 에디터가 거짓말을 한다.
        if (!slotMeta || !slotMeta.sizeCssEmitted(archetypeCode(), found.slotKey, found.index)) {
            return null;
        }
        var props = found.instance.props || {};
        var row = el("div", "frg-props-size");
        row.appendChild(el("span", "frg-props-size-label", "크기"));

        var wWrap = el("label", "frg-size-field");
        var wInput = sizeInput("자동", 10, 100,
            (typeof props.layoutWidthPct === "number") ? props.layoutWidthPct : undefined);
        wWrap.appendChild(wInput);
        wWrap.appendChild(el("span", "frg-size-unit", "% 너비"));
        row.appendChild(wWrap);

        var hWrap = el("label", "frg-size-field");
        var hInput = sizeInput("자동", 40, 2000,
            (typeof props.layoutHeightPx === "number") ? props.layoutHeightPx : undefined);
        hWrap.appendChild(hInput);
        hWrap.appendChild(el("span", "frg-size-unit", "px 높이"));
        row.appendChild(hWrap);

        function apply() { applySize(found.instance.instanceId, wInput.value, hInput.value); }
        wInput.addEventListener("change", apply);
        hInput.addEventListener("change", apply);
        return row;
    }

    /** 크기 입력 반영: 기존 props 보존 + layout 키만 갱신(빈 값은 키 제거 = 자동). */
    function applySize(instanceId, widthRaw, heightRaw) {
        var def = studio.getDefinitionJson();
        var found = findInstance(def, instanceId);
        if (!found) { return; }
        var newDef = shallowCloneDef(def);
        var arr = newDef.slots[found.slotKey].slice();
        var oldInst = arr[found.index];
        var props = {};
        Object.keys(oldInst.props || {}).forEach(function (k) { props[k] = oldInst.props[k]; });

        var w = parseInt(widthRaw, 10);
        if (isFinite(w)) { props.layoutWidthPct = Math.min(100, Math.max(10, w)); }
        else { delete props.layoutWidthPct; }
        var h = parseInt(heightRaw, 10);
        if (isFinite(h)) { props.layoutHeightPx = Math.min(2000, Math.max(40, h)); }
        else { delete props.layoutHeightPx; }

        arr[found.index] = copyInstance(oldInst);
        arr[found.index].props = props;
        newDef.slots[found.slotKey] = arr;
        // reason "edit" → 패널 자신은 재렌더하지 않아 입력 포커스 유지, 캔버스는 브리지가 재푸시.
        studio.updateDefinitionJson(newDef, { reason: "edit", history: "new" });
    }

    // ---------- 렌더 ----------
    function renderInstance(found) {
        endEditHistory();
        var instance = found.instance;
        if (!instance || !instance.moduleTypeCode) {
            showMessage("캔버스에서 모듈을 선택하세요.");
            return;
        }
        var body = bodyContainer();
        if (!body || !renderer) { return; }
        clear(body);
        body.className = "frg-pane-body";
        body.appendChild(buildHeader(found));
        var content = el("div", "frg-props-content");
        body.appendChild(content);
        renderActiveTab(found, content);
    }

    function renderActiveTab(found, content) {
        if (activeTab === "data") {
            var values = copyPlain(found.instance.data || {});
            values.enabled = !!found.instance.data;
            renderForm(content, DATA_SCHEMA, values, "data", found, false);
            // P11: 타겟 DB에서 테이블/컬럼을 가져오는 진입점. renderForm이 content를 비우므로 그 뒤에 끼운다.
            content.insertBefore(buildDbBindRow(found), content.firstChild);
            return;
        }
        if (activeTab === "events") {
            renderForm(content, EVENTS_SCHEMA, { handlers: copyPlain(found.instance.events || []) }, "events", found, false);
            return;
        }
        fetchSchema(found.instance.moduleTypeCode)
            .then(function (schema) {
                if (selectedInstanceId !== found.instance.instanceId || activeTab !== "props") { return; }
                renderForm(content, schema, found.instance.props || {}, "props", found, true);
                // §19: 프레임은 "어느 화면을 띄울지"가 첫 질문이다. renderForm 이 content 를
                // 비우므로 그 뒤에 끼운다(P11 dbBind 와 같은 자리).
                if (found.instance.moduleTypeCode === "LAYOUT_FRAME") {
                    content.insertBefore(buildFrameSrcRow(found), content.firstChild);
                }
            })
            .catch(function () {
                if (selectedInstanceId === found.instance.instanceId && activeTab === "props") {
                    showMessage("속성 스키마를 불러오지 못했습니다.");
                }
            });
    }

    function renderForm(content, schema, values, tab, found, includeSize) {
        if (!content || !renderer) { return; }
        clear(content);
        if (includeSize) {
            var sizeRow = buildSizeRow(found);
            if (sizeRow) { content.appendChild(sizeRow); }
        }
        var formWrap = el("div", "frg-props-form");
        formWrap.setAttribute("data-inspector-tab", tab);
        formWrap.appendChild(renderer.render(schema, values));
        content.appendChild(formWrap);
        currentSchema = schema;
        currentFormRoot = formWrap;
        bindFormChange(formWrap, tab);
    }

    // ---------- 폼 변경 → DEFINITION_JSON 갱신 ----------
    // text 입력은 짧게 디바운스해 라이브 프리뷰로 보낸다. 한 포커스 세션의 연속 입력은
    // undo 히스토리를 한 건으로 묶어 Ctrl+Z가 글자 하나씩 되감기지 않도록 한다.
    function bindFormChange(root, tab) {
        root.addEventListener("input", function () {
            if (liveInputTimer) { clearTimeout(liveInputTimer); }
            liveInputTab = tab;
            liveInputTimer = setTimeout(function () {
                liveInputTimer = null;
                onFormChange(nextHistoryMode(), tab);
            }, LIVE_INPUT_DELAY);
        });
        root.addEventListener("change", function () {
            flushLiveInput(tab);
            onFormChange(nextHistoryMode(), tab);
            endEditHistory();
        });
        root.addEventListener("focusout", function () {
            flushLiveInput(tab);
            endEditHistory();
        });
        root.addEventListener("click", function (e) {
            if (renderer && typeof renderer.handleAction === "function"
                    && renderer.handleAction(root, currentSchema, e)) {
                e.preventDefault();
                flushLiveInput(tab);
                onFormChange("new", tab);
                endEditHistory();
            }
        });
        root.addEventListener("keydown", function (e) {
            if (renderer && typeof renderer.handleKeydown === "function"
                    && renderer.handleKeydown(root, e)) {
                e.preventDefault();
                flushLiveInput(tab);
                onFormChange("new", tab);
                endEditHistory();
            }
        });
    }

    function flushLiveInput(tab) {
        if (!liveInputTimer) { return; }
        clearTimeout(liveInputTimer);
        liveInputTimer = null;
        onFormChange(nextHistoryMode(), tab || liveInputTab);
    }

    function nextHistoryMode() {
        if (editHistoryOpen) { return "merge"; }
        editHistoryOpen = true;
        return "new";
    }

    function endEditHistory() {
        editHistoryOpen = false;
    }

    function onFormChange(historyMode, tab) {
        if (!currentSchema || !currentFormRoot || !renderer) { return; }
        var def = studio.getDefinitionJson();
        var found = findInstance(def, selectedInstanceId);
        if (!found) { return; }

        var collected = renderer.collect(currentFormRoot, currentSchema);

        // 불변 갱신: 편집 대상만 교체하고 기존 인스턴스 메타는 보존한다.
        // P8: 스키마 밖 예약 키(layoutWidthPct/layoutHeightPx 등 §13)는 props 폼 수집에 없으므로
        // 기존 props 위에 수집분을 병합한다(캔버스 리사이즈 값 유실 방지).
        var newDef = shallowCloneDef(def);
        var arr = newDef.slots[found.slotKey];
        var newArr = arr.slice();
        var oldInst = newArr[found.index];
        var nextInst = copyInstance(oldInst);

        if (tab === "data") {
            // endpoint가 비어 있거나 사용 해제면 메타 자체를 제거한다. 기존 생성 결과는 그대로 유지된다.
            if (collected && collected.enabled && String(collected.endpoint || "").trim()) {
                nextInst.data = {
                    endpoint: String(collected.endpoint).trim(),
                    method: collected.method === "POST" ? "POST" : "GET",
                    resultPath: String(collected.resultPath || "").trim(),
                    autoLoad: collected.autoLoad !== false
                };
                // 서버 바인딩(계약 §14): 빈 값이면 키 자체를 넣지 않는다(기존 화면 DEFINITION 무변경).
                // 형식 최종 방어는 서버(저장 검증 + 생성 게이트) — 여기서는 공백만 정리한다.
                var boundTable = String((collected.table == null ? "" : collected.table)).trim();
                if (boundTable) { nextInst.data.table = boundTable; }
                var boundKey = String((collected.keyColumn == null ? "" : collected.keyColumn)).trim();
                if (boundKey) { nextInst.data.keyColumn = boundKey; }
            } else {
                delete nextInst.data;
            }
        } else if (tab === "events") {
            var handlers = (collected && Array.isArray(collected.handlers)) ? collected.handlers : [];
            handlers = handlers.filter(function (handler) {
                return handler && typeof handler.action === "string" && handler.action !== "";
            }).map(function (handler) {
                return {
                    event: (handler.event === "change" || handler.event === "select") ? handler.event : "click",
                    action: handler.action,
                    target: String(handler.target || "").trim()
                };
            });
            if (handlers.length) { nextInst.events = handlers; }
            else { delete nextInst.events; }
        } else {
            var merged = {};
            Object.keys(oldInst.props || {}).forEach(function (k) { merged[k] = oldInst.props[k]; });
            Object.keys(collected || {}).forEach(function (k) { merged[k] = collected[k]; });
            nextInst.props = merged;
        }
        newArr[found.index] = nextInst;
        newDef.slots[found.slotKey] = newArr;

        studio.updateDefinitionJson(newDef, { reason: "edit", history: historyMode || "new" });
    }

    // ---------- P11: 타겟 DB 테이블 → 컬럼 자동 채움 ----------

    function buildDbBindRow(found) {
        var row = el("div", "frg-props-dbbind");
        var button = el("button", "frg-btn frg-btn-secondary frg-btn-sm", "테이블에서 가져오기");
        button.type = "button";
        button.title = "타겟 DB의 테이블을 골라 컬럼을 자동으로 채웁니다";
        button.addEventListener("click", function () { openDbPicker(found.instance.instanceId); });
        row.appendChild(button);
        row.appendChild(el("small", "frg-hint", "테이블을 고르면 컬럼·키 컬럼이 채워집니다."));
        return row;
    }

    function openDbPicker(instanceId) {
        var picker = window.MagicIAM_JSForgeAdminStudioDbPicker;
        if (!picker || typeof picker.open !== "function") { return; }
        var projectId = (typeof studio.getProjectId === "function") ? studio.getProjectId() : null;
        picker.open(projectId, function (selection) { applyDbSelection(instanceId, selection); });
    }

    // ---------- §19: 프레임에 넣을 화면 고르기 ----------

    /** LAYOUT_FRAME 인스턴스일 때만 속성 탭 맨 위에 얹는다. */
    function buildFrameSrcRow(found) {
        var row = el("div", "frg-props-dbbind");
        var current = String(((found.instance.props || {}).frameSrc) || "");
        var button = el("button", "frg-btn frg-btn-secondary frg-btn-sm",
            current ? "연결된 화면 바꾸기" : "화면 연결하기");
        button.type = "button";
        button.title = "이 프레임이 불러올 화면의 경로를 지정합니다";
        button.addEventListener("click", function () { openScreenPicker(found.instance.instanceId); });
        row.appendChild(button);
        row.appendChild(el("small", "frg-hint",
            current ? current : "지금은 빈 프레임입니다(생성 시 src 미산출)."));
        return row;
    }

    function openScreenPicker(instanceId) {
        var picker = window.MagicIAM_JSForgeAdminStudioScreenPicker;
        if (!picker || typeof picker.open !== "function") { return; }
        var projectId = (typeof studio.getProjectId === "function") ? studio.getProjectId() : null;
        var def = studio.getDefinitionJson();
        var found = findInstance(def, instanceId);
        var current = found ? String(((found.instance.props || {}).frameSrc) || "") : "";
        var screen = (typeof studio.getScreen === "function") ? studio.getScreen() : null;
        picker.open(projectId, current, screen ? screen.screenId : null, function (path) {
            applyFrameSrc(instanceId, path);
        });
    }

    /** 빈 문자열이면 키를 지운다 — 없는 상태와 "빈 값" 상태가 갈라지지 않게(§19.2 하위호환). */
    function applyFrameSrc(instanceId, path) {
        var def = studio.getDefinitionJson();
        var found = findInstance(def, instanceId);
        if (!found) { return; }

        var newDef = shallowCloneDef(def);
        var newArr = newDef.slots[found.slotKey].slice();
        var nextInst = copyInstance(newArr[found.index]);
        var props = copyPlain(nextInst.props || {});
        var value = String(path == null ? "" : path).trim();
        if (value === "") { delete props.frameSrc; }
        else { props.frameSrc = value; }
        nextInst.props = props;

        newArr[found.index] = nextInst;
        newDef.slots[found.slotKey] = newArr;
        endEditHistory();
        studio.updateDefinitionJson(newDef, { reason: "frameSrc", history: "new" });
    }

    /**
     * 선택 결과를 DEFINITION에 반영한다(불변 갱신 · undo 대상 1건).
     * endpoint가 비어 있으면 화면 stem 기준 기본값을 넣는다 — data 노드는 서버 저장 검증(§5)에서
     * endpoint를 필수로 보기 때문에, 빈 값으로 두면 저장 자체가 거부된다.
     */
    function applyDbSelection(instanceId, selection) {
        if (!selection || !selection.table) { return; }
        var def = studio.getDefinitionJson();
        var found = findInstance(def, instanceId);
        if (!found) { return; }

        var newDef = shallowCloneDef(def);
        var newArr = newDef.slots[found.slotKey].slice();
        var nextInst = copyInstance(newArr[found.index]);

        var data = nextInst.data ? copyPlain(nextInst.data) : {};
        data.endpoint = String(data.endpoint || "").trim() || defaultEndpoint();
        data.method = data.method === "POST" ? "POST" : "GET";
        data.resultPath = String(data.resultPath || "").trim() || "items";
        data.autoLoad = data.autoLoad !== false;
        data.table = selection.table;
        if (selection.keyColumn) { data.keyColumn = selection.keyColumn; }
        else { delete data.keyColumn; }
        nextInst.data = data;

        // 컬럼 props 구조는 TABLE_VIEW 스키마 기준이라 그 모듈에만 채운다(다른 뷰는 표시 필드가 다름).
        if (nextInst.moduleTypeCode === "TABLE_VIEW" && Array.isArray(selection.columns)) {
            var props = copyPlain(nextInst.props || {});
            props.columns = selection.columns;
            nextInst.props = props;
        }

        newArr[found.index] = nextInst;
        newDef.slots[found.slotKey] = newArr;
        endEditHistory();
        studio.updateDefinitionJson(newDef, { reason: "dbBind", history: "new" });
        selectInstance(instanceId); // 패널 재렌더(방금 채운 값 반영)

        if (window.JWORKS_JSSnackBar) {
            var count = Array.isArray(selection.columns) ? selection.columns.length : 0;
            window.JWORKS_JSSnackBar.create(
                selection.table + " 연결 · 컬럼 " + count + "개 · API 경로 " + data.endpoint);
        }
    }

    /** 화면 stem 기준 기본 API 경로(생성될 Controller의 @RequestMapping과 같은 모양). */
    function defaultEndpoint() {
        var screen = (typeof studio.getScreen === "function") ? studio.getScreen() : null;
        var stem = (screen && screen.stem) ? String(screen.stem) : "";
        return /^[a-z][a-zA-Z0-9]*$/.test(stem) ? "/api/" + stem : "/api/list";
    }

    function shallowCloneDef(def) {
        var copy = {};
        Object.keys(def).forEach(function (k) { copy[k] = def[k]; });
        var slots = {};
        Object.keys(def.slots || {}).forEach(function (sk) {
            slots[sk] = def.slots[sk];
        });
        copy.slots = slots;
        return copy;
    }

    function copyPlain(value) {
        try { return JSON.parse(JSON.stringify(value == null ? {} : value)); }
        catch (e) { return value || {}; }
    }

    // 인스턴스 공통 메타(data/events 포함)를 props 수정·크기조절 중에도 add-only로 보존한다.
    function copyInstance(instance) {
        var copy = {};
        Object.keys(instance || {}).forEach(function (key) { copy[key] = instance[key]; });
        copy.props = copyPlain((instance && instance.props) || {});
        if (instance && instance.data) { copy.data = copyPlain(instance.data); }
        if (instance && instance.events) { copy.events = copyPlain(instance.events); }
        return copy;
    }

    // ---------- 선택 진입점(브리지가 호출 — 단일 선택 소유는 브리지) ----------
    function selectInstance(instanceId) {
        if (instanceId == null) {
            selectedInstanceId = null;
            showMessage("캔버스에서 모듈을 선택하세요.");
            return;
        }
        var def = studio.getDefinitionJson();
        var found = findInstance(def, instanceId);
        if (!found) {
            selectedInstanceId = null;
            showMessage("캔버스에서 모듈을 선택하세요.");
            return;
        }
        if (selectedInstanceId !== instanceId) { activeTab = "props"; }
        selectedInstanceId = instanceId;
        renderInstance(found);
    }

    // ---------- 오케스트레이터 결선 ----------
    function init() {
        if (!studio || !renderer) { return; }

        // 정의 변경 구독. reason:"edit"(자기 자신 유발)는 재렌더하지 않는다(입력 커서 보존).
        // 그 외(palette/preview/saved/undo/screenLoaded): 현재 선택이 살아있으면 재렌더, 아니면 해제.
        studio.onDefinitionChanged(function (def, meta) {
            if (meta && meta.reason === "edit") { return; }
            if (meta && meta.reason === "screenLoaded") {
                selectedInstanceId = null;
                showMessage("캔버스에서 모듈을 선택하세요.");
                return;
            }
            if (selectedInstanceId) {
                var found = findInstance(def, selectedInstanceId);
                if (found) { renderInstance(found); }
                else {
                    selectedInstanceId = null;
                    showMessage("캔버스에서 모듈을 선택하세요.");
                }
            }
        });

        // 삭제 버튼(선택 헤더) — 브리지 삭제 진입점 경유(undo 로 복구 가능, P7-3).
        var p = pane();
        if (p) {
            p.addEventListener("click", function (e) {
                var t = e.target;
                var tabButton = t && t.closest ? t.closest("[data-props-tab]") : null;
                if (tabButton) {
                    var nextTab = tabButton.getAttribute("data-props-tab");
                    if (nextTab === "props" || nextTab === "data" || nextTab === "events") {
                        activeTab = nextTab;
                        var def = studio.getDefinitionJson();
                        var found = findInstance(def, selectedInstanceId);
                        if (found) { renderInstance(found); }
                    }
                    return;
                }
                var id = t && t.getAttribute ? t.getAttribute("data-del-instance") : null;
                if (!id) { return; }
                var b = bridge();
                if (b && typeof b.requestDelete === "function") { b.requestDelete(id); }
            });
        }

        // 초기 상태.
        showMessage("캔버스에서 모듈을 선택하세요.");
    }

    mod.selectInstance = selectInstance;
    mod.getSelectedInstanceId = function () { return selectedInstanceId; };

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})(window.MagicIAM_JSForgeAdminStudioProps);
