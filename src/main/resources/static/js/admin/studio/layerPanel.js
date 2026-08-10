/* ===============================================================================================
Name : layerPanel.js
Description : 레이어 패널 — 화면에 배치된 인스턴스의 **계층 구조**를 목록으로 보여준다.

★ 왜 필요한가 ★
 §17.8 로 캔버스에 중첩(패널 안 패널)이 생겼는데, 그 구조가 캔버스 그림에만 존재했다.
 3단까지 쌓으면 뭐가 누구 자식인지 눈으로 못 쫓는다. 이 패널이 트리를 드러낸다.

★ 결합 방식(P7 규약) ★
 - 구조 원본은 studioApp 의 DEFINITION_JSON 하나. 이 패널은 **자체 상태를 갖지 않는다**.
 - 트리 계산은 slotMeta.buildCanvasTree 재사용 — 캔버스/생성기와 **같은 규칙**(루트 수렴 포함).
 - 선택은 previewBridge 가 단일 소유. 여기서는 setSelected 로 요청하고 onSelectionChanged 로 받는다.
 - 삭제는 bridge.requestDelete(자손 동반 삭제 규칙을 그대로 탄다).

★ 앞으로/뒤로 ★
 계약 §17.10 으로 layoutZ 의 의미가 "**형제 사이의 순서**"로 확정됐다(컨테이너가 자기 레이어
 공간을 열므로 자식 z 는 부모 밖으로 새지 않는다). 그래서 이 버튼은 **같은 부모를 가진 형제**
 중에서만 최대/최소를 찾아 조정한다 — 화면 전체를 뒤지지 않는다.

🔒 XSS: DOM 은 createElement + textContent 로만 만든다(innerHTML 금지 — 자유문자열이 라벨로 들어온다).
=============================================================================================== */
window.MagicIAM_JSForgeAdminStudioLayerPanel = window.MagicIAM_JSForgeAdminStudioLayerPanel || {};
(function (mod) {
    "use strict";
    if (mod.__defined) { return; }
    mod.__defined = true;

    var studio = window.MagicIAM_JSForgeAdminStudio;
    var slotMeta = window.MagicIAM_JSForgeAdminStudioSlotMeta;

    function bridge() { return window.MagicIAM_JSForgeAdminStudioPreviewBridge; }
    function listEl() { return document.getElementById("frg-layer-list"); }
    function panelEl() { return document.getElementById("frg-pane-layers"); }

    /** 인스턴스의 표시 이름 — 사람이 붙인 이름이 있으면 그것, 없으면 모듈 타입. */
    function displayName(inst) {
        var p = (inst && inst.props) || {};
        var candidates = [p.title, p.text, p.label, p.placeholder];
        for (var i = 0; i < candidates.length; i++) {
            var v = candidates[i];
            if (typeof v === "string" && v.trim() !== "") {
                return v.trim().length > 24 ? v.trim().slice(0, 24) + "…" : v.trim();
            }
        }
        return String((inst && inst.moduleTypeCode) || "?");
    }

    function zOf(inst) {
        var raw = inst && inst.props ? inst.props.layoutZ : null;
        var n = Number(raw);
        return (raw == null || !isFinite(n)) ? null : n;
    }

    // ---------- 행 만들기 ----------

    function makeRow(inst, depth, selectedId, isContainer) {
        var id = String(inst.instanceId);
        var row = document.createElement("li");
        row.className = "frg-layer-row";
        if (String(selectedId) === id) { row.className += " is-selected"; }
        row.setAttribute("data-instance-id", id);
        row.style.paddingLeft = (8 + depth * 14) + "px";

        var btn = document.createElement("button");
        btn.type = "button";
        btn.className = "frg-layer-name";
        btn.setAttribute("aria-pressed", String(selectedId) === id ? "true" : "false");

        var icon = document.createElement("span");
        icon.className = "frg-layer-icon";
        icon.textContent = isContainer ? "▣" : "▪";  // 컨테이너인지 한눈에
        btn.appendChild(icon);

        var name = document.createElement("span");
        name.className = "frg-layer-label";
        name.textContent = displayName(inst);        // 🔒 textContent
        btn.appendChild(name);

        var type = document.createElement("span");
        type.className = "frg-layer-type";
        type.textContent = String(inst.moduleTypeCode || "");
        btn.appendChild(type);

        var z = zOf(inst);
        if (z !== null) {
            var zb = document.createElement("span");
            zb.className = "frg-layer-z";
            zb.textContent = "z" + z;
            zb.title = "형제 사이의 순서(§17.10)";
            btn.appendChild(zb);
        }

        btn.addEventListener("click", function () {
            var b = bridge();
            if (b && typeof b.setSelected === "function") { b.setSelected(id); }
        });
        row.appendChild(btn);

        var actions = document.createElement("span");
        actions.className = "frg-layer-actions";
        actions.appendChild(makeActionButton("▲", "앞으로 보내기", function () { bumpZ(id, +1); }));
        actions.appendChild(makeActionButton("▼", "뒤로 보내기", function () { bumpZ(id, -1); }));
        actions.appendChild(makeActionButton("×", "삭제", function () { requestDelete(id, inst); }));
        row.appendChild(actions);
        return row;
    }

    function makeActionButton(glyph, title, onClick) {
        var b = document.createElement("button");
        b.type = "button";
        b.className = "frg-layer-act";
        b.textContent = glyph;
        b.title = title;
        b.setAttribute("aria-label", title);
        b.addEventListener("click", function (e) {
            e.stopPropagation();   // 행 선택으로 번지지 않게
            onClick();
        });
        return b;
    }

    // ---------- 편집 ----------

    function canvasItems(def) {
        return (def && def.slots && Array.isArray(def.slots.canvasArea)) ? def.slots.canvasArea : [];
    }

    /**
     * 같은 부모를 가진 형제 중 맨 앞/맨 뒤로 보낸다. §17.10 이후 z 는 형제 범위에서만 의미가
     * 있으므로 형제만 훑는다. 값은 §17.2 유효범위(0~999)로 클램프한다 — 벗어나면 산출이
     * 0바이트가 되어 "조절했는데 파일엔 안 나가는" 상태가 된다.
     */
    function bumpZ(instanceId, direction) {
        if (!studio) { return; }
        var def = studio.getDefinitionJson();
        if (!def) { return; }
        var items = canvasItems(def);
        var target = null;
        items.forEach(function (i) { if (i && String(i.instanceId) === String(instanceId)) { target = i; } });
        if (!target) { return; }

        var parentId = slotMeta ? slotMeta.parentIdOf(target) : null;
        var siblingZ = [];
        items.forEach(function (i) {
            if (!i || String(i.instanceId) === String(instanceId)) { return; }
            var pid = slotMeta ? slotMeta.parentIdOf(i) : null;
            if (String(pid) !== String(parentId)) { return; }
            var z = zOf(i);
            if (z !== null) { siblingZ.push(z); }
        });

        var next;
        if (siblingZ.length === 0) {
            next = direction > 0 ? 1 : 0;
        } else if (direction > 0) {
            next = Math.max.apply(null, siblingZ) + 1;
        } else {
            next = Math.min.apply(null, siblingZ) - 1;
        }
        var clamped = slotMeta ? slotMeta.clampCanvas("layoutZ", next) : next;
        if (clamped === null) { return; }
        if (zOf(target) === clamped) { return; }   // 이미 끝이면 아무것도 하지 않는다(빈 undo 방지)

        var newDef = cloneWithProp(def, instanceId, "layoutZ", clamped);
        studio.updateDefinitionJson(newDef, { reason: "layerPanel" });
    }

    /** props 한 키만 바꾼 새 정의를 만든다(원본 불변 — undo 가 성립하려면 새 객체여야 한다). */
    function cloneWithProp(def, instanceId, key, value) {
        var copy = {};
        Object.keys(def || {}).forEach(function (k) { copy[k] = def[k]; });
        var slots = {};
        var src = (def && def.slots) || {};
        Object.keys(src).forEach(function (sk) {
            var arr = src[sk];
            if (!Array.isArray(arr)) { slots[sk] = arr; return; }
            slots[sk] = arr.map(function (inst) {
                if (!inst || String(inst.instanceId) !== String(instanceId)) { return inst; }
                var nextInst = {};
                Object.keys(inst).forEach(function (ik) { nextInst[ik] = inst[ik]; });
                var nextProps = {};
                Object.keys(inst.props || {}).forEach(function (pk) { nextProps[pk] = inst.props[pk]; });
                nextProps[key] = value;
                nextInst.props = nextProps;
                return nextInst;
            });
        });
        copy.slots = slots;
        return copy;
    }

    function requestDelete(instanceId, inst) {
        var b = bridge();
        if (!b || typeof b.requestDelete !== "function") { return; }
        var isContainer = slotMeta && slotMeta.isContainer(inst && inst.moduleTypeCode);
        var text = isContainer
            ? "\"" + displayName(inst) + "\" 를 지우면 그 안의 부품도 함께 지워집니다. 계속할까요?"
            : "\"" + displayName(inst) + "\" 를 지울까요?";
        confirmThen("부품 삭제", text, function (ok) {
            if (ok) { b.requestDelete(instanceId); }
        });
    }

    // 번들 위젯 우선(P7 규약: window.confirm 직접 호출 금지, 미로드시에만 폴백).
    function confirmThen(title, text, cb) {
        if (window.JWORKS_JSConfirm && typeof window.JWORKS_JSConfirm.start === "function") {
            window.JWORKS_JSConfirm.start(String(title), String(text), function (result) {
                cb(result === true || result === "true" || result === 1);
            });
            return;
        }
        cb(window.confirm(String(text)));
    }

    // ---------- 렌더 ----------

    function renderCanvasTree(ul, items, selectedId) {
        var roots = slotMeta ? slotMeta.buildCanvasTree(items) : [];
        (function walk(nodes, depth) {
            nodes.forEach(function (node) {
                var isContainer = slotMeta && slotMeta.isContainer(node.inst.moduleTypeCode);
                ul.appendChild(makeRow(node.inst, depth, selectedId, isContainer));
                walk(node.children, depth + 1);
            });
        })(roots, 0);
        return roots.length;
    }

    function renderSlotGroup(ul, slotKey, items, selectedId) {
        var head = document.createElement("li");
        head.className = "frg-layer-group";
        head.textContent = slotMeta ? slotMeta.label(slotKey) : slotKey;
        ul.appendChild(head);
        items.forEach(function (inst) {
            if (!inst || inst.instanceId == null) { return; }
            ul.appendChild(makeRow(inst, 1, selectedId, false));
        });
    }

    function render() {
        var ul = listEl();
        if (!ul) { return; }
        while (ul.firstChild) { ul.removeChild(ul.firstChild); }

        var def = studio && typeof studio.getDefinitionJson === "function"
            ? studio.getDefinitionJson() : null;
        var b = bridge();
        var selectedId = (b && typeof b.getSelected === "function") ? b.getSelected() : null;

        if (!def || !def.slots) {
            ul.appendChild(emptyRow("화면을 선택하세요"));
            return;
        }

        var archetype = def.archetype;
        var count = 0;
        if (slotMeta && slotMeta.isFreeCanvas(archetype)) {
            count = renderCanvasTree(ul, canvasItems(def), selectedId);
        } else {
            var slots = slotMeta ? slotMeta.orderedSlots(archetype) : Object.keys(def.slots);
            slots.forEach(function (slotKey) {
                var arr = def.slots[slotKey];
                if (!Array.isArray(arr) || arr.length === 0) { return; }
                renderSlotGroup(ul, slotKey, arr, selectedId);
                count += arr.length;
            });
        }
        if (count === 0) { ul.appendChild(emptyRow("배치된 부품이 없습니다")); }
    }

    function emptyRow(text) {
        var li = document.createElement("li");
        li.className = "frg-layer-empty";
        li.textContent = text;
        return li;
    }

    // ---------- 초기화 ----------

    function init() {
        if (!studio || typeof studio.onDefinitionChanged !== "function") { return; }
        if (!panelEl()) { return; }
        studio.onDefinitionChanged(function () { render(); });
        var b = bridge();
        if (b && typeof b.onSelectionChanged === "function") {
            b.onSelectionChanged(function () { render(); });
        }
        render();
    }

    mod.render = render; // 테스트/디버그 진입점

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})(window.MagicIAM_JSForgeAdminStudioLayerPanel);
