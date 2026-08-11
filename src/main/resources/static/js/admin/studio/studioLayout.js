/* ===============================================================================================
Name : studioLayout.js
Description : 3-pane 스튜디오의 도킹 패널 폭 조절 컨트롤러.
             - 팔레트/속성 패널 경계를 드래그 또는 키보드로 조절.
             - 사용자가 정한 폭을 브라우저에만 저장(localStorage)해 다음 편집에도 유지.
             - 좁은 화면의 세로 레이아웃에서는 CSS가 리사이저를 숨기므로 동작하지 않는다.
=============================================================================================== */
window.JWorks_JSForgeAdminStudioLayout = window.JWorks_JSForgeAdminStudioLayout || {};
(function (mod) {
    "use strict";
    if (mod.__defined) { return; }
    mod.__defined = true;

    var KEY_PALETTE = "jforge.studio.paletteWidth";
    var KEY_PROPS = "jforge.studio.propsWidth";
    var MIN_PALETTE = 200;
    var MAX_PALETTE = 440;
    var MIN_PROPS = 260;
    var MAX_PROPS = 520;
    var drag = null;

    function grid() { return document.querySelector(".frg-studio-grid"); }

    function clamp(value, min, max) {
        return Math.min(max, Math.max(min, Math.round(value)));
    }

    function stored(key, fallback, min, max) {
        try {
            var value = Number(window.localStorage.getItem(key));
            return isFinite(value) ? clamp(value, min, max) : fallback;
        } catch (e) { return fallback; }
    }

    function setWidth(kind, width, persist) {
        var g = grid();
        if (!g) { return; }
        var isPalette = kind === "palette";
        var value = clamp(width,
            isPalette ? MIN_PALETTE : MIN_PROPS,
            isPalette ? MAX_PALETTE : MAX_PROPS);
        g.style.setProperty(isPalette ? "--frg-palette-width" : "--frg-props-width", value + "px");
        if (persist) {
            try { window.localStorage.setItem(isPalette ? KEY_PALETTE : KEY_PROPS, String(value)); } catch (e) { /* 무시 */ }
        }
    }

    function resetWidth(kind) {
        var g = grid();
        if (!g) { return; }
        var isPalette = kind === "palette";
        g.style.removeProperty(isPalette ? "--frg-palette-width" : "--frg-props-width");
        try { window.localStorage.removeItem(isPalette ? KEY_PALETTE : KEY_PROPS); } catch (e) { /* 무시 */ }
    }

    function kindOf(resizer) { return resizer && resizer.id === "frg-resize-palette" ? "palette" : "props"; }

    function startDrag(resizer, clientX) {
        if (!resizer || window.matchMedia("(max-width: 1100px)").matches) { return; }
        var g = grid();
        if (!g) { return; }
        var kind = kindOf(resizer);
        var pane = document.getElementById(kind === "palette" ? "frg-pane-palette" : "frg-pane-props");
        if (!pane) { return; }
        drag = { kind: kind, startX: clientX, startWidth: pane.getBoundingClientRect().width };
        document.body.classList.add("frg-pane-resizing");
    }

    function moveDrag(clientX) {
        if (!drag) { return; }
        var delta = clientX - drag.startX;
        // 오른쪽 패널은 마우스를 왼쪽으로 옮길수록 넓어진다.
        setWidth(drag.kind, drag.startWidth + (drag.kind === "palette" ? delta : -delta), false);
    }

    function endDrag() {
        if (!drag) { return; }
        var kind = drag.kind;
        drag = null;
        document.body.classList.remove("frg-pane-resizing");
        var pane = document.getElementById(kind === "palette" ? "frg-pane-palette" : "frg-pane-props");
        if (pane) { setWidth(kind, pane.getBoundingClientRect().width, true); }
    }

    function bindResizer(resizer) {
        if (!resizer) { return; }
        resizer.addEventListener("pointerdown", function (e) {
            startDrag(resizer, e.clientX);
            if (drag) {
                e.preventDefault();
                try { resizer.setPointerCapture(e.pointerId); } catch (ex) { /* 무시 */ }
            }
        });
        resizer.addEventListener("dblclick", function () { resetWidth(kindOf(resizer)); });
        resizer.addEventListener("keydown", function (e) {
            var kind = kindOf(resizer);
            var pane = document.getElementById(kind === "palette" ? "frg-pane-palette" : "frg-pane-props");
            if (!pane || (e.key !== "ArrowLeft" && e.key !== "ArrowRight")) { return; }
            e.preventDefault();
            var shift = e.shiftKey ? 40 : 12;
            var direction = e.key === "ArrowRight" ? 1 : -1;
            var factor = kind === "palette" ? direction : -direction;
            setWidth(kind, pane.getBoundingClientRect().width + factor * shift, true);
        });
    }

    function init() {
        setWidth("palette", stored(KEY_PALETTE, 260, MIN_PALETTE, MAX_PALETTE), false);
        setWidth("props", stored(KEY_PROPS, 340, MIN_PROPS, MAX_PROPS), false);
        bindResizer(document.getElementById("frg-resize-palette"));
        bindResizer(document.getElementById("frg-resize-props"));
        document.addEventListener("pointermove", function (e) { moveDrag(e.clientX); });
        document.addEventListener("pointerup", endDrag);
    }

    mod.reset = function () { resetWidth("palette"); resetWidth("props"); };

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})(window.JWorks_JSForgeAdminStudioLayout);
