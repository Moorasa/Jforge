/* ===============================================================================================
Name : previewBridge.js
Description : 부모(스튜디오) 측 캔버스 브리지 (P3-6, P7-2 확장). 오케스트레이터 허브
             (JWorks_JSForgeAdminStudio)의 공개 API 만으로 중앙 인터랙티브 캔버스 iframe 과 결선.
             캔버스에서 일어나는 선택/삭제/순서이동 + (P7-2) 슬롯 배치 확정/모듈 추가 요청을
             DEFINITION_JSON 갱신 또는 팔레트 호출로 반영한다.

★ 결합 방식 ★
 studioApp.js(허브) 공개 API + 팔레트 공개 API(placeInto/openChooser)로만 결선한다.
 선택 상태(selectedId)와 배치 대기 상태(pending)는 이 브리지가 단일 소유한다.

P7-2 push 페이로드: { type, definition, selectedId, archetype, pending }
 - archetype: 현재 화면의 archetypeCode(캔버스 슬롯 스캐폴드용)
 - pending: { moduleTypeCode, moduleName, slots:[slotKey...] } | null — 팔레트발 배치 대기

🔒 iframe 보안(스키마_DEFINITION_JSON.md §5)
 - postMessage 송신 targetOrigin = location.origin("*" 금지), 수신 event.origin 검증.
 - 삭제/순서이동/배치는 구조(instanceId/slotKey/index)만 다루며 props 자유문자열은 무가공 보존.
 - 서버 최종 구조검증은 저장 시 다시 수행되므로, 브리지 편집은 UX 편의 계층이다.
=============================================================================================== */
window.JWorks_JSForgeAdminStudioPreviewBridge = window.JWorks_JSForgeAdminStudioPreviewBridge || {};
(function (mod) {
    "use strict";
    if (mod.__defined) { return; }
    mod.__defined = true;

    var MSG_DEFINITION = "frg:preview:definition";        // 부모 → iframe
    var MSG_SELECT = "frg:preview:select";                // iframe → 부모
    var MSG_DELETE = "frg:preview:delete";                // iframe → 부모
    var MSG_DUPLICATE = "frg:preview:duplicate";          // iframe → 부모
    var MSG_REORDER = "frg:preview:reorder";              // iframe → 부모
    var MSG_READY = "frg:preview:ready";                  // iframe → 부모
    var MSG_PLACE = "frg:preview:place";                  // iframe → 부모 : 배치 대기 → 슬롯 확정(클릭/드롭)
    var MSG_ADD = "frg:preview:addRequest";               // iframe → 부모 : 슬롯 "+ 모듈 추가"
    var MSG_CANCEL_PENDING = "frg:preview:cancelPending"; // iframe → 부모 : 배치 대기 취소
    var MSG_RESIZE = "frg:preview:resize";                // iframe → 부모 : 크기 조절 확정(P8, §13)
    var MSG_CANVAS_LAYOUT = "frg:preview:canvasLayout";   // iframe → 부모 : 자유 배치 좌표 확정(P13, §17.2)

    var studio = window.JWorks_JSForgeAdminStudio;
    var slotMeta = window.JWorks_JSForgeAdminStudioSlotMeta;

    // palette 는 로드 순서 상 나중에 준비될 수 있어 사용 시점에 조회한다.
    function palette() { return window.JWorks_JSForgeAdminStudioPalette; }
    function props() { return window.JWorks_JSForgeAdminStudioProps; }

    var selectedId = null; // 캔버스/속성패널이 공유하는 단일 선택 상태
    var pending = null;    // 팔레트발 배치 대기 { moduleTypeCode, moduleName, slots:[...] } | null
    var selectionListeners = []; // 선택 변경 구독자(레이어 패널 등) — 선택 소유권은 여전히 브리지다

    // 선택이 바뀔 때마다 호출. 구독자 예외가 브리지를 멈추지 않게 개별 try 로 감싼다.
    function notifySelection() {
        for (var i = 0; i < selectionListeners.length; i++) {
            try { selectionListeners[i](selectedId); } catch (e) { /* 구독자 오류 무시 */ }
        }
    }

    function frame() { return document.getElementById("frg-preview-frame"); }

    // 현재 DEFINITION_JSON + 선택/아키타입/배치대기 상태를 iframe 으로 송신(🔒 targetOrigin 고정).
    function pushDefinition(def) {
        var f = frame();
        if (!f || !f.contentWindow) { return; }
        var screen = (studio && typeof studio.getScreen === "function") ? studio.getScreen() : null;
        try {
            f.contentWindow.postMessage(
                {
                    type: MSG_DEFINITION,
                    definition: def == null ? null : def,
                    selectedId: selectedId,
                    archetype: screen ? screen.archetypeCode : null,
                    pending: pending
                },
                window.location.origin
            );
        } catch (e) { /* iframe 미준비 등은 ready 시 재송신 */ }
    }

    function repush() {
        if (studio && typeof studio.getDefinitionJson === "function") {
            pushDefinition(studio.getDefinitionJson());
        }
    }

    // 선택 코디네이터: 캔버스 하이라이트(재push) + 속성패널 로드.
    function setSelected(id) {
        selectedId = (id == null ? null : String(id));
        var p = props();
        if (p && typeof p.selectInstance === "function") { p.selectInstance(selectedId); }
        notifySelection();
        repush();
    }

    // 배치 대기 상태 설정/해제(팔레트가 호출). null 이면 해제.
    function setPending(next) {
        pending = next || null;
        repush();
    }

    // ---------- 편집 반영(구조만; props 무가공 보존) ----------
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

    /**
     * §17.8: 컨테이너를 지우면 그 안의 자손도 함께 지운다. 남겨두면 부모를 잃은 인스턴스가 되어
     * 저장 검증(§17.8 부모 무결성)에서 막힌다 — 지운 자리에 유령이 남지 않게 한 번에 정리한다.
     * (실수는 undo 로 되돌린다.)
     */
    function withDescendants(def, instanceId) {
        var ids = {};
        ids[String(instanceId)] = 1;
        var items = (def && def.slots && Array.isArray(def.slots.canvasArea)) ? def.slots.canvasArea : [];
        var grew = true;
        while (grew) {
            grew = false;
            items.forEach(function (inst) {
                if (!inst || inst.instanceId == null) { return; }
                var id = String(inst.instanceId);
                if (ids[id]) { return; }
                var parentId = slotMeta ? slotMeta.parentIdOf(inst) : null;
                if (parentId && ids[parentId]) { ids[id] = 1; grew = true; }
            });
        }
        return ids;
    }

    // ---------- 레이어 순서(구조 편집 — 삭제와 같이 브리지가 소유) ----------

    function canvasItemsOf(def) {
        return (def && def.slots && Array.isArray(def.slots.canvasArea)) ? def.slots.canvasArea : [];
    }

    function zOf(inst) {
        var raw = inst && inst.props ? inst.props.layoutZ : null;
        var n = Number(raw);
        return (raw == null || !isFinite(n)) ? null : n;
    }

    /**
     * 목록/캔버스에서 **한 칸** 앞뒤로 옮긴다. 캔버스 ▲▼ 와 레이어 패널 ▲▼ 가 같은 진입점이다.
     *
     * 예전에는 두 곳이 각자 `max + 1` 로 "맨 앞으로"를 했다. 그래서 누를 때마다 z 가 끝없이
     * 커졌고(36→37→38…), 캔버스 쪽은 형제가 아니라 **캔버스 전체**에서 최댓값을 찾아 더 빨리
     * 불어났다. §17.10 으로 z 는 형제 범위에서만 의미가 있으므로 형제만 보고, 이웃과 자리를
     * 바꾼 뒤 **0..n-1 로 다시 매긴다** — 값이 촘촘하고 목록 순서와 항상 일치한다.
     *
     * @param direction +1 = 한 칸 앞으로 / -1 = 한 칸 뒤로
     */
    function requestLayerMove(instanceId, direction) {
        if (!studio) { return false; }
        var def = studio.getDefinitionJson();
        if (!def) { return false; }
        var items = canvasItemsOf(def);

        var target = null;
        items.forEach(function (i) {
            if (i && String(i.instanceId) === String(instanceId)) { target = i; }
        });
        if (!target) { return false; }

        var parentId = slotMeta ? slotMeta.parentIdOf(target) : null;
        var siblings = items.filter(function (i) {
            if (!i || i.instanceId == null) { return false; }
            var pid = slotMeta ? slotMeta.parentIdOf(i) : null;
            return String(pid) === String(parentId);
        });
        if (siblings.length < 2) { return false; }

        var ordered = siblings
            .map(function (inst, i) { return { inst: inst, i: i, z: zOf(inst) || 0 }; })
            .sort(function (a, b) { return (b.z - a.z) || (a.i - b.i); })
            .map(function (x) { return x.inst; });

        var at = -1;
        ordered.forEach(function (inst, idx) {
            if (String(inst.instanceId) === String(instanceId)) { at = idx; }
        });
        var to = at - direction;
        if (at < 0 || to < 0 || to >= ordered.length) { return false; }  // 끝 — 빈 undo 방지

        var swap = ordered[at];
        ordered[at] = ordered[to];
        ordered[to] = swap;

        var n = ordered.length;
        var zById = {};
        ordered.forEach(function (inst, idx) {
            var z = n - 1 - idx;
            if (slotMeta) { z = slotMeta.clampCanvas("layoutZ", z); }
            zById[String(inst.instanceId)] = z;
        });

        var newDef = cloneDef(def);
        Object.keys(newDef.slots).forEach(function (sk) {
            var arr = newDef.slots[sk];
            if (!Array.isArray(arr)) { return; }
            newDef.slots[sk] = arr.map(function (inst) {
                if (!inst || inst.instanceId == null) { return inst; }
                var next = zById[String(inst.instanceId)];
                if (next === undefined || zOf(inst) === next) { return inst; }
                var nextInst = {};
                Object.keys(inst).forEach(function (k) { nextInst[k] = inst[k]; });
                nextInst.props = copyProps(inst.props);
                nextInst.props.layoutZ = next;
                return nextInst;
            });
        });
        studio.updateDefinitionJson(newDef, { reason: "layerOrder" });
        return true;
    }

    /**
     * 삭제 확인은 **이 브리지가 단일 소유**한다(선택 상태와 같은 원칙).
     * 캔버스 ×, 속성패널 삭제, 레이어 패널 × 가 전부 이 진입점으로 들어오므로
     * 확인 문구·규칙이 자리마다 갈리지 않는다. 컨테이너면 자손 동반 삭제(§17.8)를 알린다.
     */
    function requestDelete(instanceId) {
        if (!studio) { return; }
        var def = studio.getDefinitionJson();
        var inst = null;
        Object.keys((def && def.slots) || {}).forEach(function (sk) {
            var arr = def.slots[sk];
            if (!Array.isArray(arr)) { return; }
            arr.forEach(function (i) {
                if (i && String(i.instanceId) === String(instanceId)) { inst = i; }
            });
        });
        if (!inst) { return; }

        var isContainer = slotMeta && slotMeta.isContainer(inst.moduleTypeCode);
        var name = instanceLabel(inst);
        var text = isContainer
            ? "\"" + name + "\" 를 지우면 그 안에 담긴 부품도 함께 지워집니다. 계속할까요?"
            : "\"" + name + "\" 를 화면에서 지울까요?";

        askConfirm("모듈 삭제", text, function (ok) {
            if (ok) { deleteInstance(String(instanceId)); }
        });
    }

    function instanceLabel(inst) {
        var p = (inst && inst.props) || {};
        var candidates = [p.title, p.text, p.label];
        for (var i = 0; i < candidates.length; i++) {
            if (typeof candidates[i] === "string" && candidates[i].trim() !== "") {
                return candidates[i].trim();
            }
        }
        return String(inst.moduleTypeCode || "이 모듈");
    }

    // 번들 위젯 우선(P7 규약: window.confirm 직접 호출 금지, 미로드 시에만 폴백).
    function askConfirm(title, text, cb) {
        if (window.JWORKS_JSConfirm && typeof window.JWORKS_JSConfirm.start === "function") {
            window.JWORKS_JSConfirm.start(String(title), String(text), function (result) {
                cb(result === true || result === "true" || result === 1);
            });
            return;
        }
        cb(window.confirm(String(text)));
    }

    /** 실제 삭제(확인 이후). 자손 동반 삭제 규칙은 withDescendants 가 담당한다. */
    function deleteInstance(instanceId) {
        if (!studio) { return; }
        var def = studio.getDefinitionJson();
        if (!def) { return; }
        var newDef = cloneDef(def);
        var doomed = withDescendants(def, instanceId);
        var removed = false;
        Object.keys(newDef.slots).forEach(function (sk) {
            var arr = newDef.slots[sk];
            if (!Array.isArray(arr)) { return; }
            newDef.slots[sk] = arr.filter(function (inst) {
                if (inst && doomed[String(inst.instanceId)]) { removed = true; return false; }
                return true;
            });
        });
        if (!removed) { return; }
        if (String(selectedId) === String(instanceId)) { selectedId = null; notifySelection(); }
        studio.updateDefinitionJson(newDef, { reason: "preview" });
    }

    function copyProps(props) {
        try { return JSON.parse(JSON.stringify(props || {})); }
        catch (e) {
            var copy = {};
            Object.keys(props || {}).forEach(function (key) { copy[key] = props[key]; });
            return copy;
        }
    }

    // 구조 편집(복제/크기)에서도 선택 모듈의 선언형 메타(data/events)를 잃지 않는다.
    function copyInstance(instance) {
        try { return JSON.parse(JSON.stringify(instance || {})); }
        catch (e) {
            var copy = {};
            Object.keys(instance || {}).forEach(function (key) { copy[key] = instance[key]; });
            copy.props = copyProps(instance && instance.props);
            if (instance && instance.data) { copy.data = copyProps(instance.data); }
            if (instance && instance.events) { copy.events = copyProps(instance.events); }
            return copy;
        }
    }

    function duplicateId(def, sourceId) {
        var used = {};
        Object.keys((def && def.slots) || {}).forEach(function (slotKey) {
            var instances = def.slots[slotKey];
            if (!Array.isArray(instances)) { return; }
            instances.forEach(function (instance) {
                if (instance && instance.instanceId != null) { used[String(instance.instanceId)] = true; }
            });
        });
        var base = String(sourceId || "module").replace(/[^A-Za-z0-9_-]/g, "");
        if (!/^[A-Za-z]/.test(base)) { base = "module" + base; }
        base = base.slice(0, 56) || "module";
        var number = 2;
        var candidate = base + "Copy" + number;
        while (used[candidate]) {
            number += 1;
            candidate = base.slice(0, Math.max(1, 64 - ("Copy" + number).length)) + "Copy" + number;
        }
        return candidate;
    }

    function duplicateInstance(instanceId) {
        if (!studio) { return; }
        var def = studio.getDefinitionJson();
        var screen = (typeof studio.getScreen === "function") ? studio.getScreen() : null;
        if (!def || !def.slots || !screen || !slotMeta || !slotMeta.WHITELIST) { return; }

        var source = null;
        var sourceSlot = null;
        var sourceIndex = -1;
        Object.keys(def.slots).some(function (slotKey) {
            var instances = def.slots[slotKey];
            if (!Array.isArray(instances)) { return false; }
            var index = instances.findIndex(function (instance) {
                return instance && String(instance.instanceId) === String(instanceId);
            });
            if (index < 0) { return false; }
            source = instances[index];
            sourceSlot = slotKey;
            sourceIndex = index;
            return true;
        });
        var rules = slotMeta.WHITELIST[screen.archetypeCode];
        if (!source || !rules || !rules[sourceSlot] || !rules[sourceSlot].multi) { return; }

        var newDef = cloneDef(def);
        var clone = copyInstance(source);
        clone.instanceId = duplicateId(def, source.instanceId);
        newDef.slots[sourceSlot] = newDef.slots[sourceSlot].slice();
        newDef.slots[sourceSlot].splice(sourceIndex + 1, 0, clone);
        studio.updateDefinitionJson(newDef, { reason: "preview" });
        setSelected(clone.instanceId);
    }

    /**
     * §13 크기 조절 반영(P8): 해당 인스턴스 props 에 layoutWidthPct/layoutHeightPx 만 병합한다.
     * 기존 props 키는 무가공 보존, 값은 서버 게이트와 동일 범위로 클램프(숫자만).
     */
    function resizeInstance(instanceId, widthPct, heightPx) {
        if (!studio) { return; }
        var def = studio.getDefinitionJson();
        if (!def) { return; }
        var newDef = cloneDef(def);
        var changed = false;
        Object.keys(newDef.slots).forEach(function (sk) {
            var arr = newDef.slots[sk];
            if (!Array.isArray(arr)) { return; }
            newDef.slots[sk] = arr.map(function (inst) {
                if (!inst || String(inst.instanceId) !== String(instanceId)) { return inst; }
                var props = {};
                Object.keys(inst.props || {}).forEach(function (k) { props[k] = inst.props[k]; });
                if (typeof widthPct === "number" && isFinite(widthPct)) {
                    props.layoutWidthPct = Math.min(100, Math.max(10, Math.round(widthPct)));
                }
                if (typeof heightPx === "number" && isFinite(heightPx)) {
                    props.layoutHeightPx = Math.min(2000, Math.max(40, Math.round(heightPx)));
                }
                changed = true;
                var resized = copyInstance(inst);
                resized.props = props;
                return resized;
            });
        });
        if (changed) { studio.updateDefinitionJson(newDef, { reason: "resize" }); }
    }

    /**
     * §17.2 자유 배치 좌표 반영(P13). 전달된 키만 병합한다 — 기존 props 는 무가공 보존하고
     * 값은 서버 게이트와 같은 범위로 클램프한다(숫자만). 이동/리사이즈/z-order 공용 경로.
     */
    function applyCanvasLayout(instanceId, data) {
        if (!studio || !slotMeta) { return; }
        var def = studio.getDefinitionJson();
        if (!def) { return; }
        var mapping = { x: "layoutXPx", y: "layoutYPx", w: "layoutWPx", h: "layoutHPx", z: "layoutZ" };
        var newDef = cloneDef(def);
        var changed = false;
        Object.keys(newDef.slots).forEach(function (sk) {
            var arr = newDef.slots[sk];
            if (!Array.isArray(arr)) { return; }
            newDef.slots[sk] = arr.map(function (inst) {
                if (!inst || String(inst.instanceId) !== String(instanceId)) { return inst; }
                var props = {};
                Object.keys(inst.props || {}).forEach(function (k) { props[k] = inst.props[k]; });
                Object.keys(mapping).forEach(function (field) {
                    if (data[field] == null) { return; }
                    var value = slotMeta.clampCanvas(mapping[field], data[field]);
                    if (value != null) { props[mapping[field]] = value; }
                });
                // §17.8 부모 변경: 키가 실려 온 경우에만 반영한다(null = 캔버스 루트로 빼내기).
                if (Object.prototype.hasOwnProperty.call(data, "parentId")) {
                    if (data.parentId) { props.layoutParentId = String(data.parentId); }
                    else { delete props.layoutParentId; }
                }
                changed = true;
                var moved = copyInstance(inst);
                moved.props = props;
                return moved;
            });
        });
        if (changed) { studio.updateDefinitionJson(newDef, { reason: "resize" }); }
    }

    function reorderInstance(slotKey, fromIndex, toIndex) {
        if (!studio) { return; }
        var def = studio.getDefinitionJson();
        if (!def || !def.slots || !Array.isArray(def.slots[slotKey])) { return; }
        var arr = def.slots[slotKey];
        if (fromIndex < 0 || fromIndex >= arr.length || toIndex < 0 || toIndex >= arr.length || fromIndex === toIndex) { return; }
        var newDef = cloneDef(def);
        var newArr = newDef.slots[slotKey].slice();
        var moved = newArr.splice(fromIndex, 1)[0]; // from 위치 제거
        newArr.splice(toIndex, 0, moved);           // to 위치 삽입
        newDef.slots[slotKey] = newArr;
        studio.updateDefinitionJson(newDef, { reason: "preview" });
    }

    // ---------- iframe → 부모 수신(🔒 origin 검증) ----------
    function onMessage(event) {
        if (event.origin !== window.location.origin) { return; }
        var data = event.data;
        if (!data) { return; }
        if (data.type === MSG_READY) {
            repush();
            return;
        }
        if (data.type === MSG_SELECT && data.instanceId != null) {
            setSelected(String(data.instanceId));
            return;
        }
        if (data.type === MSG_DELETE && data.instanceId != null) {
            requestDelete(String(data.instanceId));
            return;
        }
        if (data.type === MSG_DUPLICATE && data.instanceId != null) {
            duplicateInstance(String(data.instanceId));
            return;
        }
        if (data.type === MSG_REORDER && data.slotKey != null) {
            reorderInstance(String(data.slotKey), Number(data.fromIndex), Number(data.toIndex));
            return;
        }
        // P7-2/P8: 배치 대기 → 캔버스 슬롯 클릭/드롭으로 확정.
        // 모듈 코드는 메시지 동봉값 우선(드롭 직후 dragend 가 pending 을 먼저 지우는 경합 방지),
        // 없으면 저장된 pending 폴백.
        if (data.type === MSG_PLACE && data.slotKey != null) {
            var pal = palette();
            var code = (data.moduleTypeCode != null && String(data.moduleTypeCode) !== "")
                ? String(data.moduleTypeCode)
                : (pending ? pending.moduleTypeCode : null);
            pending = null; // 확정 즉시 대기 해제(placeInto 실패 시에도 대기 잔류 방지)
            if (code && pal && typeof pal.placeInto === "function") {
                // P13: 자유 배치는 놓은 좌표를 함께 넘긴다(§17.2 4키를 배치 시점에 채운다).
                var at = null;
                if (data.x != null && data.y != null) {
                    at = { x: Number(data.x), y: Number(data.y), parentId: data.parentId || null };
                }
                pal.placeInto(code, String(data.slotKey), at);
            } else {
                repush();
            }
            return;
        }
        // P13: 자유 배치 좌표 확정 — §17.2 layout 키 병합.
        if (data.type === MSG_CANVAS_LAYOUT && data.instanceId != null) {
            // 한 칸 이동 의도(zStep)는 형제 판정·재번호가 필요하므로 전용 경로로 보낸다.
            if (data.zStep === 1 || data.zStep === -1) {
                requestLayerMove(String(data.instanceId), data.zStep);
                return;
            }
            applyCanvasLayout(String(data.instanceId), data);
            return;
        }
        // P8: 크기 조절 확정 — §13 layout props 병합.
        if (data.type === MSG_RESIZE && data.instanceId != null) {
            resizeInstance(String(data.instanceId), data.widthPct, data.heightPx);
            return;
        }
        // P7-2: 슬롯 "+ 모듈 추가" → 팔레트 모듈 선택 UI.
        if (data.type === MSG_ADD && data.slotKey != null) {
            var pal2 = palette();
            if (pal2 && typeof pal2.openChooser === "function") {
                pal2.openChooser(String(data.slotKey));
            }
            return;
        }
        if (data.type === MSG_CANCEL_PENDING) {
            setPending(null);
            return;
        }
    }

    function init() {
        if (!studio || typeof studio.onDefinitionChanged !== "function") { return; }
        window.addEventListener("message", onMessage, false);
        studio.onDefinitionChanged(function (def, meta) {
            // 화면이 새로 로드되면 선택/배치 대기 초기화.
            if (meta && meta.reason === "screenLoaded") { selectedId = null; pending = null; notifySelection(); }
            pushDefinition(def);
        });
        // 부모 문서 쪽에서도 Esc 로 배치 대기 취소(포커스가 부모에 있을 때).
        document.addEventListener("keydown", function (e) {
            if (e.key === "Escape" && pending) { setPending(null); }
        });
    }

    // ---------- 공개 API(팔레트/속성패널이 호출) ----------
    mod.setSelected = setSelected;
    mod.setPending = setPending;
    mod.getSelected = function () { return selectedId; };
    /**
     * 선택 변경 구독(레이어 패널 등 부모측 뷰 전용). 선택 **소유권은 여전히 이 브리지**이며
     * 구독자는 읽기만 한다 — 바꾸려면 setSelected 를 호출한다(단일 진입점 유지).
     */
    mod.onSelectionChanged = function (fn) {
        if (typeof fn === "function") { selectionListeners.push(fn); }
    };
    mod.requestDelete = requestDelete;  // 🔒 확인을 포함한 단일 삭제 진입점(캔버스·속성·레이어 공통)
    mod.requestDuplicate = duplicateInstance;
    mod.requestLayerMove = requestLayerMove; // 한 칸 앞뒤 이동(캔버스·레이어 패널 공통)

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})(window.JWorks_JSForgeAdminStudioPreviewBridge);
