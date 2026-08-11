/* ===============================================================================================
Name : projectList.js
Description : 프로젝트 관리 도그푸딩 화면 스크립트 (P1-3, P7-1 개편). /api/projects CRUD 소비.
             P7-1: innerHTML 문자열 조립 → createElement/textContent 로 교체(스튜디오 JS 와 동일
             규약), 삭제는 JWORKS_JSConfirm(번들 위젯) 경유, 성공 알림은 JWORKS_JSSnackBar.

XSS: 모든 텍스트 삽입은 createElement/textContent 로만. innerHTML(및 jQuery .html()) 미사용.
=============================================================================================== */
window.JWorks_JSForgeAdminProject = window.JWorks_JSForgeAdminProject || {};
(function (page) {
    "use strict";
    if (page.__defined) { return; }
    page.__defined = true;

    var ctx = (window.JWorks_JSForge && window.JWorks_JSForge.contextPath) || "";
    var api = ctx + "/api/projects";

    function el(tag, className, text) {
        var node = document.createElement(tag);
        if (className) { node.className = className; }
        if (text != null) { node.textContent = String(text); } // textContent 로만
        return node;
    }

    function clear(node) {
        while (node.firstChild) { node.removeChild(node.firstChild); }
    }

    function emptyRow(text) {
        var tr = el("tr");
        var td = el("td", "frg-empty", text);
        td.colSpan = 5;
        tr.appendChild(td);
        return tr;
    }

    function snack(text) {
        if (window.JWORKS_JSSnackBar && typeof window.JWORKS_JSSnackBar.create === "function") {
            window.JWORKS_JSSnackBar.create(String(text));
        }
    }

    function renderRows(list) {
        var tbody = document.getElementById("frg-project-rows");
        clear(tbody);
        if (!Array.isArray(list) || !list.length) {
            tbody.appendChild(emptyRow("등록된 프로젝트가 없습니다."));
            return;
        }
        list.forEach(function (p) {
            var tr = el("tr");
            tr.appendChild(el("td", null, p.projectId));
            tr.appendChild(el("td", null, p.projectName));
            var pathTd = el("td", "frg-cell-path", p.targetRootPath);
            tr.appendChild(pathTd);
            // 패키지 베이스가 비어 있으면 파일 생성이 거부되므로 목록에서 바로 눈에 띄게 한다.
            if (p.packageBase) {
                tr.appendChild(el("td", "frg-cell-path", p.packageBase));
            } else {
                var warnTd = el("td", "frg-cell-path");
                var warn = el("span", "frg-msg warn", "미설정 — 생성 불가");
                warn.title = "생성될 Controller/Mapper 의 패키지 경로입니다. '수정'에서 채워 주세요.";
                warnTd.appendChild(warn);
                tr.appendChild(warnTd);
            }
            var actTd = el("td");
            var edit = el("button", "frg-btn frg-btn-secondary frg-btn-sm", "수정");
            edit.type = "button";
            edit.setAttribute("data-edit", String(p.projectId));
            actTd.appendChild(edit);
            var db = el("button", "frg-btn frg-btn-secondary frg-btn-sm", "DB 연결");
            db.type = "button";
            db.setAttribute("data-db", String(p.projectId));
            db.setAttribute("data-name", String(p.projectName == null ? "" : p.projectName));
            actTd.appendChild(db);
            var del = el("button", "frg-btn frg-btn-danger frg-btn-sm", "삭제");
            del.type = "button";
            del.setAttribute("data-del", String(p.projectId));
            del.setAttribute("data-name", String(p.projectName == null ? "" : p.projectName));
            actTd.appendChild(del);
            tr.appendChild(actTd);
            tbody.appendChild(tr);
        });
    }

    function load() {
        fetch(api, { headers: { "Accept": "application/json" } })
            .then(function (r) { if (!r.ok) { throw new Error("http " + r.status); } return r.json(); })
            .then(renderRows)
            .catch(function () {
                var tbody = document.getElementById("frg-project-rows");
                clear(tbody);
                tbody.appendChild(emptyRow("목록을 불러오지 못했습니다."));
            });
    }

    // TARGET_ROOT_PATH 절대경로 사전 힌트 (경로안전 계층 규약 반영)
    function looksAbsolute(v) {
        return /^[a-zA-Z]:[\\/]/.test(v) || /^[\\/]{2}/.test(v) || /^\//.test(v);
    }

    // ---------- 수정 모드(PUT /api/projects/{id}) ----------
    // 등록 폼을 그대로 재사용한다. 이 화면에 수정 수단이 없어서, 패키지 베이스가 빈 채로 만들어진
    // 프로젝트는 삭제 후 재생성 외에는 고칠 방법이 없었다(파일 생성이 그 값 없이는 거부된다).
    var editingId = null;

    function enterEditMode(project) {
        editingId = project.projectId;
        var form = document.getElementById("frg-project-form");
        form.elements["projectName"].value = project.projectName || "";
        form.elements["targetRootPath"].value = project.targetRootPath || "";
        form.elements["packageBase"].value = project.packageBase || "";
        document.getElementById("frg-form-title").textContent =
            "프로젝트 수정 (#" + project.projectId + ")";
        document.getElementById("frg-form-submit").textContent = "수정 저장";
        document.getElementById("frg-form-cancel").hidden = false;
        var msg = document.getElementById("frg-form-msg");
        if (!project.packageBase) {
            msg.textContent = "패키지 베이스가 비어 있습니다 — 파일 생성을 하려면 채워야 합니다.";
            msg.className = "frg-msg warn";
        } else {
            msg.textContent = "";
            msg.className = "frg-msg";
        }
        form.elements["projectName"].focus();
        form.scrollIntoView({ block: "nearest" });
    }

    function exitEditMode() {
        editingId = null;
        var form = document.getElementById("frg-project-form");
        form.reset();
        document.getElementById("frg-path-hint").textContent = "";
        document.getElementById("frg-form-title").textContent = "프로젝트 등록";
        document.getElementById("frg-form-submit").textContent = "저장";
        document.getElementById("frg-form-cancel").hidden = true;
        var msg = document.getElementById("frg-form-msg");
        msg.textContent = "";
        msg.className = "frg-msg";
    }

    function loadForEdit(id) {
        fetch(api + "/" + encodeURIComponent(id), { headers: { "Accept": "application/json" } })
            .then(function (r) { if (!r.ok) { throw new Error("http " + r.status); } return r.json(); })
            .then(enterEditMode)
            .catch(function () {
                var msg = document.getElementById("frg-form-msg");
                msg.textContent = "프로젝트를 불러오지 못했습니다.";
                msg.className = "frg-msg warn";
            });
    }

    function deleteProject(id, name) {
        var label = name ? "'" + name + "' 프로젝트를 삭제할까요?" : "프로젝트를 삭제할까요?";
        window.JWORKS_JSConfirm.start("프로젝트 삭제", label + " (화면 정의는 DB에 남습니다)", function (result) {
            if (!result) { return; }
            fetch(api + "/" + encodeURIComponent(id), { method: "DELETE" })
                .then(function (r) {
                    if (r.ok) { snack("프로젝트가 삭제되었습니다."); }
                    load();
                })
                .catch(function () { load(); });
        });
    }

    function bind() {
        var form = document.getElementById("frg-project-form");
        var pathInput = form.elements["targetRootPath"];
        var hint = document.getElementById("frg-path-hint");

        pathInput.addEventListener("input", function () {
            var v = pathInput.value.trim();
            if (!v) { hint.textContent = ""; hint.className = "frg-hint"; return; }
            if (looksAbsolute(v)) { hint.textContent = "✓ 절대경로"; hint.className = "frg-hint ok"; }
            else { hint.textContent = "⚠ 절대경로가 아닙니다 (예: C:\\path 또는 /path)"; hint.className = "frg-hint warn"; }
        });

        form.addEventListener("submit", function (e) {
            e.preventDefault();
            var msg = document.getElementById("frg-form-msg");
            var body = {
                projectName: form.elements["projectName"].value.trim(),
                targetRootPath: form.elements["targetRootPath"].value.trim(),
                packageBase: form.elements["packageBase"].value.trim(),
                jspBasePath: "jsp",
                jsBasePath: "js",
                cssBasePath: "css"
            };
            // 수정 모드면 PUT, 아니면 POST. 같은 폼을 재사용한다.
            var editing = editingId != null;
            fetch(editing ? (api + "/" + encodeURIComponent(editingId)) : api, {
                method: editing ? "PUT" : "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(body)
            }).then(function (r) {
                return r.json().then(function (j) { return { ok: r.ok, j: j }; });
            }).then(function (res) {
                if (res.ok) {
                    msg.textContent = "";
                    msg.className = "frg-msg";
                    snack(editing
                        ? "프로젝트가 수정되었습니다."
                        : "프로젝트가 저장되었습니다. (id=" + res.j.projectId + ")");
                    exitEditMode();
                    load();
                } else {
                    msg.textContent = res.j.message || "저장 실패";
                    msg.className = "frg-msg warn";
                }
            }).catch(function () { msg.textContent = "네트워크 오류"; msg.className = "frg-msg warn"; });
        });

        document.getElementById("frg-form-cancel").addEventListener("click", exitEditMode);

        document.getElementById("frg-project-rows").addEventListener("click", function (e) {
            var target = e.target;
            var editBtn = target.closest ? target.closest("[data-edit]") : null;
            if (editBtn) {
                loadForEdit(editBtn.getAttribute("data-edit"));
                return;
            }
            var dbBtn = target.closest ? target.closest("[data-db]") : null;
            if (dbBtn) {
                openDbModal(dbBtn.getAttribute("data-db"), dbBtn.getAttribute("data-name"));
                return;
            }
            var btn = target.closest ? target.closest("[data-del]") : null;
            if (!btn) { return; }
            deleteProject(btn.getAttribute("data-del"), btn.getAttribute("data-name"));
        });

        bindDbModal();
    }

    // ---------- P11: 타겟 DB 연결 설정 ----------
    // JDBC URL은 다루지 않는다 — host/port/database 조각만 보내고 서버가 조립한다(계약 §15).

    var dbProjectId = null;

    function dbApi(suffix) {
        return api + "/" + encodeURIComponent(dbProjectId) + "/db" + (suffix || "");
    }

    function dbForm() { return document.getElementById("frg-db-form"); }

    function dbMessage(text, kind) {
        var msg = document.getElementById("frg-db-msg");
        msg.textContent = text || "";
        msg.className = "frg-msg" + (kind ? " frg-msg-" + kind : "");
    }

    function dbFields() {
        var form = dbForm();
        return {
            host: form.elements["host"].value.trim(),
            port: Number(form.elements["port"].value),
            database: form.elements["database"].value.trim(),
            schema: form.elements["schema"].value.trim(),
            username: form.elements["username"].value.trim(),
            password: form.elements["password"].value
        };
    }

    function openDbModal(projectId, projectName) {
        dbProjectId = projectId;
        var backdrop = document.getElementById("frg-db-backdrop");
        var form = dbForm();
        form.reset();
        dbMessage("");
        document.getElementById("frg-db-target").textContent =
            (projectName ? projectName + " · " : "") + "프로젝트 #" + projectId;
        backdrop.hidden = false;

        fetch(dbApi(), { headers: { "Accept": "application/json" } })
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function (view) {
                if (!view) { return; }
                if (!view.secretAvailable) {
                    dbMessage("암호화 키를 준비하지 못해 연결 설정을 저장할 수 없습니다.", "err");
                }
                if (!view.configured) { return; }
                form.elements["host"].value = view.host || "";
                form.elements["port"].value = view.port == null ? "" : view.port;
                form.elements["database"].value = view.database || "";
                form.elements["schema"].value = view.schema || "";
                form.elements["username"].value = view.username || "";
                // 비밀번호는 서버가 돌려주지 않는다 — 비워둔 채로 두면 기존 값이 유지된다.
            })
            .catch(function () { dbMessage("설정을 불러오지 못했습니다.", "warn"); });
    }

    function closeDbModal() {
        document.getElementById("frg-db-backdrop").hidden = true;
        dbProjectId = null;
    }

    function postDb(suffix, method, onOk) {
        if (!dbForm().reportValidity()) { return; }
        dbMessage("요청 중…");
        fetch(dbApi(suffix), {
            method: method,
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(dbFields())
        }).then(function (r) {
            return r.json().then(function (j) { return { ok: r.ok, j: j }; });
        }).then(function (res) {
            if (!res.ok) {
                dbMessage(res.j.message || "요청에 실패했습니다.", "err");
                return;
            }
            onOk(res.j);
        }).catch(function () { dbMessage("네트워크 오류", "err"); });
    }

    function bindDbModal() {
        var backdrop = document.getElementById("frg-db-backdrop");
        if (!backdrop) { return; }

        document.getElementById("frg-db-close").addEventListener("click", closeDbModal);
        backdrop.addEventListener("click", function (e) {
            if (e.target === backdrop) { closeDbModal(); }
        });
        document.addEventListener("keydown", function (e) {
            if (e.key === "Escape" && !backdrop.hidden) { closeDbModal(); }
        });

        document.getElementById("frg-db-test").addEventListener("click", function () {
            postDb("/test", "POST", function (result) {
                if (result.success) {
                    dbMessage("연결 성공 · " + (result.productName || ""), "ok");
                } else {
                    dbMessage(result.message || "연결 실패", "err");
                }
            });
        });

        document.getElementById("frg-db-save").addEventListener("click", function () {
            postDb("", "PUT", function () {
                snack("DB 연결 정보가 저장되었습니다.");
                closeDbModal();
            });
        });

        document.getElementById("frg-db-delete").addEventListener("click", function () {
            var id = dbProjectId;
            window.JWORKS_JSConfirm.start("연결 해제", "저장된 DB 접속정보를 삭제할까요?", function (result) {
                if (!result || id == null) { return; }
                fetch(api + "/" + encodeURIComponent(id) + "/db", { method: "DELETE" })
                    .then(function () {
                        snack("DB 접속정보가 삭제되었습니다.");
                        closeDbModal();
                    })
                    .catch(function () { dbMessage("삭제에 실패했습니다.", "err"); });
            });
        });
    }

    document.addEventListener("DOMContentLoaded", function () { bind(); load(); });
})(window.JWorks_JSForgeAdminProject);
