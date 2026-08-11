/* ===============================================================================================
Name : screenMgmt.js
Description : 화면 관리(이름변경/복제/삭제) 컨트롤러 (P7-4).
             상단바 "⋯" 버튼 → 팝오버 메뉴 → 각 액션 모달/확인창.
             studioApp(허브) 공개 API(getScreen/reloadScreens)로만 결선한다(허브 파일 수정 없음).

서버 API:
  이름변경  PUT    /api/screens/{id}            (ForgeScreenRequest — 현재 메타 + 새 이름)
  복제      POST   /api/screens/{id}/duplicate  ({screenName, stem})
  삭제      DELETE /api/screens/{id}            (STATUS_CODE='DELETED' 논리삭제)

XSS: 모든 표시 문자열은 createElement/textContent 로만. innerHTML 미사용.
=============================================================================================== */
window.JWorks_JSForgeAdminStudioScreenMgmt = window.JWorks_JSForgeAdminStudioScreenMgmt || {};
(function (mod) {
    "use strict";
    if (mod.__defined) { return; }
    mod.__defined = true;

    var studio = window.JWorks_JSForgeAdminStudio;
    var ctx = (window.JWorks_JSForge && window.JWorks_JSForge.contextPath) || "";
    var apiScreens = ctx + "/api/screens";

    var STEM_PATTERN = /^[a-z][a-zA-Z0-9]*$/; // ForgeScreenRequest.stem 과 동일(클라 1차 검증)

    // ---------- 위젯/DOM 헬퍼 ----------
    function el(tag, className, text) {
        var node = document.createElement(tag);
        if (className) { node.className = className; }
        if (text != null) { node.textContent = String(text); } // textContent 로만
        return node;
    }

    function $(id) { return document.getElementById(id); }

    function confirmDialog(title, text, cb) {
        if (window.JWORKS_JSConfirm && typeof window.JWORKS_JSConfirm.start === "function") {
            window.JWORKS_JSConfirm.start(String(title == null ? "" : title), String(text), function (result) {
                cb(!!result);
            });
            return;
        }
        cb(window.confirm(String(text)));
    }

    function snack(text) {
        if (window.JWORKS_JSSnackBar && typeof window.JWORKS_JSSnackBar.create === "function") {
            window.JWORKS_JSSnackBar.create(String(text));
        }
    }

    // ---------- 팝오버 메뉴 ----------
    function menu() { return $("frg-screen-menu"); }
    function menuBtn() { return $("frg-btn-screen-menu"); }

    function refreshMenuButton() {
        var btn = menuBtn();
        if (!btn) { return; }
        var screen = studio && studio.getScreen ? studio.getScreen() : null;
        btn.disabled = !(screen && screen.screenId);
        if (btn.disabled) { hideMenu(); }
    }

    function hideMenu() {
        var m = menu();
        if (m) { m.hidden = true; }
    }

    function toggleMenu() {
        var m = menu();
        var btn = menuBtn();
        if (!m || !btn) { return; }
        if (!m.hidden) { m.hidden = true; return; }
        // 버튼 바로 아래에 위치(상단바가 position:relative 앵커).
        m.style.left = btn.offsetLeft + "px";
        m.style.top = (btn.offsetTop + btn.offsetHeight + 6) + "px";
        m.hidden = false;
    }

    // ---------- 공용 모달(1필드/2필드 입력) ----------
    var backdrop = null;

    function closeModal() {
        if (backdrop && backdrop.parentNode) { backdrop.parentNode.removeChild(backdrop); }
        backdrop = null;
    }

    /**
     * fields: [{key, label, value, mono}] / onSubmit(values, setError) — setError(문구)로 유지 표기.
     */
    function openModal(title, fields, submitLabel, onSubmit) {
        closeModal();
        var bd = el("div", "frg-modal-backdrop");
        var modal = el("div", "frg-modal");

        var head = el("div", "frg-modal-head");
        head.appendChild(el("span", null, title));
        var close = el("button", "frg-modal-close", "×");
        close.type = "button";
        close.setAttribute("data-mgmt-close", "1");
        head.appendChild(close);
        modal.appendChild(head);

        var body = el("div", "frg-modal-body frg-nsf-body");
        var inputs = {};
        fields.forEach(function (f) {
            var wrap = el("div", "frg-nsf-field");
            wrap.appendChild(el("label", "frg-label", f.label));
            var input = document.createElement("input");
            input.type = "text";
            input.className = "frg-input" + (f.mono ? " frg-mono" : "");
            input.value = f.value == null ? "" : String(f.value);
            inputs[f.key] = input;
            wrap.appendChild(input);
            body.appendChild(wrap);
        });
        var err = el("p", "frg-nsf-msg");
        err.setAttribute("role", "alert");
        body.appendChild(err);
        modal.appendChild(body);

        var foot = el("div", "frg-modal-foot");
        var cancel = el("button", "frg-btn frg-btn-secondary", "취소");
        cancel.type = "button";
        cancel.setAttribute("data-mgmt-close", "1");
        foot.appendChild(cancel);
        var submit = el("button", "frg-btn frg-btn-primary", submitLabel);
        submit.type = "button";
        submit.setAttribute("data-mgmt-submit", "1");
        foot.appendChild(submit);
        modal.appendChild(foot);

        bd.appendChild(modal);
        bd.addEventListener("click", function (e) {
            var t = e.target;
            if (t === bd || (t.getAttribute && t.getAttribute("data-mgmt-close"))) {
                closeModal();
                return;
            }
            if (t.getAttribute && t.getAttribute("data-mgmt-submit")) {
                var values = {};
                Object.keys(inputs).forEach(function (k) { values[k] = inputs[k].value.trim(); });
                onSubmit(values, function (text) { err.textContent = String(text == null ? "" : text); });
            }
        });
        document.body.appendChild(bd);
        backdrop = bd;
        // 첫 입력 포커스.
        var firstKey = fields.length ? fields[0].key : null;
        if (firstKey && inputs[firstKey]) { inputs[firstKey].focus(); }
    }

    // ---------- HTTP 헬퍼(응답 바디 message 취합) ----------
    function jsonFetch(url, options) {
        return fetch(url, options).then(function (r) {
            return r.json().then(function (payload) {
                return { ok: r.ok, status: r.status, payload: payload };
            }, function () {
                return { ok: r.ok, status: r.status, payload: null };
            });
        });
    }

    function errMessage(res, fallback) {
        return (res.payload && res.payload.message) ? res.payload.message
            : (fallback + " (HTTP " + res.status + ")");
    }

    // ---------- 액션: 이름변경 ----------
    function renameScreen(screen) {
        openModal("화면 이름 변경",
            [{ key: "screenName", label: "화면명", value: screen.screenName }],
            "변경", function (values, setError) {
                if (!values.screenName) { setError("화면명을 입력하세요."); return; }
                // updateMeta 는 전체 메타를 받으므로 현재 값 + 새 이름으로 구성.
                var body = {
                    projectId: screen.projectId,
                    screenName: values.screenName,
                    stem: screen.stem,
                    archetypeCode: screen.archetypeCode,
                    roleCode: screen.roleCode,
                    statusCode: screen.statusCode
                };
                jsonFetch(apiScreens + "/" + encodeURIComponent(screen.screenId), {
                    method: "PUT",
                    headers: { "Content-Type": "application/json", "Accept": "application/json" },
                    body: JSON.stringify(body)
                }).then(function (res) {
                    if (!res.ok) { setError(errMessage(res, "이름 변경에 실패했습니다.")); return; }
                    closeModal();
                    snack("화면 이름이 변경되었습니다.");
                    studio.reloadScreens(screen.screenId);
                }).catch(function () { setError("네트워크 오류로 변경에 실패했습니다."); });
            });
    }

    // ---------- 액션: 복제 ----------
    function duplicateScreen(screen) {
        openModal("화면 복제 — 모듈 배치/속성이 그대로 복사됩니다",
            [
                { key: "screenName", label: "새 화면명", value: String(screen.screenName || "") + " 복사본" },
                { key: "stem", label: "새 stem (소문자 시작 영숫자)", value: String(screen.stem || "") + "Copy", mono: true }
            ],
            "복제", function (values, setError) {
                if (!values.screenName) { setError("화면명을 입력하세요."); return; }
                if (!STEM_PATTERN.test(values.stem)) {
                    setError("stem 형식이 올바르지 않습니다. (예: userMgmt — 소문자 시작 영숫자)");
                    return;
                }
                jsonFetch(apiScreens + "/" + encodeURIComponent(screen.screenId) + "/duplicate", {
                    method: "POST",
                    headers: { "Content-Type": "application/json", "Accept": "application/json" },
                    body: JSON.stringify({ screenName: values.screenName, stem: values.stem })
                }).then(function (res) {
                    if (!res.ok || !res.payload) { setError(errMessage(res, "복제에 실패했습니다.")); return; }
                    closeModal();
                    snack("화면이 복제되었습니다.");
                    studio.reloadScreens(res.payload.screenId); // 복제본을 바로 연다
                }).catch(function () { setError("네트워크 오류로 복제에 실패했습니다."); });
            });
    }

    // ---------- 액션: 삭제 ----------
    function deleteScreen(screen) {
        confirmDialog("화면 삭제",
            "'" + String(screen.screenName || "") + "' 화면을 삭제할까요? (논리삭제 — 생성된 파일은 지우지 않습니다)",
            function (ok) {
                if (!ok) { return; }
                fetch(apiScreens + "/" + encodeURIComponent(screen.screenId), { method: "DELETE" })
                    .then(function (r) {
                        if (!r.ok) { snack("삭제에 실패했습니다. (HTTP " + r.status + ")"); return; }
                        snack("화면이 삭제되었습니다.");
                        studio.reloadScreens(null); // 선택 해제 + 목록 갱신
                    })
                    .catch(function () { snack("네트워크 오류로 삭제에 실패했습니다."); });
            });
    }

    // ---------- 결선 ----------
    function onMenuAction(action) {
        hideMenu();
        var screen = studio && studio.getScreen ? studio.getScreen() : null;
        if (!screen || !screen.screenId) { return; }
        if (action === "rename") { renameScreen(screen); }
        else if (action === "duplicate") { duplicateScreen(screen); }
        else if (action === "delete") { deleteScreen(screen); }
    }

    function init() {
        if (!studio) { return; }
        var btn = menuBtn();
        var m = menu();
        if (!btn || !m) { return; }

        btn.addEventListener("click", function (e) {
            e.stopPropagation();
            toggleMenu();
        });
        m.addEventListener("click", function (e) {
            var t = e.target.closest ? e.target.closest("[data-menu]") : null;
            if (t) { onMenuAction(t.getAttribute("data-menu")); }
        });
        // 바깥 클릭/Esc 로 메뉴 닫기.
        document.addEventListener("click", function (e) {
            if (!m.hidden && !m.contains(e.target) && e.target !== btn) { hideMenu(); }
        });
        document.addEventListener("keydown", function (e) {
            if (e.key === "Escape") { hideMenu(); closeModal(); }
        });

        // 화면 로드/해제에 따라 버튼 활성화 동기화.
        studio.onScreenLoaded(function () { refreshMenuButton(); });
        studio.onDefinitionChanged(function () { refreshMenuButton(); });
        refreshMenuButton();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})(window.JWorks_JSForgeAdminStudioScreenMgmt);
