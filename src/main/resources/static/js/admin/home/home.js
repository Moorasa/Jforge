/* ===============================================================================================
Name : home.js
Description : 대시보드 도그푸딩 화면 (P7-1). /api/projects + /api/screens?projectId= 를 소비해
             요약(프로젝트/화면 수)과 프로젝트 카드 그리드를 렌더한다.

XSS: 모든 텍스트 삽입은 createElement/textContent 로만. innerHTML(및 jQuery .html()) 미사용.
=============================================================================================== */
window.JWorks_JSForgeAdminHome = window.JWorks_JSForgeAdminHome || {};
(function (page) {
    "use strict";
    if (page.__defined) { return; }
    page.__defined = true;

    var ctx = (window.JWorks_JSForge && window.JWorks_JSForge.contextPath) || "";
    var apiProjects = ctx + "/api/projects";
    var apiScreens = ctx + "/api/screens";

    function el(tag, className, text) {
        var node = document.createElement(tag);
        if (className) { node.className = className; }
        if (text != null) { node.textContent = String(text); } // textContent 로만
        return node;
    }

    function clear(node) {
        while (node.firstChild) { node.removeChild(node.firstChild); }
    }

    function setStat(id, value) {
        var box = document.getElementById(id);
        if (box) { box.textContent = String(value); }
    }

    // 프로젝트 1건 → 카드 DOM(전부 textContent). 화면 수는 로드 후 채움.
    function projectCard(p) {
        var card = el("article", "frg-proj-card");

        var head = el("div", "frg-proj-head");
        head.appendChild(el("h3", "frg-proj-name", p.projectName));
        var count = el("span", "frg-proj-count", "…");
        count.setAttribute("data-count-for", String(p.projectId));
        head.appendChild(count);
        card.appendChild(head);

        var meta = el("dl", "frg-proj-meta");
        meta.appendChild(el("dt", null, "타겟 루트"));
        meta.appendChild(el("dd", "frg-mono", p.targetRootPath));
        if (p.packageBase) {
            meta.appendChild(el("dt", null, "패키지"));
            meta.appendChild(el("dd", "frg-mono", p.packageBase));
        }
        card.appendChild(meta);

        var actions = el("div", "frg-proj-actions");
        var open = el("a", "frg-btn frg-btn-primary frg-btn-sm", "스튜디오에서 열기");
        open.href = ctx + "/admin/studio";
        actions.appendChild(open);
        var manage = el("a", "frg-btn frg-btn-secondary frg-btn-sm", "프로젝트 관리");
        manage.href = ctx + "/admin/projects";
        actions.appendChild(manage);
        card.appendChild(actions);

        return card;
    }

    // 프로젝트별 화면 수를 병렬 조회해 카드 뱃지 + 합계 스탯을 채운다.
    function loadScreenCounts(projects) {
        var total = 0;
        var done = 0;
        projects.forEach(function (p) {
            var url = apiScreens + "?projectId=" + encodeURIComponent(p.projectId);
            fetch(url, { headers: { "Accept": "application/json" } })
                .then(function (r) { if (!r.ok) { throw new Error("http " + r.status); } return r.json(); })
                .then(function (list) {
                    var n = Array.isArray(list) ? list.length : 0;
                    total += n;
                    var badge = document.querySelector('[data-count-for="' + String(p.projectId) + '"]');
                    if (badge) { badge.textContent = "화면 " + n; }
                })
                .catch(function () {
                    var badge = document.querySelector('[data-count-for="' + String(p.projectId) + '"]');
                    if (badge) { badge.textContent = "–"; }
                })
                .finally(function () {
                    done += 1;
                    if (done === projects.length) { setStat("frg-stat-screens", total); }
                });
        });
        if (!projects.length) { setStat("frg-stat-screens", 0); }
    }

    function renderProjects(list) {
        var grid = document.getElementById("frg-home-projects");
        if (!grid) { return; }
        clear(grid);
        if (!Array.isArray(list) || !list.length) {
            var empty = el("div", "frg-home-empty");
            empty.appendChild(el("p", "frg-empty", "아직 프로젝트가 없습니다. 타겟 폴더를 등록해 시작하세요."));
            var cta = el("a", "frg-btn frg-btn-primary", "프로젝트 만들기");
            cta.href = ctx + "/admin/projects";
            empty.appendChild(cta);
            grid.appendChild(empty);
            return;
        }
        list.forEach(function (p) { grid.appendChild(projectCard(p)); });
    }

    function load() {
        fetch(apiProjects, { headers: { "Accept": "application/json" } })
            .then(function (r) { if (!r.ok) { throw new Error("http " + r.status); } return r.json(); })
            .then(function (list) {
                var projects = Array.isArray(list) ? list : [];
                setStat("frg-stat-projects", projects.length);
                renderProjects(projects);
                loadScreenCounts(projects);
            })
            .catch(function () {
                setStat("frg-stat-projects", "–");
                setStat("frg-stat-screens", "–");
                var grid = document.getElementById("frg-home-projects");
                if (grid) {
                    clear(grid);
                    grid.appendChild(el("p", "frg-empty", "프로젝트 목록을 불러오지 못했습니다."));
                }
            });
    }

    document.addEventListener("DOMContentLoaded", load);
})(window.JWorks_JSForgeAdminHome);
