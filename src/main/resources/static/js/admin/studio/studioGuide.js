/* ===============================================================================================
Name : studioGuide.js
Description : 스튜디오 시작 가이드 (P7-5). 화면이 선택되기 전까지 캔버스 위 오버레이로
             "지금 해야 할 일"을 단계(1 프로젝트 → 2 화면 → 3 조립·생성)로 안내하고,
             프로젝트가 없으면 **스튜디오 안에서 바로 만들 수 있는** 모달을 제공한다.

결합: studioApp(허브) 공개 API(getScreen/reloadProjects/onScreenLoaded/onDefinitionChanged)와
     상단바 select 의 DOM 관찰만 사용 — 허브 내부는 수정하지 않는다.

상태 판정:
  프로젝트 미선택 + 프로젝트 없음   → [프로젝트 만들기] (인스튜디오 모달)
  프로젝트 미선택 + 프로젝트 있음   → "상단에서 프로젝트를 선택하세요" (+ 새 프로젝트)
  프로젝트 선택   + 화면 없음       → [새 화면 만들기]
  프로젝트 선택   + 화면 미선택     → "화면을 선택하거나 새로 만드세요"
  화면 선택                         → 가이드 숨김(캔버스 노출)

XSS: 모든 표시 문자열은 createElement/textContent 로만. innerHTML 미사용.
=============================================================================================== */
window.MagicIAM_JSForgeAdminStudioGuide = window.MagicIAM_JSForgeAdminStudioGuide || {};
(function (mod) {
    "use strict";
    if (mod.__defined) { return; }
    mod.__defined = true;

    var studio = window.MagicIAM_JSForgeAdminStudio;
    var ctx = (window.MagicIAM_JSForge && window.MagicIAM_JSForge.contextPath) || "";
    var apiProjects = ctx + "/api/projects";

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

    function $(id) { return document.getElementById(id); }

    function snack(text) {
        if (window.JWORKS_JSSnackBar && typeof window.JWORKS_JSSnackBar.create === "function") {
            window.JWORKS_JSSnackBar.create(String(text));
        }
    }

    // ---------- 상태 판정 ----------
    function hasRealOptions(sel) {
        if (!sel) { return false; }
        var opts = sel.querySelectorAll("option");
        for (var i = 0; i < opts.length; i++) {
            if (opts[i].value !== "") { return true; }
        }
        return false;
    }

    function computeState() {
        var screen = studio && studio.getScreen ? studio.getScreen() : null;
        if (screen && screen.screenId) { return "hidden"; }
        var projectSel = $("frg-project-select");
        var screenSel = $("frg-screen-select");
        if (!projectSel || !projectSel.value) {
            return hasRealOptions(projectSel) ? "pickProject" : "noProjects";
        }
        return hasRealOptions(screenSel) ? "pickScreen" : "noScreens";
    }

    // ---------- 가이드 렌더 ----------
    function stepRow(num, label, state) {
        // state: "done" | "now" | "todo"
        var row = el("div", "frg-guide-step frg-guide-step-" + state);
        row.appendChild(el("span", "frg-guide-step-num", state === "done" ? "✓" : String(num)));
        row.appendChild(el("span", "frg-guide-step-label", label));
        return row;
    }

    function ctaButton(label, primary, onClick) {
        var btn = el("button", "frg-btn " + (primary ? "frg-btn-primary" : "frg-btn-secondary"), label);
        btn.type = "button";
        btn.addEventListener("click", onClick);
        return btn;
    }

    function render() {
        var box = $("frg-canvas-guide");
        if (!box) { return; }
        var state = computeState();
        if (state === "hidden") {
            box.hidden = true;
            return;
        }
        clear(box);
        var card = el("div", "frg-guide-card");
        card.appendChild(el("h3", "frg-guide-title", "화면 만들기, 3단계면 됩니다"));

        var projectDone = (state === "noScreens" || state === "pickScreen");
        var steps = el("div", "frg-guide-steps");
        steps.appendChild(stepRow(1, "타겟 프로젝트 연결 — 파일이 생성될 폴더",
            projectDone ? "done" : "now"));
        steps.appendChild(stepRow(2, "화면 만들기 — 유형을 고르면 기본 구성이 배치됩니다",
            projectDone ? "now" : "todo"));
        steps.appendChild(stepRow(3, "조립 후 [파일 생성] — JSP/JS/CSS 가 폴더에 생성",
            "todo"));
        card.appendChild(steps);

        var hint = el("p", "frg-guide-hint");
        var actions = el("div", "frg-guide-actions");

        if (state === "noProjects") {
            hint.textContent = "아직 연결된 프로젝트가 없습니다. 파일이 생성될 타겟 폴더를 알려주세요.";
            actions.appendChild(ctaButton("프로젝트 만들기", true, openProjectModal));
        } else if (state === "pickProject") {
            hint.textContent = "왼쪽 위 [프로젝트]에서 작업할 프로젝트를 선택하세요.";
            actions.appendChild(ctaButton("새 프로젝트 만들기", false, openProjectModal));
        } else if (state === "noScreens") {
            hint.textContent = "이 프로젝트에는 아직 화면이 없습니다. 첫 화면을 만들어 보세요.";
            actions.appendChild(ctaButton("새 화면 만들기", true, clickNewScreen));
        } else { // pickScreen
            hint.textContent = "위의 [화면]에서 편집할 화면을 선택하거나, 새 화면을 만드세요.";
            actions.appendChild(ctaButton("새 화면 만들기", true, clickNewScreen));
        }
        card.appendChild(hint);
        card.appendChild(actions);
        box.appendChild(card);
        box.hidden = false;
    }

    function clickNewScreen() {
        var btn = $("frg-btn-new-screen");
        if (btn && !btn.disabled) { btn.click(); }
    }

    // ---------- 인스튜디오 프로젝트 생성 모달 ----------
    var backdrop = null;

    function closeModal() {
        if (backdrop && backdrop.parentNode) { backdrop.parentNode.removeChild(backdrop); }
        backdrop = null;
    }

    function field(labelText, hintText, mono, placeholder) {
        var wrap = el("div", "frg-nsf-field");
        wrap.appendChild(el("label", "frg-label", labelText));
        var input = document.createElement("input");
        input.type = "text";
        input.className = "frg-input" + (mono ? " frg-mono" : "");
        if (placeholder) { input.placeholder = placeholder; }
        wrap.appendChild(input);
        if (hintText) { wrap.appendChild(el("span", "frg-nsf-hint", hintText)); }
        return { wrap: wrap, input: input };
    }

    function openProjectModal() {
        closeModal();
        var bd = el("div", "frg-modal-backdrop");
        var modal = el("div", "frg-modal");

        var head = el("div", "frg-modal-head");
        head.appendChild(el("span", null, "프로젝트 만들기 — 파일이 생성될 위치"));
        var close = el("button", "frg-modal-close", "×");
        close.type = "button";
        close.setAttribute("data-guide-close", "1");
        head.appendChild(close);
        modal.appendChild(head);

        var body = el("div", "frg-modal-body frg-nsf-body");
        var name = field("프로젝트 이름", null, false, "예: 사내 포털");
        var path = field("타겟 폴더 (절대경로)",
            "생성되는 JSP/JS/CSS 파일이 이 폴더 아래에 쓰입니다.", true, "예: C:\\parkDev\\my-target");
        var pkg = field("자바 패키지 (선택)",
            "컨트롤러/매퍼 골격(stub)이 이 패키지로 생성됩니다. 비워두면 stub 경로에 기본값 사용.",
            true, "예: com.acme.portal");
        body.appendChild(name.wrap);
        body.appendChild(path.wrap);
        body.appendChild(pkg.wrap);
        var err = el("p", "frg-nsf-msg");
        err.setAttribute("role", "alert");
        body.appendChild(err);
        modal.appendChild(body);

        var foot = el("div", "frg-modal-foot");
        var cancel = el("button", "frg-btn frg-btn-secondary", "취소");
        cancel.type = "button";
        cancel.setAttribute("data-guide-close", "1");
        foot.appendChild(cancel);
        var submit = el("button", "frg-btn frg-btn-primary", "만들기");
        submit.type = "button";
        submit.setAttribute("data-guide-submit", "1");
        foot.appendChild(submit);
        modal.appendChild(foot);

        bd.appendChild(modal);
        bd.addEventListener("click", function (e) {
            var t = e.target;
            if (t === bd || (t.getAttribute && t.getAttribute("data-guide-close"))) {
                closeModal();
                return;
            }
            if (!(t.getAttribute && t.getAttribute("data-guide-submit"))) { return; }
            var bodyJson = {
                projectName: name.input.value.trim(),
                targetRootPath: path.input.value.trim(),
                packageBase: pkg.input.value.trim()
            };
            if (!bodyJson.projectName) { err.textContent = "프로젝트 이름을 입력하세요."; return; }
            if (!bodyJson.targetRootPath) { err.textContent = "타겟 폴더(절대경로)를 입력하세요."; return; }
            fetch(apiProjects, {
                method: "POST",
                headers: { "Content-Type": "application/json", "Accept": "application/json" },
                body: JSON.stringify(bodyJson)
            }).then(function (r) {
                return r.json().then(function (j) { return { ok: r.ok, status: r.status, j: j }; },
                    function () { return { ok: r.ok, status: r.status, j: null }; });
            }).then(function (res) {
                if (!res.ok || !res.j) {
                    err.textContent = (res.j && res.j.message)
                        ? res.j.message : ("프로젝트 생성에 실패했습니다. (HTTP " + res.status + ")");
                    return;
                }
                closeModal();
                snack("프로젝트가 만들어졌습니다. 이제 화면을 만들어 보세요.");
                // 목록 재조회 + 신규 프로젝트 자동 선택(허브 공개 API).
                if (studio && typeof studio.reloadProjects === "function") {
                    studio.reloadProjects(res.j.projectId).then(render);
                }
            }).catch(function () { err.textContent = "네트워크 오류로 생성에 실패했습니다."; });
        });
        document.body.appendChild(bd);
        backdrop = bd;
        name.input.focus();
    }

    // ---------- 결선 ----------
    function init() {
        if (!studio) { return; }
        var projectSel = $("frg-project-select");
        var screenSel = $("frg-screen-select");

        // select 값 변경 + 옵션 목록 변경(비동기 로드 완료)을 모두 관찰해 가이드를 갱신한다.
        if (projectSel) { projectSel.addEventListener("change", render); }
        if (screenSel) { screenSel.addEventListener("change", render); }
        var observer = new MutationObserver(render);
        if (projectSel) { observer.observe(projectSel, { childList: true }); }
        if (screenSel) { observer.observe(screenSel, { childList: true }); }

        studio.onScreenLoaded(render);
        studio.onDefinitionChanged(render); // reloadScreens(null) 등 화면 해제도 커버
        document.addEventListener("keydown", function (e) {
            if (e.key === "Escape") { closeModal(); }
        });

        render();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})(window.MagicIAM_JSForgeAdminStudioGuide);
