/* ===============================================================================================
Name : dbTablePicker.js
Description : P11 타겟 DB 테이블/컬럼 선택기. /api/projects/{id}/db 의 읽기전용 카탈로그를 조회해
             "테이블을 고르면 컬럼이 채워지는" 흐름을 제공한다. 선택 결과만 콜백으로 돌려주고,
             DEFINITION 갱신은 호출측(propsPanel)이 담당한다(책임 분리).

XSS: 모든 텍스트 삽입은 createElement/textContent 로만. innerHTML 미사용.
=============================================================================================== */
window.JWorks_JSForgeAdminStudioDbPicker = window.JWorks_JSForgeAdminStudioDbPicker || {};
(function (picker) {
    "use strict";
    if (picker.__defined) { return; }
    picker.__defined = true;

    var ctx = (window.JWorks_JSForge && window.JWorks_JSForge.contextPath) || "";
    var backdrop = null;
    var state = { projectId: null, onPick: null, table: null, columns: [] };

    function el(tag, className, text) {
        var node = document.createElement(tag);
        if (className) { node.className = className; }
        if (text != null) { node.textContent = String(text); }
        return node;
    }

    function clear(node) {
        while (node && node.firstChild) { node.removeChild(node.firstChild); }
    }

    function api(suffix) {
        return ctx + "/api/projects/" + encodeURIComponent(state.projectId) + "/db" + suffix;
    }

    function getJson(url) {
        return fetch(url, { headers: { "Accept": "application/json" } })
            .then(function (r) {
                return r.json().then(function (j) {
                    if (!r.ok) { throw new Error(j && j.message ? j.message : "요청 실패"); }
                    return j;
                });
            });
    }

    // ---------- 셸 ----------

    function ensureShell() {
        if (backdrop) { return backdrop; }
        backdrop = el("div", "frg-modal-backdrop");
        backdrop.id = "frg-dbp-backdrop";
        backdrop.hidden = true;

        var modal = el("div", "frg-modal");
        modal.setAttribute("role", "dialog");
        modal.setAttribute("aria-modal", "true");

        var head = el("div", "frg-modal-head");
        head.appendChild(el("span", null, "테이블에서 컬럼 가져오기"));
        var close = el("button", "frg-modal-close", "×");
        close.type = "button";
        close.setAttribute("aria-label", "닫기");
        close.addEventListener("click", hide);
        head.appendChild(close);

        var body = el("div", "frg-modal-body");
        body.id = "frg-dbp-body";

        var foot = el("div", "frg-modal-foot");
        var msg = el("span", "frg-msg");
        msg.id = "frg-dbp-msg";
        foot.appendChild(msg);
        var apply = el("button", "frg-btn frg-btn-primary", "적용");
        apply.type = "button";
        apply.id = "frg-dbp-apply";
        apply.disabled = true;
        apply.addEventListener("click", applySelection);
        foot.appendChild(apply);

        modal.appendChild(head);
        modal.appendChild(body);
        modal.appendChild(foot);
        backdrop.appendChild(modal);
        document.body.appendChild(backdrop);

        backdrop.addEventListener("click", function (e) {
            if (e.target === backdrop) { hide(); }
        });
        document.addEventListener("keydown", function (e) {
            if (e.key === "Escape" && backdrop && !backdrop.hidden) { hide(); }
        });
        return backdrop;
    }

    function message(text, kind) {
        var msg = document.getElementById("frg-dbp-msg");
        if (!msg) { return; }
        msg.textContent = text || "";
        msg.className = "frg-msg" + (kind ? " frg-msg-" + kind : "");
    }

    function hide() {
        if (backdrop) { backdrop.hidden = true; }
        state.onPick = null;
        state.table = null;
        state.columns = [];
    }

    // ---------- 1단계: 테이블 ----------

    function renderTableStep() {
        var body = document.getElementById("frg-dbp-body");
        clear(body);
        document.getElementById("frg-dbp-apply").disabled = true;

        var search = el("input", "frg-input");
        search.type = "search";
        search.placeholder = "테이블 이름으로 검색";
        body.appendChild(search);

        var list = el("div", "frg-dbp-list");
        body.appendChild(list);

        var timer = null;
        search.addEventListener("input", function () {
            if (timer) { window.clearTimeout(timer); }
            timer = window.setTimeout(function () { loadTables(list, search.value); }, 220);
        });
        loadTables(list, "");
    }

    function loadTables(list, keyword) {
        clear(list);
        list.appendChild(el("p", "frg-hint", "불러오는 중…"));
        var url = api("/tables") + (keyword ? "?keyword=" + encodeURIComponent(keyword) : "");
        getJson(url)
            .then(function (tables) {
                clear(list);
                if (!Array.isArray(tables) || !tables.length) {
                    list.appendChild(el("p", "frg-hint", "테이블이 없습니다."));
                    return;
                }
                tables.forEach(function (t) {
                    var row = el("button", "frg-dbp-item");
                    row.type = "button";
                    row.appendChild(el("strong", "frg-mono", t.name));
                    if (t.remarks) { row.appendChild(el("span", "frg-dbp-remark", t.remarks)); }
                    if (t.type === "VIEW") { row.appendChild(el("span", "frg-badge", "VIEW")); }
                    row.addEventListener("click", function () { renderColumnStep(t.name); });
                    list.appendChild(row);
                });
            })
            .catch(function (e) {
                clear(list);
                list.appendChild(el("p", "frg-hint", e.message || "테이블을 불러오지 못했습니다."));
                list.appendChild(el("p", "frg-hint",
                    "프로젝트 화면에서 'DB 연결'을 먼저 설정하세요."));
            });
    }

    // ---------- 2단계: 컬럼 ----------

    function renderColumnStep(table) {
        state.table = table;
        var body = document.getElementById("frg-dbp-body");
        clear(body);

        var back = el("button", "frg-btn frg-btn-secondary frg-btn-sm", "← 테이블 목록");
        back.type = "button";
        back.addEventListener("click", renderTableStep);
        body.appendChild(back);
        body.appendChild(el("h3", "frg-dbp-table", table));

        var list = el("div", "frg-dbp-list");
        list.appendChild(el("p", "frg-hint", "불러오는 중…"));
        body.appendChild(list);

        getJson(api("/tables/" + encodeURIComponent(table) + "/columns"))
            .then(function (res) {
                state.columns = (res && Array.isArray(res.columns)) ? res.columns : [];
                clear(list);
                if (!state.columns.length) {
                    list.appendChild(el("p", "frg-hint", "컬럼이 없습니다."));
                    return;
                }
                state.columns.forEach(function (col, index) {
                    list.appendChild(columnRow(col, index));
                });
                document.getElementById("frg-dbp-apply").disabled = false;
                message(state.columns.length + "개 컬럼 · 체크한 컬럼만 화면에 추가됩니다.");
            })
            .catch(function (e) {
                clear(list);
                list.appendChild(el("p", "frg-hint", e.message || "컬럼을 불러오지 못했습니다."));
            });
    }

    function columnRow(col, index) {
        var row = el("label", "frg-dbp-col");

        var use = el("input");
        use.type = "checkbox";
        use.checked = true;
        use.setAttribute("data-use", String(index));
        row.appendChild(use);

        row.appendChild(el("span", "frg-mono", col.name));
        row.appendChild(el("span", "frg-dbp-type", col.typeName || ""));
        if (col.remarks) { row.appendChild(el("span", "frg-dbp-remark", col.remarks)); }

        var key = el("input");
        key.type = "radio";
        key.name = "frg-dbp-key";
        key.checked = !!col.primaryKey;
        key.setAttribute("data-key", String(index));
        key.title = "키 컬럼(단건 조회 기준)으로 사용";
        row.appendChild(key);
        row.appendChild(el("span", "frg-dbp-keylabel", col.primaryKey ? "PK" : "키"));

        return row;
    }

    function applySelection() {
        var body = document.getElementById("frg-dbp-body");
        if (!body || !state.table || !state.onPick) { return; }

        var picked = [];
        Array.prototype.forEach.call(body.querySelectorAll("[data-use]"), function (input) {
            if (!input.checked) { return; }
            var col = state.columns[Number(input.getAttribute("data-use"))];
            if (!col) { return; }
            picked.push({
                name: col.name,
                displayName: col.remarks ? col.remarks : col.name,
                displayYn: true,
                sortYn: false
            });
        });
        if (!picked.length) {
            message("컬럼을 하나 이상 선택하세요.", "warn");
            return;
        }

        var keyColumn = "";
        var keyInput = body.querySelector("[data-key]:checked");
        if (keyInput) {
            var keyCol = state.columns[Number(keyInput.getAttribute("data-key"))];
            if (keyCol) { keyColumn = keyCol.name; }
        }

        var callback = state.onPick;
        var result = { table: state.table, keyColumn: keyColumn, columns: picked };
        hide();
        callback(result);
    }

    // ---------- Public API ----------

    /**
     * 선택기를 연다.
     * @param {number|string} projectId 대상 프로젝트
     * @param {function} onPick {table, keyColumn, columns[]} 를 받는 콜백
     */
    picker.open = function (projectId, onPick) {
        if (projectId == null) {
            if (window.JWORKS_JSSnackBar) { window.JWORKS_JSSnackBar.create("프로젝트를 먼저 선택하세요."); }
            return;
        }
        state.projectId = projectId;
        state.onPick = onPick;
        state.table = null;
        state.columns = [];
        ensureShell().hidden = false;
        message("");
        renderTableStep();
    };

})(window.JWorks_JSForgeAdminStudioDbPicker);
