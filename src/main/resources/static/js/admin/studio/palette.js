/* ===============================================================================================
Name : palette.js
Description : 좌측 팔레트 + 슬롯 조립 컨트롤러 (P3-5, P7-2 전면 개편).
             JWorks_JSForgeAdminStudio(허브) 위에서 동작. 기획서 0장 2번 원칙(자유 드래그 캔버스
             아님 → 팔레트/캔버스 클릭으로 정해진 슬롯에 조립)을 구현한다.

P7-2 배치 흐름(슬롯픽커 폐기):
 1) 팔레트 모듈 클릭 → 배치 후보 슬롯 1개면 즉시 배치, 여러 개면 previewBridge.setPending 으로
    캔버스 후보 슬롯 하이라이트 → 캔버스 슬롯 클릭이 placeInto 를 호출(브리지 경유). Esc 취소.
 2) 캔버스 빈 슬롯 "+ 모듈 추가" → openChooser(slotKey): 그 슬롯에 배치 가능한 모듈만 추린
    선택 모달 → 클릭 배치.
 부가: 카테고리 그룹핑 + 검색 + 현재 아키타입에 배치 불가 모듈 dim.

★ 구조 방어 경계(§5 신뢰경계) ★
 클라이언트 슬롯 화이트리스트(slotMeta.js 단일 소스)와 cardinality/카테고리 검사는 UX 1차 검증이며
 최종 신뢰 대상이 아니다. 서버 최종 구조 검증(P3-5b)이 저장 경로에서 수행된다.

XSS: 모든 표시 문자열은 textContent/createElement 로만. innerHTML(및 .html()) 미사용.
의존: slotMeta.js(슬롯 메타 단일 소스), previewBridge.js(배치 대기/선택 코디네이터, 지연 조회)
=============================================================================================== */
window.JWorks_JSForgeAdminStudioPalette = window.JWorks_JSForgeAdminStudioPalette || {};
(function (mod) {
    "use strict";
    if (mod.__defined) { return; }
    mod.__defined = true;

    var studio = window.JWorks_JSForgeAdminStudio;
    var slotMeta = window.JWorks_JSForgeAdminStudioSlotMeta;
    var ctx = (window.JWorks_JSForge && window.JWorks_JSForge.contextPath) || "";
    var apiModuleTypes = ctx + "/api/module-types";

    // 브리지/속성패널은 로드 순서 이슈를 피해 사용 시점에 조회.
    function bridge() { return window.JWorks_JSForgeAdminStudioPreviewBridge; }
    function props() { return window.JWorks_JSForgeAdminStudioProps; }

    // 카테고리 표시 순서/라벨(그 외 카테고리는 뒤에 원문 표기).
    var CATEGORY_ORDER = ["VIEW", "FILTER", "ACTION", "DETAIL", "LAYOUT"];
    var CATEGORY_LABELS = {
        VIEW: "뷰", FILTER: "필터", ACTION: "액션", DETAIL: "상세", LAYOUT: "레이아웃"
    };

    // 모듈 카탈로그 캐시(코드 → 목록 항목 / PROP_SCHEMA). 클릭 시 재조회 방지.
    var moduleList = [];   // GET /api/module-types 결과
    var moduleByCode = {}; // moduleTypeCode → 목록 항목
    var schemaCache = {};  // moduleTypeCode → PROP_SCHEMA_JSON(파싱 객체)
    var searchTerm = "";   // 팔레트 검색어(소문자)
    var activeSlotKey = null; // 현재 펼친 캔버스 영역

    // ---------- 위젯 헬퍼 ----------
    function confirmDialog(title, text, cb) {
        if (window.JWORKS_JSConfirm && typeof window.JWORKS_JSConfirm.start === "function") {
            window.JWORKS_JSConfirm.start(String(title == null ? "" : title), String(text), function (result) {
                cb(!!result);
            });
            return;
        }
        cb(window.confirm(String(text)));
    }

    // ---------- DOM 헬퍼 ----------
    function el(tag, className, text) {
        var node = document.createElement(tag);
        if (className) { node.className = className; }
        if (text != null) { node.textContent = String(text); } // textContent 로만
        return node;
    }

    function clear(node) {
        while (node.firstChild) { node.removeChild(node.firstChild); }
    }

    function pane() { return document.getElementById("frg-pane-palette"); }

    function slotLabel(slotKey) {
        return slotMeta ? slotMeta.label(slotKey) : String(slotKey);
    }

    // ---------- 팔레트 pane 구조 확보 ----------
    // pane 안에 [검색] + [모듈 목록(카테고리 그룹)] + [상태 메시지] 를 확보한다.
    function ensureStructure() {
        var p = pane();
        if (!p) { return null; }
        var body = p.querySelector(".frg-pane-body");
        if (!body) {
            body = el("div", "frg-pane-body");
            p.appendChild(body);
        }
        if (!body.querySelector(".frg-palette-modules")) {
            clear(body);
            body.className = "frg-pane-body frg-palette-body";
            var search = document.createElement("input");
            search.type = "search";
            search.className = "frg-input frg-palette-search";
            search.placeholder = "모듈 검색…";
            search.setAttribute("aria-label", "모듈 검색");
            body.appendChild(search);
            body.appendChild(el("p", "frg-palette-hint", "모듈을 클릭하거나 캔버스 슬롯으로 드래그해 배치합니다."));
            body.appendChild(el("ul", "frg-palette-modules"));
            body.appendChild(el("p", "frg-palette-msg")); // 안내 메시지
        }
        return {
            body: body,
            search: body.querySelector(".frg-palette-search"),
            modules: body.querySelector(".frg-palette-modules"),
            msg: body.querySelector(".frg-palette-msg")
        };
    }

    function setMessage(text) {
        var s = ensureStructure();
        if (!s) { return; }
        s.msg.textContent = String(text == null ? "" : text);
    }

    // ---------- 모듈 카탈로그 로드 ----------
    function loadModules() {
        var s = ensureStructure();
        if (!s) { return; }
        clear(s.modules);
        s.modules.appendChild(el("li", "frg-empty", "불러오는 중…"));
        fetch(apiModuleTypes, { headers: { "Accept": "application/json" } })
            .then(function (r) { if (!r.ok) { throw new Error("http " + r.status); } return r.json(); })
            .then(function (list) {
                moduleList = Array.isArray(list) ? list : [];
                moduleByCode = {};
                moduleList.forEach(function (m) { moduleByCode[m.moduleTypeCode] = m; });
                renderModules();
            })
            .catch(function () {
                clear(s.modules);
                s.modules.appendChild(el("li", "frg-empty", "모듈을 불러오지 못했습니다."));
            });
    }

    function currentArchetype() {
        var screen = studio.getScreen();
        return screen ? screen.archetypeCode : null;
    }

    function defaultSlot(archetype) {
        if (!slotMeta || !archetype) { return null; }
        var def = studio.getDefinitionJson();
        var slots = def && def.slots ? def.slots : {};
        var ordered = slotMeta.orderedSlots(archetype);
        for (var i = 0; i < ordered.length; i++) {
            var arr = slots[ordered[i]];
            if (!Array.isArray(arr) || !arr.length) { return ordered[i]; }
        }
        return ordered.length ? ordered[0] : null;
    }

    // 검색어 매치(모듈명/코드/카테고리 부분일치, 대소문자 무시).
    function matchesSearch(m) {
        if (!searchTerm) { return true; }
        var hay = (String(m.moduleName || "") + " " + String(m.moduleTypeCode || "")
            + " " + String(m.categoryCode || "")).toLowerCase();
        return hay.indexOf(searchTerm) !== -1;
    }

    // 모듈 1건 → 팔레트 항목 li. 현재 아키타입에 배치 불가면 dim.
    function moduleItem(m, dim, slotKey) {
        var li = el("li", "frg-palette-item" + (dim ? " is-dim" : ""));
        li.setAttribute("data-code", m.moduleTypeCode); // 코드값 setAttribute(화이트리스트 식별자)
        if (slotKey) { li.setAttribute("data-slot", slotKey); }
        li.setAttribute("role", "button");
        li.tabIndex = 0;
        li.draggable = !dim; // P8: 캔버스 슬롯으로 드래그 배치
        if (dim) { li.title = "현재 아키타입에는 배치할 수 없습니다"; }
        else { li.title = "클릭 또는 캔버스 슬롯으로 드래그해서 배치"; }
        li.appendChild(el("span", "frg-item-name", m.moduleName));
        var badge = el("span", "frg-item-badge", m.categoryCode);
        badge.setAttribute("data-cat", String(m.categoryCode == null ? "" : m.categoryCode));
        li.appendChild(badge);
        return li;
    }

    function renderModules() {
        var s = ensureStructure();
        if (!s) { return; }
        clear(s.modules);
        var visible = moduleList.filter(matchesSearch);
        if (!visible.length) {
            s.modules.appendChild(el("li", "frg-empty",
                moduleList.length ? "검색 결과가 없습니다." : "등록된 모듈이 없습니다."));
            return;
        }
        var archetype = currentArchetype();
        // §17: 자유 배치는 슬롯 필터가 없다 — 델파이 팔레트처럼 카탈로그 전체를 항상 보여 준다.
        var slotRules = (slotMeta && archetype && !slotMeta.isFreeCanvas(archetype))
            ? slotMeta.WHITELIST[archetype] : null;

        // 화면이 선택되면 캔버스와 동일한 슬롯/영역 순서로 보인다.
        // 같은 모듈을 모든 슬롯에 반복하지 않고, 선택한 영역 후보만 펼친다.
        if (slotRules) {
            if (!activeSlotKey || !slotRules[activeSlotKey]) {
                activeSlotKey = defaultSlot(archetype);
            }
            var rendered = 0;
            slotMeta.orderedSlots(archetype).forEach(function (slotKey) {
                var rule = slotRules[slotKey];
                var compatible = visible.filter(function (m) {
                    return rule.cats.indexOf(String(m.categoryCode || "")) !== -1;
                });
                var selected = slotKey === activeSlotKey;
                var group = el("li", "frg-palette-group frg-palette-slot-group" + (selected ? " is-active" : ""));
                var toggle = el("button", "frg-palette-slot-toggle");
                toggle.type = "button";
                toggle.setAttribute("data-slot-select", slotKey);
                toggle.appendChild(el("span", "frg-palette-slot-label", slotMeta.label(slotKey)));
                toggle.appendChild(el("span", "frg-palette-slot-key", slotKey));
                group.appendChild(toggle);
                s.modules.appendChild(group);
                if (!selected) { return; }
                if (!compatible.length) {
                    s.modules.appendChild(el("li", "frg-empty", searchTerm
                        ? "이 영역의 검색 결과가 없습니다."
                        : "이 영역에 배치할 수 있는 모듈이 없습니다."));
                    return;
                }
                compatible.forEach(function (m) {
                    s.modules.appendChild(moduleItem(m, false, slotKey));
                    rendered++;
                });
            });
            if (!rendered) {
                s.modules.appendChild(el("li", "frg-empty", "현재 화면 영역에 배치 가능한 모듈이 없습니다."));
            }
            return;
        }

        // 화면 미선택 시에는 기존 카테고리 분류로 전체 카탈로그를 안내한다.
        var groups = {};
        visible.forEach(function (m) {
            var cat = String(m.categoryCode || "기타");
            (groups[cat] = groups[cat] || []).push(m);
        });
        var cats = [];
        CATEGORY_ORDER.forEach(function (c) { if (groups[c]) { cats.push(c); } });
        Object.keys(groups).forEach(function (c) { if (cats.indexOf(c) === -1) { cats.push(c); } });

        cats.forEach(function (cat) {
            s.modules.appendChild(el("li", "frg-palette-group", CATEGORY_LABELS[cat] || cat));
            groups[cat].forEach(function (m) {
                var dim = !!archetype
                    && slotMeta
                    && !slotMeta.placeableSlots(archetype, m.categoryCode).length;
                s.modules.appendChild(moduleItem(m, dim, null));
            });
        });
    }

    // ---------- PROP_SCHEMA 조회 + default props 초기화 ----------
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

    // PROP_SCHEMA_JSON fields[].default 로 초기 props 구성(§3.2). 스키마에 정의된 key 만.
    function initialProps(schema) {
        var out = {};
        if (schema && Array.isArray(schema.fields)) {
            schema.fields.forEach(function (f) {
                if (f && f.key != null && Object.prototype.hasOwnProperty.call(f, "default")) {
                    out[f.key] = f.default;
                }
            });
        }
        return out;
    }

    // ---------- instanceId 생성(§1.3): <moduleTypeCode 소문자 camel>_<seq> ----------
    function toCamel(moduleTypeCode) {
        var parts = String(moduleTypeCode).toLowerCase().split("_");
        return parts.map(function (p, i) {
            if (i === 0) { return p; }
            return p.charAt(0).toUpperCase() + p.slice(1);
        }).join("");
    }

    function collectAllIds(def) {
        var ids = {};
        if (def && def.slots && typeof def.slots === "object") {
            Object.keys(def.slots).forEach(function (sk) {
                var arr = def.slots[sk];
                if (Array.isArray(arr)) {
                    arr.forEach(function (inst) {
                        if (inst && inst.instanceId != null) { ids[inst.instanceId] = true; }
                    });
                }
            });
        }
        return ids;
    }

    function nextInstanceId(def, moduleTypeCode) {
        var base = toCamel(moduleTypeCode);
        var existing = collectAllIds(def);
        var seq = 1;
        while (existing[base + "_" + seq]) { seq += 1; } // 문서 전체 유일성 보장
        return base + "_" + seq;
    }

    // ---------- def 얕은 복제(불변 갱신용) ----------
    function cloneDef(def) {
        var copy = {};
        Object.keys(def || {}).forEach(function (k) { copy[k] = def[k]; });
        var slots = {};
        var src = (def && def.slots) || {};
        Object.keys(src).forEach(function (sk) {
            slots[sk] = Array.isArray(src[sk]) ? src[sk].slice() : src[sk];
        });
        copy.slots = slots;
        return copy;
    }

    // ---------- 배치(팔레트 클릭 진입점) ----------
    function onModuleClick(moduleTypeCode, preferredSlot) {
        setMessage("");
        var def = studio.getDefinitionJson();
        var archetype = currentArchetype();
        if (!def || !archetype) {
            setMessage("먼저 화면을 선택하세요.");
            return;
        }
        var m = moduleByCode[moduleTypeCode];
        if (!m) { return; }

        // §17 자유 배치: 슬롯이 하나뿐이라 "어디에" 대신 "어디쯤에"를 묻는다 —
        // 델파이처럼 팔레트를 고른 뒤 캔버스에서 놓을 자리를 클릭한다.
        if (slotMeta && slotMeta.isFreeCanvas(archetype)) {
            var canvasBridge = bridge();
            if (canvasBridge && typeof canvasBridge.setPending === "function") {
                canvasBridge.setPending({
                    moduleTypeCode: moduleTypeCode,
                    moduleName: m.moduleName,
                    slots: ["canvasArea"]
                });
                setMessage("캔버스에서 놓을 자리를 클릭하세요. (Esc 취소)");
            } else {
                placeInto(moduleTypeCode, "canvasArea");
            }
            return;
        }

        var slots = slotMeta ? slotMeta.placeableSlots(archetype, m.categoryCode) : [];
        if (!slots.length) {
            setMessage("'" + m.moduleName + "'(" + m.categoryCode + ") 은(는) 이 아키타입(" +
                archetype + ")에 배치할 수 없습니다.");
            return;
        }
        if (preferredSlot && slots.indexOf(preferredSlot) !== -1) {
            placeInto(moduleTypeCode, preferredSlot);
            return;
        }
        if (slots.length === 1) {
            placeInto(moduleTypeCode, slots[0]);
            return;
        }
        // 후보 여러 개 → 캔버스 슬롯 하이라이트(브리지 pending). 슬롯 클릭으로 확정, Esc 취소.
        var b = bridge();
        if (b && typeof b.setPending === "function") {
            b.setPending({
                moduleTypeCode: moduleTypeCode,
                moduleName: m.moduleName,
                slots: slots
            });
            setMessage("캔버스에서 하이라이트된 슬롯을 클릭해 배치하세요. (Esc 취소)");
        } else {
            // 브리지 미로드 폴백: 첫 후보에 배치.
            placeInto(moduleTypeCode, slots[0]);
        }
    }

    /**
     * §17.2 자유 배치 좌표 초기화(P13). 배치 시점에 X/Y/W/H 4키를 **항상** 채운다 —
     * 산출 게이트가 4키 전부를 요구하므로(부분 결여 = CSS 0바이트), 여기서 비워두면
     * 놓자마자 "캔버스와 생성물이 다른" 상태가 된다.
     */
    function seedCanvasProps(props, moduleTypeCode, at) {
        if (!slotMeta) { return props; }
        var size = slotMeta.defaultItemSize(moduleTypeCode);
        var x = (at && at.x != null) ? at.x : 24;
        var y = (at && at.y != null) ? at.y : 24;
        props.layoutXPx = slotMeta.clampCanvas("layoutXPx", x);
        props.layoutYPx = slotMeta.clampCanvas("layoutYPx", y);
        props.layoutWPx = slotMeta.clampCanvas("layoutWPx", size.w);
        props.layoutHPx = slotMeta.clampCanvas("layoutHPx", size.h);
        // §17.8: 패널 위에 놓았으면 그 패널의 자식으로 만든다(좌표는 이미 패널 기준).
        if (at && at.parentId) { props.layoutParentId = String(at.parentId); }
        return props;
    }

    // ---------- 슬롯에 실제 인스턴스 추가(cardinality 확인 포함) ----------
    function placeInto(moduleTypeCode, slotKey, at) {
        var def = studio.getDefinitionJson();
        var archetype = currentArchetype();
        var wl = (slotMeta && archetype) ? slotMeta.WHITELIST[archetype] : null;
        if (!def || !wl || !wl[slotKey]) {
            setMessage("배치할 수 없는 슬롯입니다.");
            return;
        }
        var rule = wl[slotKey];
        var existingArr = (def.slots && Array.isArray(def.slots[slotKey])) ? def.slots[slotKey] : [];

        // cardinality: 단일 슬롯(0..1 / 1..1)에 이미 1개 있으면 교체 확인(번들 confirm 위젯).
        if (!rule.multi && existingArr.length >= 1) {
            confirmDialog("모듈 교체",
                slotLabel(slotKey) + " 슬롯에는 1개만 배치할 수 있습니다. 기존 모듈을 교체할까요?",
                function (ok) {
                    if (!ok) { setMessage("배치가 취소되었습니다."); return; }
                    doPlace(moduleTypeCode, slotKey, true, at);
                });
            return;
        }
        return doPlace(moduleTypeCode, slotKey, false, at);
    }

    // 배치 1건 실행. 프리셋(applyPreset)의 순차 체이닝을 위해 Promise 를 돌려준다(P7-5).
    function doPlace(moduleTypeCode, slotKey, replace, at) {
        return fetchSchema(moduleTypeCode)
            .then(function (schema) {
                var latestDef = studio.getDefinitionJson(); // 비동기 사이 갱신 대비 최신본 재취득
                if (!latestDef) { return; }
                var newDef = cloneDef(latestDef);
                if (!Array.isArray(newDef.slots[slotKey])) { newDef.slots[slotKey] = []; }
                var arr = newDef.slots[slotKey].slice();
                var instanceId = nextInstanceId(newDef, moduleTypeCode);
                var props = initialProps(schema);
                if (slotMeta && slotMeta.isFreeCanvas(currentArchetype())) {
                    props = seedCanvasProps(props, moduleTypeCode, at);
                }
                var inst = {
                    instanceId: instanceId,
                    moduleTypeCode: moduleTypeCode,
                    props: props
                };
                if (replace) { arr = [inst]; } // 단일 슬롯 교체
                else { arr.push(inst); }
                newDef.slots[slotKey] = arr;

                studio.updateDefinitionJson(newDef, { reason: "palette" });
                setMessage("'" + ((moduleByCode[moduleTypeCode] || {}).moduleName || moduleTypeCode)
                    + "' 을(를) " + slotLabel(slotKey) + "에 배치했습니다.");
                selectInstance(instanceId);
            })
            .catch(function () {
                setMessage("모듈 스키마를 불러오지 못해 배치에 실패했습니다.");
            });
    }

    // ---------- 슬롯 "+ 모듈 추가" 선택 모달(P7-2) ----------
    var chooserBackdrop = null;

    function closeChooser() {
        if (chooserBackdrop && chooserBackdrop.parentNode) {
            chooserBackdrop.parentNode.removeChild(chooserBackdrop);
        }
        chooserBackdrop = null;
    }

    function openChooser(slotKey) {
        // 모달을 열기보다 팔레트에서 해당 영역을 즉시 펼친다.
        if (focusSlot(slotKey)) { return; }
        var archetype = currentArchetype();
        var wl = (slotMeta && archetype) ? slotMeta.WHITELIST[archetype] : null;
        if (!wl || !wl[slotKey]) { return; }
        closeChooser();

        var eligible = moduleList.filter(function (m) {
            return wl[slotKey].cats.indexOf(m.categoryCode) !== -1;
        });

        var backdrop = el("div", "frg-modal-backdrop");
        var modal = el("div", "frg-modal");

        var head = el("div", "frg-modal-head");
        head.appendChild(el("span", null, slotLabel(slotKey) + "에 모듈 추가"));
        var close = el("button", "frg-modal-close", "×");
        close.type = "button";
        close.setAttribute("data-chooser-close", "1");
        head.appendChild(close);
        modal.appendChild(head);

        var body = el("div", "frg-modal-body");
        if (!eligible.length) {
            body.appendChild(el("p", "frg-empty", "이 슬롯에 배치할 수 있는 모듈이 없습니다."));
        } else {
            var ul = el("ul", "frg-chooser-list");
            eligible.forEach(function (m) {
                var li = el("li", "frg-palette-item frg-chooser-item");
                li.setAttribute("data-choose", m.moduleTypeCode);
                li.setAttribute("role", "button");
                li.tabIndex = 0;
                li.appendChild(el("span", "frg-item-name", m.moduleName));
                var badge = el("span", "frg-item-badge", m.categoryCode);
                badge.setAttribute("data-cat", String(m.categoryCode == null ? "" : m.categoryCode));
                li.appendChild(badge);
                ul.appendChild(li);
            });
            body.appendChild(ul);
        }
        modal.appendChild(body);
        backdrop.appendChild(modal);

        backdrop.addEventListener("click", function (e) {
            var t = e.target;
            if (t === backdrop || (t.getAttribute && t.getAttribute("data-chooser-close"))) {
                closeChooser();
                return;
            }
            var item = t.closest ? t.closest("[data-choose]") : null;
            if (item) {
                var code = item.getAttribute("data-choose");
                closeChooser();
                placeInto(code, slotKey);
            }
        });
        document.addEventListener("keydown", function onEsc(e) {
            if (e.key === "Escape") {
                closeChooser();
                document.removeEventListener("keydown", onEsc);
            }
        });

        document.body.appendChild(backdrop);
        chooserBackdrop = backdrop;
    }

    // ---------- 기본 구성 프리셋(P7-5) ----------
    // 새 화면 만들기 "기본 구성으로 시작" 체크 시, 화면 유형에 맞는 기본 모듈을 순차 배치한다.
    // 배치는 일반 배치 경로(doPlace)와 동일 — props 는 PROP_SCHEMA default, undo 로 개별 회수 가능.
    var PRESETS = {
        MGMT_LIST_DETAIL: [
            ["searchArea", "SEARCH_FILTER_BAR"],
            ["listToolbar", "TOOLBAR"],
            ["listArea", "TABLE_VIEW"],
            ["detailBasic", "DETAIL_BASIC"]
        ],
        SIMPLE_LIST: [
            ["searchArea", "SEARCH_FILTER_BAR"],
            ["listToolbar", "TOOLBAR"],
            ["listArea", "TABLE_VIEW"]
        ],
        DUAL_LAYOUT: [
            ["leftArea", "LAYOUT_FRAME"],
            ["rightArea", "LAYOUT_FRAME"]
        ],
        POPUP: [
            ["popupBody", "POPUP_FORM"]
        ],
        DASHBOARD: [
            ["widgetArea", "BAR_CHART"],
            ["widgetArea", "SEMICIRCLE_CHART"]
        ],
        // §17 자유 배치: 좌표까지 프리셋으로 준다(3번째 원소 = 놓을 자리).
        FREE_CANVAS: [
            ["canvasArea", "LABEL", { x: 24, y: 24 }],
            ["canvasArea", "TABLE_VIEW", { x: 24, y: 72 }],
            ["canvasArea", "BUTTON", { x: 24, y: 456 }]
        ]
    };

    function applyPreset(archetype) {
        var seq = PRESETS[archetype] || [];
        var chain = Promise.resolve();
        seq.forEach(function (pair) {
            chain = chain.then(function () {
                return doPlace(pair[1], pair[0], false, pair[2]);
            });
        });
        return chain.then(function () {
            if (seq.length) {
                setMessage("기본 구성을 배치했습니다. 캔버스에서 모듈을 클릭해 속성을 조정하세요.");
            }
        });
    }

    // ---------- 선택(캔버스 하이라이트 + 속성패널 연동, 브리지 위임) ----------
    function selectInstance(instanceId) {
        var b = bridge();
        if (b && typeof b.setSelected === "function") {
            b.setSelected(instanceId);
        } else {
            var p = props();
            if (p && typeof p.selectInstance === "function") { p.selectInstance(instanceId); }
        }
    }

    // 캔버스의 "+ 모듈 추가" 또는 영역 헤더 클릭이 이 함수를 호출한다.
    // 모달을 띄우지 않고 같은 화면에서 후보를 확인·배치하게 해 조립 흐름을 유지한다.
    function focusSlot(slotKey) {
        var archetype = currentArchetype();
        var rules = (slotMeta && archetype) ? slotMeta.WHITELIST[archetype] : null;
        if (!rules || !rules[slotKey]) { return false; }
        activeSlotKey = slotKey;
        renderModules();
        setMessage(slotLabel(slotKey) + "에 넣을 모듈을 선택하세요.");
        var p = pane();
        var toggle = p ? p.querySelector('.frg-palette-slot-toggle[data-slot-select="' + slotKey + '"]') : null;
        if (toggle && toggle.scrollIntoView) { toggle.scrollIntoView({ block: "nearest" }); }
        return true;
    }

    // ---------- 이벤트 바인딩(위임) ----------
    function bind() {
        var s = ensureStructure();
        if (!s) { return; }

        s.search.addEventListener("input", function () {
            searchTerm = s.search.value.trim().toLowerCase();
            renderModules();
        });

        s.modules.addEventListener("click", function (e) {
            var toggle = e.target.closest ? e.target.closest("[data-slot-select]") : null;
            if (toggle) {
                focusSlot(toggle.getAttribute("data-slot-select"));
                return;
            }
            var li = e.target.closest ? e.target.closest(".frg-palette-item") : null;
            if (li) { onModuleClick(li.getAttribute("data-code"), li.getAttribute("data-slot")); }
        });
        s.modules.addEventListener("keydown", function (e) {
            if (e.key !== "Enter" && e.key !== " ") { return; }
            var li = e.target.closest ? e.target.closest(".frg-palette-item") : null;
            if (li) { e.preventDefault(); onModuleClick(li.getAttribute("data-code"), li.getAttribute("data-slot")); }
        });

        // P8: 팔레트 → 캔버스 슬롯 드래그 배치. dragstart 에서 배치 가능 슬롯을 pending 으로
        // 하이라이트하고, 드롭 확정은 캔버스(iframe)가 MSG place(moduleTypeCode 동봉)로 보낸다.
        // dragend 는 대기 해제만(드롭 성공 시엔 place 가 먼저 처리됨 — 경합은 코드 동봉으로 무해).
        s.modules.addEventListener("dragstart", function (e) {
            var li = e.target.closest ? e.target.closest(".frg-palette-item") : null;
            if (!li || li.classList.contains("is-dim")) {
                e.preventDefault();
                return;
            }
            var code = li.getAttribute("data-code");
            var m = moduleByCode[code];
            var archetype = currentArchetype();
            if (!m || !archetype || !studio.getDefinitionJson()) {
                e.preventDefault();
                setMessage("먼저 화면을 선택하세요.");
                return;
            }
            var slots = slotMeta ? slotMeta.placeableSlots(archetype, m.categoryCode) : [];
            if (!slots.length) {
                e.preventDefault();
                return;
            }
            try {
                e.dataTransfer.effectAllowed = "copy";
                e.dataTransfer.setData("text/plain", String(code));
            } catch (ex) { /* 무시 */ }
            var b = bridge();
            if (b && typeof b.setPending === "function") {
                b.setPending({ moduleTypeCode: code, moduleName: m.moduleName, slots: slots });
                setMessage("하이라이트된 슬롯 위에 놓으세요. (Esc 취소)");
            }
        });
        s.modules.addEventListener("dragend", function () {
            var b = bridge();
            if (b && typeof b.setPending === "function") { b.setPending(null); }
            setMessage("");
        });
    }

    // ---------- 오케스트레이터 결선 ----------
    function init() {
        if (!studio) { return; }
        ensureStructure();
        bind();
        loadModules();

        studio.onDefinitionChanged(function (def, meta) {
            if (meta && meta.reason === "screenLoaded") {
                setMessage("");
                closeChooser();
                activeSlotKey = defaultSlot(currentArchetype());
                renderModules(); // 아키타입이 바뀌면 dim 상태 재계산
            }
        });
    }

    // ---------- 공개 API(브리지/속성패널/테스트) ----------
    mod.selectInstance = selectInstance;
    mod.placeInto = placeInto;     // 브리지: 캔버스 슬롯 클릭 배치 확정
    mod.openChooser = openChooser; // 브리지: 슬롯 "+ 모듈 추가"
    mod.focusSlot = focusSlot;     // 캔버스/컴포넌트 트리에서 특정 영역으로 이동
    mod.applyPreset = applyPreset; // studioApp: 새 화면 기본 구성(P7-5)
    mod.getModule = function (code) { return moduleByCode[code] || null; };

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})(window.JWorks_JSForgeAdminStudioPalette);
