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
window.JWorks_JSForgeAdminStudioLayerPanel = window.JWorks_JSForgeAdminStudioLayerPanel || {};
(function (mod) {
    "use strict";
    if (mod.__defined) { return; }
    mod.__defined = true;

    var studio = window.JWorks_JSForgeAdminStudio;
    var slotMeta = window.JWorks_JSForgeAdminStudioSlotMeta;

    function bridge() { return window.JWorks_JSForgeAdminStudioPreviewBridge; }
    function listEl() { return document.getElementById("frg-layer-list"); }
    function panelEl() { return document.getElementById("frg-pane-layers"); }

    /**
     * 사람이 붙인 이름(있으면). 없으면 null — 호출측이 타입을 이름 자리에 쓴다.
     * 이름과 타입을 **둘 다** 그리면 이름 없는 부품이 "TOOLBAR TOOLBAR"로 두 번 나오고,
     * 좁은 패널에서 그 중복이 이름을 밀어내 잘리게 만든다.
     */
    function customName(inst) {
        var p = (inst && inst.props) || {};
        var candidates = [p.title, p.text, p.label, p.placeholder];
        for (var i = 0; i < candidates.length; i++) {
            var v = candidates[i];
            if (typeof v === "string" && v.trim() !== "") { return v.trim(); }
        }
        return null;
    }

    function displayName(inst) {
        return customName(inst) || String((inst && inst.moduleTypeCode) || "?");
    }

    function zOf(inst) {
        var raw = inst && inst.props ? inst.props.layoutZ : null;
        var n = Number(raw);
        return (raw == null || !isFinite(n)) ? null : n;
    }

    // ---------- 끌어놓기(DOM 판정) ----------

    var dragId = null;   // 끌고 있는 instanceId

    function clearDropMarks() {
        var ul = listEl();
        if (!ul) { return; }
        for (var i = 0; i < ul.childNodes.length; i++) {
            var n = ul.childNodes[i];
            if (n && n.className) {
                n.className = n.className
                    .replace(/\s*is-drop-(before|after|into)/g, "");
            }
        }
    }

    /**
     * 커서 위치로 모드를 정한다 — 행 위쪽 30% = 앞에, 아래쪽 30% = 뒤에,
     * 가운데 40% = 그 안으로(컨테이너일 때만). 캔버스의 "패널에 끌어넣기"와 같은 감각이다.
     */
    function dropModeAt(row, clientY, isContainer) {
        var rect = (typeof row.getBoundingClientRect === "function")
            ? row.getBoundingClientRect() : null;
        if (!rect || !rect.height) { return isContainer ? "into" : "before"; }
        var ratio = (clientY - rect.top) / rect.height;
        if (isContainer && ratio > 0.3 && ratio < 0.7) { return "into"; }
        return ratio < 0.5 ? "before" : "after";
    }

    function attachDrag(row, id, isContainer) {
        row.draggable = true;

        row.addEventListener("dragstart", function (e) {
            dragId = id;
            row.className += " is-dragging";
            if (e.dataTransfer) {
                e.dataTransfer.effectAllowed = "move";
                // 일부 브라우저는 데이터가 없으면 드래그를 시작하지 않는다.
                try { e.dataTransfer.setData("text/plain", id); } catch (ignored) { /* 무시 */ }
            }
        });

        row.addEventListener("dragend", function () {
            dragId = null;
            row.className = row.className.replace(/\s*is-dragging/g, "");
            clearDropMarks();
        });

        row.addEventListener("dragover", function (e) {
            if (!dragId || dragId === id) { return; }
            var mode = dropModeAt(row, e.clientY, isContainer);
            var def = studio ? studio.getDefinitionJson() : null;
            var items = canvasItems(def);
            var newParent = (mode === "into") ? id : parentIdOf(items, id);
            if (!canDrop(items, dragId, newParent)) { return; }  // 금지면 드롭 자체를 안 받는다
            if (e.preventDefault) { e.preventDefault(); }
            if (e.dataTransfer) { e.dataTransfer.dropEffect = "move"; }
            clearDropMarks();
            row.className += " is-drop-" + mode;
        });

        row.addEventListener("dragleave", function () {
            row.className = row.className.replace(/\s*is-drop-(before|after|into)/g, "");
        });

        row.addEventListener("drop", function (e) {
            if (e.preventDefault) { e.preventDefault(); }
            if (e.stopPropagation) { e.stopPropagation(); }
            var dragged = dragId;
            clearDropMarks();
            dragId = null;
            if (!dragged || dragged === id) { return; }
            applyMove(dragged, id, dropModeAt(row, e.clientY, isContainer));
        });
    }

    /** 목록 빈 곳에 놓으면 루트로 빼낸다(패널 밖으로 꺼내는 유일한 방법). */
    function attachRootDrop(ul) {
        ul.addEventListener("dragover", function (e) {
            if (!dragId) { return; }
            if (e.preventDefault) { e.preventDefault(); }
        });
        ul.addEventListener("drop", function (e) {
            if (!dragId) { return; }
            if (e.preventDefault) { e.preventDefault(); }
            var dragged = dragId;
            dragId = null;
            clearDropMarks();
            moveToRoot(dragged);
        });
    }

    /** 루트 맨 위로 빼낸다. 부모가 이미 없으면 아무 일도 하지 않는다. */
    function moveToRoot(draggedId) {
        if (!studio) { return false; }
        var def = studio.getDefinitionJson();
        if (!def) { return false; }
        var items = canvasItems(def);
        if (parentIdOf(items, draggedId) == null) { return false; }

        var ordered = siblingsOf(items, null)
            .map(function (inst, i) { return { inst: inst, i: i, z: zOf(inst) || 0 }; })
            .sort(function (a, b) { return (b.z - a.z) || (a.i - b.i); })
            .map(function (x) { return x.inst; });
        ordered.unshift(findItem(items, draggedId));

        var zById = {};
        var n = ordered.length;
        ordered.forEach(function (inst, idx) {
            var z = n - 1 - idx;
            if (slotMeta) { z = slotMeta.clampCanvas("layoutZ", z); }
            zById[String(inst.instanceId)] = z;
        });
        studio.updateDefinitionJson(
            withZ(def, zById, String(draggedId), null), { reason: "layerPanel" });
        return true;
    }

    // ---------- 행 만들기 ----------

    function makeRow(inst, depth, selectedId, isContainer) {
        var id = String(inst.instanceId);
        var row = document.createElement("li");
        row.className = "frg-layer-row";
        if (String(selectedId) === id) { row.className += " is-selected"; }
        row.setAttribute("data-instance-id", id);
        row.setAttribute("data-container", isContainer ? "1" : "0");
        row.style.paddingLeft = (8 + depth * 14) + "px";
        attachDrag(row, id, isContainer);

        var btn = document.createElement("button");
        btn.type = "button";
        btn.className = "frg-layer-name";
        btn.setAttribute("aria-pressed", String(selectedId) === id ? "true" : "false");

        var icon = document.createElement("span");
        icon.className = "frg-layer-icon";
        icon.textContent = isContainer ? "▣" : "▪";  // 컨테이너인지 한눈에
        btn.appendChild(icon);

        var custom = customName(inst);
        var name = document.createElement("span");
        name.className = "frg-layer-label";
        name.textContent = custom || String(inst.moduleTypeCode || "?");  // 🔒 textContent
        name.title = String(inst.moduleTypeCode || "");
        btn.appendChild(name);

        // 타입 배지는 **이름이 따로 있을 때만** 단다. 이름이 곧 타입인 부품에 또 붙이면
        // 같은 글자가 두 번 나오면서 좁은 패널에서 이름을 잘라먹는다.
        if (custom) {
            var type = document.createElement("span");
            type.className = "frg-layer-type";
            type.textContent = String(inst.moduleTypeCode || "");
            btn.appendChild(type);
        }

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
        actions.appendChild(makeActionButton("▲", "한 칸 앞으로", function () { moveLayer(id, +1); }));
        actions.appendChild(makeActionButton("▼", "한 칸 뒤로", function () { moveLayer(id, -1); }));
        actions.appendChild(makeActionButton("×", "삭제", function () { requestDelete(id); }));
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

    // ---------- 계층 질의(끌어놓기 판정에 쓰는 순수 함수들) ----------

    function findItem(items, id) {
        var found = null;
        items.forEach(function (i) {
            if (i && String(i.instanceId) === String(id)) { found = i; }
        });
        return found;
    }

    function parentIdOf(items, id) {
        var inst = findItem(items, id);
        return inst && slotMeta ? slotMeta.parentIdOf(inst) : null;
    }

    /** 형제 목록(같은 부모). parentId 가 null 이면 루트들. */
    function siblingsOf(items, parentId) {
        return items.filter(function (i) {
            if (!i || i.instanceId == null) { return false; }
            var pid = slotMeta ? slotMeta.parentIdOf(i) : null;
            return String(pid) === String(parentId);
        });
    }

    /** 루트를 1로 센 깊이. 부모 사슬이 끊기면 1(루트 수렴 — 산출측과 같은 안전측 규칙). */
    function depthOf(items, id) {
        var d = 1;
        var seen = {};
        var cursor = parentIdOf(items, id);
        while (cursor && !seen[cursor]) {
            seen[cursor] = 1;
            d++;
            cursor = parentIdOf(items, cursor);
        }
        return d;
    }

    /** 이 인스턴스를 뿌리로 하는 하위 트리의 높이(자기만 있으면 1). */
    function subtreeHeight(items, id) {
        var kids = siblingsOf(items, String(id));
        if (kids.length === 0) { return 1; }
        var max = 1;
        kids.forEach(function (k) {
            var h = 1 + subtreeHeight(items, k.instanceId);
            if (h > max) { max = h; }
        });
        return max;
    }

    function isDescendant(items, candidateId, ancestorId) {
        var seen = {};
        var cursor = parentIdOf(items, candidateId);
        while (cursor && !seen[cursor]) {
            if (String(cursor) === String(ancestorId)) { return true; }
            seen[cursor] = 1;
            cursor = parentIdOf(items, cursor);
        }
        return false;
    }

    /**
     * 끌어놓기가 허용되는가. 캔버스·저장 검증(§17.8)과 **같은 규칙**을 미리 본다 —
     * 여기서 막지 않으면 저장 시점에 400 으로 튕기거나 산출에서 루트로 수렴해 버린다.
     */
    function canDrop(items, draggedId, newParentId) {
        if (!draggedId) { return false; }
        if (newParentId == null) { return true; }                       // 루트로 빼는 건 언제나 가능
        if (String(newParentId) === String(draggedId)) { return false; } // 자기 자신
        if (isDescendant(items, newParentId, draggedId)) { return false; } // 자기 자손 안으로
        var parent = findItem(items, newParentId);
        if (!parent || !slotMeta || !slotMeta.isContainer(parent.moduleTypeCode)) { return false; }
        var max = slotMeta.MAX_CANVAS_DEPTH || 3;
        // 새 부모의 깊이 + 끌고 온 가지의 높이가 한계를 넘으면 안 된다(자식까지 함께 내려간다).
        return depthOf(items, newParentId) + subtreeHeight(items, draggedId) <= max;
    }

    /**
     * 끌어놓기 적용. DOM 판정과 분리된 **순수 진입점**이라 그대로 시험할 수 있다.
     *
     * @param mode "before" | "after" — target 의 형제가 되어 그 앞/뒤에 놓인다(위=앞)
     *             "into"            — target 의 자식이 되어 맨 위에 놓인다
     * @return 바뀌었으면 true
     */
    function applyMove(draggedId, targetId, mode) {
        if (!studio) { return false; }
        var def = studio.getDefinitionJson();
        if (!def) { return false; }
        var items = canvasItems(def);
        if (!findItem(items, draggedId) || String(draggedId) === String(targetId)) { return false; }

        var newParentId = (mode === "into") ? String(targetId) : parentIdOf(items, targetId);
        if (mode !== "into" && !findItem(items, targetId)) { return false; }
        if (!canDrop(items, draggedId, newParentId)) { return false; }

        // 새 부모의 형제열을 화면 순서(위=앞)로 만든 뒤, 끌고 온 것을 원하는 자리에 끼운다.
        var group = siblingsOf(items, newParentId).filter(function (i) {
            return String(i.instanceId) !== String(draggedId);
        });
        var ordered = group
            .map(function (inst, i) { return { inst: inst, i: i, z: zOf(inst) || 0 }; })
            .sort(function (a, b) { return (b.z - a.z) || (a.i - b.i); })
            .map(function (x) { return x.inst; });

        var at = 0;
        if (mode !== "into") {
            ordered.forEach(function (inst, idx) {
                if (String(inst.instanceId) === String(targetId)) {
                    at = (mode === "before") ? idx : idx + 1;
                }
            });
        }
        ordered.splice(at, 0, findItem(items, draggedId));

        var zById = {};
        var n = ordered.length;
        ordered.forEach(function (inst, idx) {
            var z = n - 1 - idx;
            if (slotMeta) { z = slotMeta.clampCanvas("layoutZ", z); }
            zById[String(inst.instanceId)] = z;
        });

        studio.updateDefinitionJson(
            withZ(def, zById, String(draggedId), newParentId), { reason: "layerPanel" });
        return true;
    }

    /**
     * 목록에서 **한 칸** 위/아래로 옮긴다(포토샵 레이어 목록과 같은 감각).
     *
     * 예전에는 `max(형제 z) + 1` 로 "맨 앞으로 보내기"를 했다. 그래서 누를 때마다 z 가
     * 36 → 37 → 38 … 로 **끝없이 커지고** 값 사이가 벌어져 숫자가 의미를 잃었다(§17.2 상한
     * 999 에 닿기 전에 이미 못 읽는 값이 된다). 지금은 이웃과 자리를 바꾼 뒤 **형제 전체에
     * 0..n-1 을 다시 매긴다** — 값이 촘촘하게 유지되고 목록 순서와 언제나 일치한다.
     *
     * @param direction +1 = 한 칸 앞으로(위) / -1 = 한 칸 뒤로(아래)
     */
    function moveLayer(instanceId, direction) {
        var b = bridge();
        if (b && typeof b.requestLayerMove === "function") {
            b.requestLayerMove(instanceId, direction);
        }
    }

    /**
     * 형제들의 layoutZ 를 한 번에 새로 매긴 정의를 만든다(원본 불변 — undo 1건).
     * 건드리지 않는 인스턴스는 **같은 객체를 그대로** 재사용해 불필요한 diff 를 만들지 않는다.
     */
    function withZ(def, zById, reparentId, newParentId) {
        var copy = {};
        Object.keys(def || {}).forEach(function (k) { copy[k] = def[k]; });
        var slots = {};
        var src = (def && def.slots) || {};
        Object.keys(src).forEach(function (sk) {
            var arr = src[sk];
            if (!Array.isArray(arr)) { slots[sk] = arr; return; }
            slots[sk] = arr.map(function (inst) {
                if (!inst || inst.instanceId == null) { return inst; }
                var id = String(inst.instanceId);
                var nextZ = zById[id];
                var reparent = (reparentId != null && id === String(reparentId));
                if (nextZ === undefined && !reparent) { return inst; }
                if (!reparent && zOf(inst) === nextZ) { return inst; }

                var nextInst = {};
                Object.keys(inst).forEach(function (ik) { nextInst[ik] = inst[ik]; });
                var nextProps = {};
                Object.keys(inst.props || {}).forEach(function (pk) { nextProps[pk] = inst.props[pk]; });
                if (nextZ !== undefined) { nextProps.layoutZ = nextZ; }
                if (reparent) {
                    // 루트로 뺄 때는 키를 **지운다** — 빈 문자열을 남기면 "부모 없음"과
                    // "부모가 빈 값"이 갈라져 저장 검증(§17.8)이 헷갈린다.
                    if (newParentId == null) { delete nextProps.layoutParentId; }
                    else { nextProps.layoutParentId = String(newParentId); }
                }
                nextInst.props = nextProps;
                return nextInst;
            });
        });
        copy.slots = slots;
        return copy;
    }

    /**
     * 삭제 확인은 **브리지가 단일 소유**한다(previewBridge.requestDelete).
     * 여기서 또 물으면 확인 창이 두 번 뜨고, 문구가 갈라진다.
     */
    function requestDelete(instanceId) {
        var b = bridge();
        if (b && typeof b.requestDelete === "function") { b.requestDelete(instanceId); }
    }

    // ---------- 렌더 ----------

    /**
     * 레이어 목록의 순서 = **앞에 있는 것이 위**(포토샵/델파이 관례).
     * §17.10 으로 layoutZ 의 의미가 "형제 사이의 순서"로 확정됐으므로 정렬도 형제 단위다.
     * z 가 같거나 없으면 원래 배열 순서를 유지한다(안정 정렬 — 목록이 제멋대로 흔들리지 않게).
     */
    function sortSiblingsByZ(nodes) {
        return nodes
            .map(function (node, i) { return { node: node, i: i, z: zOf(node.inst) || 0 }; })
            .sort(function (a, b) { return (b.z - a.z) || (a.i - b.i); })
            .map(function (x) { return x.node; });
    }

    function renderCanvasTree(ul, items, selectedId) {
        var roots = slotMeta ? slotMeta.buildCanvasTree(items) : [];
        var count = 0;
        (function walk(nodes, depth) {
            sortSiblingsByZ(nodes).forEach(function (node) {
                var isContainer = slotMeta && slotMeta.isContainer(node.inst.moduleTypeCode);
                ul.appendChild(makeRow(node.inst, depth, selectedId, isContainer));
                count++;
                walk(node.children, depth + 1);
            });
        })(roots, 0);
        return count;
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
        var ul = listEl();
        if (ul) { attachRootDrop(ul); }   // 목록 배선은 한 번만(행은 매 렌더마다 새로 만든다)
        studio.onDefinitionChanged(function () { render(); });
        var b = bridge();
        if (b && typeof b.onSelectionChanged === "function") {
            b.onSelectionChanged(function () { render(); });
        }
        render();
    }

    mod.render = render;        // 테스트/디버그 진입점
    mod.applyMove = applyMove;  // 끌어놓기 모델 변경(DOM 판정과 분리 — 그대로 시험 가능)
    mod.moveToRoot = moveToRoot;
    mod.canDrop = function (draggedId, newParentId) {
        var def = studio ? studio.getDefinitionJson() : null;
        return canDrop(canvasItems(def), draggedId, newParentId);
    };

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})(window.JWorks_JSForgeAdminStudioLayerPanel);
