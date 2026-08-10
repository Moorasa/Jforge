/* ===============================================================================================
Name : screenPicker.js
Description : 프레임에 넣을 화면 고르기(계약 §19). 프로젝트의 화면 목록을 보여주고, 고른 화면의
             role/stem 으로 **경로 제안값**을 만들어 콜백으로 돌려준다.
             DEFINITION 갱신은 호출측(propsPanel)이 한다(dbTablePicker 와 같은 책임 분리).

★ 왜 "화면 참조"가 아니라 "경로 제안"인가 (계약 §19.0) ★
 이 제품에는 **생성 화면의 페이지 URL 규약이 없다.** StubGenerator 의 Controller 는
 `// TODO: @RequestMapping 핸들러 추가` 인 빈 골격이고, §14 승격분은 데이터 API 용
 @RestController 다. 페이지를 어느 URL 에 매핑할지는 타겟 개발자가 정한다.
 그래서 이 피커는 정답을 아는 척하지 않는다 — `/{role}/{stem}` 을 **시작값으로 채워 주고**
 사용자가 고칠 수 있게 두며, 화면에도 "타겟 매핑과 일치해야 한다"고 적는다.

XSS: 모든 텍스트 삽입은 createElement/textContent 로만. innerHTML 미사용.
=============================================================================================== */
window.MagicIAM_JSForgeAdminStudioScreenPicker = window.MagicIAM_JSForgeAdminStudioScreenPicker || {};
(function (picker) {
    "use strict";
    if (picker.__defined) { return; }
    picker.__defined = true;

    var ctx = (window.MagicIAM_JSForge && window.MagicIAM_JSForge.contextPath) || "";
    var backdrop = null;
    var state = { onPick: null, current: "" };

    function el(tag, className, text) {
        var node = document.createElement(tag);
        if (className) { node.className = className; }
        if (text != null) { node.textContent = String(text); }
        return node;
    }

    function clear(node) {
        while (node && node.firstChild) { node.removeChild(node.firstChild); }
    }

    /**
     * 🔒 계약 §19.1 게이트와 **같은 규칙**을 클라이언트에서도 1차로 본다.
     * (최종 판정은 생성기의 common/frameSrc.ftl 이다 — 여기서 막는 건 사용자에게 즉시
     *  알려주기 위한 것이지 보안 경계가 아니다.)
     */
    var SAFE_PATH = /^\/(?!\/)[A-Za-z0-9/_.\-]{0,199}$/;

    picker.isSafePath = function (value) {
        var s = String(value == null ? "" : value);
        return SAFE_PATH.test(s) && s.indexOf("..") === -1;
    };

    /** 화면 하나의 경로 제안값. 생성 JSP 위치({role}/{stem}/{stem}.jsp)에서 가장 자연스러운 형태다. */
    picker.suggestPath = function (screen) {
        if (!screen) { return ""; }
        var role = String(screen.roleCode || "admin").replace(/[^a-z]/g, "");
        var stem = String(screen.stem || "").replace(/[^A-Za-z0-9]/g, "");
        if (!stem) { return ""; }
        return "/" + (role || "admin") + "/" + stem;
    };

    // ---------- 셸 ----------

    function close() {
        if (backdrop && backdrop.parentNode) { backdrop.parentNode.removeChild(backdrop); }
        backdrop = null;
        state.onPick = null;
    }

    function buildShell() {
        // 기존 모달 크롬(forge-theme.css)을 그대로 쓴다 — 새 껍데기를 만들지 않는다.
        backdrop = el("div", "frg-modal-backdrop");
        var modal = el("div", "frg-modal frg-picker-modal");

        var head = el("div", "frg-modal-head");
        head.appendChild(el("h3", "frg-modal-title", "프레임에 넣을 화면"));
        var closeBtn = el("button", "frg-modal-close", "×");
        closeBtn.type = "button";
        closeBtn.setAttribute("aria-label", "닫기");
        closeBtn.addEventListener("click", close);
        head.appendChild(closeBtn);
        modal.appendChild(head);

        var body = el("div", "frg-modal-body frg-picker-body");
        modal.appendChild(body);

        var foot = el("div", "frg-modal-foot");
        modal.appendChild(foot);

        backdrop.appendChild(modal);
        backdrop.addEventListener("click", function (e) { if (e.target === backdrop) { close(); } });
        document.addEventListener("keydown", function onEsc(e) {
            if (e.key === "Escape") { close(); document.removeEventListener("keydown", onEsc); }
        });
        document.body.appendChild(backdrop);
        return { body: body, foot: foot };
    }

    // ---------- 본문 ----------

    function render(parts, screens, currentScreenId) {
        clear(parts.body);
        clear(parts.foot);

        var pathRow = el("div", "frg-picker-path");
        pathRow.appendChild(el("label", "frg-picker-label", "경로"));
        var input = el("input", "frg-input frg-mono");
        input.type = "text";
        input.value = state.current || "";
        input.placeholder = "/admin/userMgmt";
        input.setAttribute("maxlength", "200");
        pathRow.appendChild(input);
        parts.body.appendChild(pathRow);

        var warn = el("p", "frg-picker-warn");
        warn.hidden = true;
        parts.body.appendChild(warn);

        parts.body.appendChild(el("p", "frg-hint",
            "이 제품은 생성 화면의 페이지 URL을 정하지 않습니다 — 타겟 앱에서 직접 매핑한 주소여야 합니다. "
            + "아래에서 화면을 고르면 흔한 형태로 채워 드리니, 실제 매핑에 맞게 고치세요."));

        var list = el("ul", "frg-picker-list");
        if (!screens.length) {
            list.appendChild(el("li", "frg-picker-empty", "이 프로젝트에 화면이 없습니다"));
        }
        screens.forEach(function (s) {
            var li = el("li", "frg-picker-item");
            var btn = el("button", "frg-picker-btn");
            btn.type = "button";
            btn.appendChild(el("span", "frg-picker-name", s.screenName || s.stem || "(이름 없음)"));
            var suggested = picker.suggestPath(s);
            btn.appendChild(el("span", "frg-picker-path-hint", suggested));
            if (String(s.screenId) === String(currentScreenId)) {
                btn.appendChild(el("span", "frg-picker-self", "지금 편집 중"));
            }
            btn.addEventListener("click", function () {
                input.value = suggested;
                validate();
            });
            li.appendChild(btn);
            list.appendChild(li);
        });
        parts.body.appendChild(list);

        function validate() {
            var v = String(input.value || "").trim();
            if (v === "") { warn.hidden = true; return true; }
            var ok = picker.isSafePath(v);
            warn.hidden = ok;
            if (!ok) {
                warn.textContent = "같은 앱 안의 / 로 시작하는 경로만 됩니다. "
                    + "영문·숫자·/·_·-·. 만 쓸 수 있고 .. 는 넣을 수 없습니다. "
                    + "(이대로 저장하면 생성 시 src가 만들어지지 않습니다)";
            }
            return ok;
        }
        input.addEventListener("input", validate);
        validate();

        var clearBtn = el("button", "frg-btn frg-btn-secondary", "연결 지우기");
        clearBtn.type = "button";
        clearBtn.addEventListener("click", function () {
            var cb = state.onPick;
            close();
            if (cb) { cb(""); }
        });
        parts.foot.appendChild(clearBtn);

        var cancel = el("button", "frg-btn frg-btn-secondary", "취소");
        cancel.type = "button";
        cancel.addEventListener("click", close);
        parts.foot.appendChild(cancel);

        var apply = el("button", "frg-btn frg-btn-primary", "적용");
        apply.type = "button";
        apply.addEventListener("click", function () {
            if (!validate()) { input.focus(); return; }
            var value = String(input.value || "").trim();
            var cb = state.onPick;
            close();
            if (cb) { cb(value); }
        });
        parts.foot.appendChild(apply);
    }

    /**
     * @param projectId       화면 목록을 가져올 프로젝트
     * @param currentValue    현재 frameSrc(있으면 입력칸 시작값)
     * @param currentScreenId 편집 중인 화면(자기 자신 표시용)
     * @param onPick          fn(path) — 빈 문자열이면 연결 해제
     */
    picker.open = function (projectId, currentValue, currentScreenId, onPick) {
        state.onPick = typeof onPick === "function" ? onPick : null;
        state.current = String(currentValue == null ? "" : currentValue);
        var parts = buildShell();
        parts.body.appendChild(el("p", "frg-hint", "화면 목록을 불러오는 중…"));

        fetch(ctx + "/api/screens?projectId=" + encodeURIComponent(projectId),
              { headers: { "Accept": "application/json" } })
            .then(function (r) { if (!r.ok) { throw new Error("http " + r.status); } return r.json(); })
            .then(function (list) {
                render(parts, Array.isArray(list) ? list : [], currentScreenId);
            })
            .catch(function () {
                // 목록을 못 불러와도 경로는 손으로 넣을 수 있어야 한다.
                render(parts, [], currentScreenId);
            });
    };

    picker.close = close;
})(window.MagicIAM_JSForgeAdminStudioScreenPicker);
