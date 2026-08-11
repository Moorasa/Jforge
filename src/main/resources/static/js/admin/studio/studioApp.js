/* ===============================================================================================
Name : studioApp.js
Description : 3-pane 스튜디오 셸 + 오케스트레이션 허브 (P3-3).
             - 프로젝트 목록(/api/projects) → 화면 목록(/api/screens?projectId=) → 단건(/api/screens/{id}) 로드.
             - 선택된 화면/DEFINITION_JSON/dirty 플래그를 이 모듈이 편집중 상태로 보유.
             - 새 화면 생성(POST /api/screens) 인라인 폼.
             - 하위 모듈(팔레트 P3-5 / 프리뷰 P3-6 / 속성 P3-4)이 붙는 pub/sub 허브 API 제공.

오케스트레이터 공개 API (P3-4/P3-5/P3-6이 참조):
  JWorks_JSForgeAdminStudio.onScreenLoaded(cb)        // cb(screen) — 화면 단건 로드 완료 시
  JWorks_JSForgeAdminStudio.onDefinitionChanged(cb)   // cb(def, meta) — DEFINITION_JSON 변경 시
  JWorks_JSForgeAdminStudio.getScreen()               // 현재 편집중 화면 메타 객체(없으면 null)
  JWorks_JSForgeAdminStudio.getDefinitionJson()       // 현재 DEFINITION_JSON 객체(없으면 null)
  JWorks_JSForgeAdminStudio.updateDefinitionJson(def, meta) // in-memory 갱신 + dirty + 통지
  JWorks_JSForgeAdminStudio.isDirty()                 // 미저장 변경 여부
  JWorks_JSForgeAdminStudio.markSaved()               // 저장 완료 후 dirty 해제(P3-6b)

XSS: 모든 텍스트 삽입은 textContent 로만. innerHTML(및 jQuery .html()) 미사용.
=============================================================================================== */
window.JWorks_JSForgeAdminStudio = window.JWorks_JSForgeAdminStudio || {};
(function (studio) {
    "use strict";
    if (studio.__defined) { return; }
    studio.__defined = true;

    var ctx = (window.JWorks_JSForge && window.JWorks_JSForge.contextPath) || "";
    var apiProjects = ctx + "/api/projects";
    var apiScreens = ctx + "/api/screens";
    var projectsById = {};

    // --- 편집중 상태(이 모듈이 소유) ---
    var state = {
        projectId: null,   // 선택된 프로젝트 id
        project: null,     // 선택된 프로젝트 메타(파일 생성 위치 변경 시 PUT 원본)
        screen: null,      // 선택된 화면 메타 객체(단건 응답)
        definition: null,  // 현재 DEFINITION_JSON(객체)
        dirty: false       // 미저장 변경 여부
    };

    // --- pub/sub 구독자 목록 ---
    var subs = { screenLoaded: [], definitionChanged: [] };

    // --- undo/redo 히스토리(P7-3) ---
    // 스냅샷은 JSON 직렬화 문자열로 보관(외부 참조에 의한 변형 차단 + 왕복 무손상).
    var HISTORY_CAP = 50;
    var history = { past: [], future: [] };

    function snapshot(def) {
        try { return JSON.stringify(def); } catch (e) { return null; }
    }

    function resetHistory() {
        history.past = [];
        history.future = [];
        refreshUndoButtons();
    }

    function pushHistory(prevDef) {
        var snap = snapshot(prevDef);
        if (snap == null) { return; }
        history.past.push(snap);
        if (history.past.length > HISTORY_CAP) { history.past.shift(); }
        history.future = []; // 새 편집이 들어오면 redo 가지 소멸
        refreshUndoButtons();
    }

    function refreshUndoButtons() {
        var u = $("frg-btn-undo");
        var r = $("frg-btn-redo");
        if (u) { u.disabled = !(state.screen && history.past.length); }
        if (r) { r.disabled = !(state.screen && history.future.length); }
    }

    function notify(list, a, b) {
        list.forEach(function (cb) {
            try { cb(a, b); } catch (e) { /* 구독자 오류 격리 */ }
        });
    }

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

    function opt(value, label) {
        var o = document.createElement("option");
        o.value = value == null ? "" : String(value);
        o.textContent = label == null ? "" : String(label); // 옵션 텍스트도 textContent
        return o;
    }

    function $(id) { return document.getElementById(id); }

    function setDirty(flag) {
        state.dirty = !!flag;
        var badge = $("frg-dirty-flag");
        if (badge) { badge.hidden = !state.dirty; }
        refreshSaveButton();
    }

    // 저장 버튼 활성/비활성: 화면이 선택되어 있고 미저장 변경이 있을 때만 활성.
    function refreshSaveButton() {
        var btn = $("frg-btn-save");
        if (!btn) { return; }
        btn.disabled = !(state.screen && state.dirty);
    }

    // 생성 버튼 활성/비활성(P4-5): 화면이 선택되어 있으면 활성(생성은 DB 저장본 기준 파일쓰기).
    // P9: 실행 미리보기 버튼도 같은 조건(화면 선택 시 활성 — 저장본 기준 렌더).
    function refreshGenerateButton() {
        var btn = $("frg-btn-generate");
        if (btn) { btn.disabled = !(state.screen && state.screen.screenId) || generating; }
        var rp = $("frg-btn-run-preview");
        if (rp) { rp.disabled = !(state.screen && state.screen.screenId); }
    }

    // ---------- 번들 위젯 헬퍼(P7-1: window.confirm/alert 대체) ----------
    // 확인창은 번들 JWORKS_JSConfirm(title, text, cb(result)) 경유. 위젯 미로드 시 네이티브 폴백.
    function confirmDialog(title, text, cb) {
        if (window.JWORKS_JSConfirm && typeof window.JWORKS_JSConfirm.start === "function") {
            window.JWORKS_JSConfirm.start(String(title == null ? "" : title), String(text), function (result) {
                cb(!!result);
            });
            return;
        }
        cb(window.confirm(String(text)));
    }

    // 성공 알림은 번들 스낵바(짧은 토스트). 미로드 시 무시(오류는 별도 영역에 유지 표기).
    function snack(text) {
        if (window.JWORKS_JSSnackBar && typeof window.JWORKS_JSSnackBar.create === "function") {
            window.JWORKS_JSSnackBar.create(String(text));
        }
    }

    // ---------- 저장 알림 영역(자체 메시지, textContent 로만) ----------
    // 성공은 스낵바로 승격(P7-1). 이 영역은 오류(유지 표기)와 진행중 상태 전용.
    var SAVE_MSG_TIMER = null;
    function showSaveMsg(text, kind) {
        var box = $("frg-save-msg");
        if (!box) { return; }
        box.textContent = String(text == null ? "" : text); // textContent 로만
        box.className = "frg-save-msg" + (kind ? " frg-save-msg-" + kind : "");
        box.hidden = !text;
        if (SAVE_MSG_TIMER) { clearTimeout(SAVE_MSG_TIMER); SAVE_MSG_TIMER = null; }
        // 성공 메시지는 잠시 후 자동 숨김(오류는 유지해 사용자가 읽도록).
        if (text && kind === "ok") {
            SAVE_MSG_TIMER = setTimeout(function () {
                box.hidden = true;
                box.textContent = "";
            }, 4000);
        }
    }

    // ---------- 프로젝트 로드 ----------
    // 완료 시점에 후속 작업(신규 프로젝트 자동 선택 등)을 잇도록 Promise 를 돌려준다(P7-5).
    function loadProjects() {
        var sel = $("frg-project-select");
        return fetch(apiProjects, { headers: { "Accept": "application/json" } })
            .then(function (r) { if (!r.ok) { throw new Error("http " + r.status); } return r.json(); })
            .then(function (list) {
                clear(sel);
                sel.appendChild(opt("", "프로젝트 선택…"));
                projectsById = {};
                if (Array.isArray(list)) {
                    list.forEach(function (p) {
                        projectsById[String(p.projectId)] = p;
                        sel.appendChild(opt(p.projectId, p.projectName));
                    });
                    if (state.projectId) {
                        state.project = projectsById[String(state.projectId)] || null;
                    }
                }
                return list;
            })
            .catch(function () {
                clear(sel);
                sel.appendChild(opt("", "프로젝트를 불러오지 못했습니다"));
                return [];
            });
    }

    // ---------- 화면 목록 로드 ----------
    // 목록 반영 완료 시점에 후속 작업(신규 화면 선택 등)을 잇도록 Promise 를 돌려준다(P7-3:
    // 기존 setInterval 재시도 폴링 제거의 근거).
    function loadScreens(projectId) {
        var sel = $("frg-screen-select");
        clear(sel);
        sel.appendChild(opt("", "불러오는 중…"));
        sel.disabled = true;
        var url = apiScreens + "?projectId=" + encodeURIComponent(projectId);
        return fetch(url, { headers: { "Accept": "application/json" } })
            .then(function (r) { if (!r.ok) { throw new Error("http " + r.status); } return r.json(); })
            .then(function (list) {
                clear(sel);
                sel.appendChild(opt("", "화면 선택…"));
                if (Array.isArray(list) && list.length) {
                    list.forEach(function (s) {
                        // "이름 (stem)" 라벨. 모두 textContent 경유(opt).
                        sel.appendChild(opt(s.screenId, s.screenName + " (" + s.stem + ")"));
                    });
                }
                sel.disabled = false;
                return list;
            })
            .catch(function () {
                clear(sel);
                sel.appendChild(opt("", "화면을 불러오지 못했습니다"));
                return [];
            });
    }

    // ---------- 화면 단건 로드 ----------
    function parseDefinition(raw) {
        // 단건 응답의 definitionJson 은 @JsonRawValue 로 이미 JSON 트리(객체)로 실려온다.
        if (raw == null) { return null; }
        if (typeof raw === "string") {
            try { return JSON.parse(raw); } catch (e) { return null; }
        }
        return raw; // 정상 경로: 이미 객체
    }

    // P7-5: 새 화면 "기본 구성" 프리셋 예약분. loadScreen 완료 시 1회 적용 후 소거.
    var pendingPreset = null; // { screenId, archetype } | null

    function loadScreen(screenId) {
        fetch(apiScreens + "/" + encodeURIComponent(screenId), { headers: { "Accept": "application/json" } })
            .then(function (r) { if (!r.ok) { throw new Error("http " + r.status); } return r.json(); })
            .then(function (s) {
                state.screen = s;
                state.definition = parseDefinition(s.definitionJson);
                showSaveMsg("", null); // 새 화면 로드 시 이전 저장 메시지 소거
                setDirty(false);       // 로드 직후엔 clean → 저장 버튼 비활성 유지
                resetHistory();        // 화면 전환 = 히스토리 리셋(P7-3)
                refreshGenerateButton(); // 화면 로드 → 생성 버튼 활성
                resetGenPanel();         // 이전 화면의 생성 결과/이력 소거
                // 오케스트레이션: 하위 모듈에 화면 로드 + DEFINITION_JSON 배포
                notify(subs.screenLoaded, state.screen);
                notify(subs.definitionChanged, state.definition, { reason: "screenLoaded" });
                // P7-5: 신규 화면의 기본 구성 프리셋 적용(팔레트 위임, 1회).
                if (pendingPreset && String(pendingPreset.screenId) === String(s.screenId)) {
                    var preset = pendingPreset;
                    pendingPreset = null;
                    var palette = window.JWorks_JSForgeAdminStudioPalette;
                    if (palette && typeof palette.applyPreset === "function") {
                        palette.applyPreset(preset.archetype);
                    }
                } else {
                    pendingPreset = null; // 다른 화면 로드가 끼어들면 예약 소거
                }
            })
            .catch(function () { /* 단건 로드 실패는 조용히(상단 select 유지) */ });
    }

    // ---------- 새 화면 생성 ----------
    function showNewForm(show) {
        var form = $("frg-new-screen-form");
        if (form) { form.hidden = !show; }
        var msg = $("frg-nsf-msg");
        if (msg) { msg.textContent = ""; }
    }

    // 새 화면 모달의 라디오 그룹(화면 유형/사용 영역) 선택값(P7-5).
    function checkedValue(name, fallback) {
        var input = document.querySelector('input[name="' + name + '"]:checked');
        return input ? input.value : fallback;
    }

    function createScreen(ev) {
        ev.preventDefault();
        var msg = $("frg-nsf-msg");
        if (msg) { msg.textContent = ""; }
        if (!state.projectId) {
            if (msg) { msg.textContent = "프로젝트를 먼저 선택하세요."; }
            return;
        }
        var body = {
            projectId: Number(state.projectId),
            screenName: $("frg-nsf-name").value.trim(),
            stem: $("frg-nsf-stem").value.trim(),
            archetypeCode: checkedValue("frg-nsf-archetype", "MGMT_LIST_DETAIL"),
            roleCode: checkedValue("frg-nsf-role", "admin")
        };
        // P7-5: 기본 구성 프리셋 — 화면 로드 완료 후 팔레트가 기본 모듈을 배치한다.
        var presetBox = $("frg-nsf-preset");
        var usePreset = !!(presetBox && presetBox.checked);
        fetch(apiScreens, {
            method: "POST",
            headers: { "Content-Type": "application/json", "Accept": "application/json" },
            body: JSON.stringify(body)
        })
            .then(function (r) {
                if (!r.ok) { throw new Error("http " + r.status); }
                return r.json();
            })
            .then(function (created) {
                showNewForm(false);
                $("frg-nsf-name").value = "";
                $("frg-nsf-stem").value = "";
                // P7-5: 기본 구성 프리셋 예약 — 화면 로드 완료 시 팔레트가 기본 모듈을 배치한다.
                if (usePreset) {
                    pendingPreset = { screenId: created.screenId, archetype: body.archetypeCode };
                }
                // 신규 화면 즉시 단건 로드 + 목록 재로드 완료 후 select 값 반영(P7-3: 폴링 제거).
                loadScreen(created.screenId);
                loadScreens(state.projectId).then(function () {
                    $("frg-screen-select").value = String(created.screenId);
                });
                snack("화면이 만들어졌습니다.");
            })
            .catch(function () {
                if (msg) { msg.textContent = "생성에 실패했습니다. 입력값(stem 형식 등)을 확인하세요."; }
            });
    }

    // ---------- 저장(P3-6b) ----------
    // 현재 in-memory DEFINITION_JSON 을 PUT /api/screens/{id}/definition 으로 저장.
    // 바디 = DEFINITION_JSON 전체 raw JSON(서버가 @RequestBody String 무가공 수신).
    // 서버가 P3-5b 구조검증을 경유하므로, 검증 위반 시 400 message 를 오류로 표기.
    var saving = false;
    function saveDefinition() {
        if (saving) { return; }
        var screen = studio.getScreen();
        var def = studio.getDefinitionJson();
        if (!screen || !screen.screenId || def == null) {
            showSaveMsg("저장할 화면이 없습니다. 화면을 먼저 선택하세요.", "err");
            return;
        }
        saving = true;
        var btn = $("frg-btn-save");
        if (btn) { btn.disabled = true; }
        showSaveMsg("저장 중…", null);

        var url = apiScreens + "/" + encodeURIComponent(screen.screenId) + "/definition";
        var raw;
        try {
            raw = JSON.stringify(def); // DEFINITION_JSON 전체 직렬화
        } catch (e) {
            saving = false;
            refreshSaveButton();
            showSaveMsg("DEFINITION_JSON 직렬화에 실패했습니다.", "err");
            return;
        }

        fetch(url, {
            method: "PUT",
            headers: { "Content-Type": "application/json", "Accept": "application/json" },
            body: raw
        })
            .then(function (r) {
                // 2xx: 저장 후 단건 응답, 그 외: {message} 오류 바디
                return r.json().then(function (payload) {
                    return { ok: r.ok, status: r.status, payload: payload };
                }, function () {
                    return { ok: r.ok, status: r.status, payload: null };
                });
            })
            .then(function (res) {
                saving = false;
                if (res.ok && res.payload) {
                    // 성공: 응답으로 받은 최신 DEFINITION_JSON 으로 내부 상태 갱신
                    var saved = res.payload;
                    state.screen = saved;
                    state.definition = parseDefinition(saved.definitionJson);
                    studio.markSaved(); // dirty 해제 → refreshSaveButton 이 버튼 비활성
                    // 하위 모듈에 저장 후 정규화된 DEFINITION_JSON 재배포
                    notify(subs.definitionChanged, state.definition, { reason: "saved" });
                    showSaveMsg("", null);
                    snack("저장되었습니다.");
                } else {
                    // 실패: 400(검증 위반) 등. message(취합된 위반 사유)를 textContent 로 표기.
                    var msg = (res.payload && res.payload.message)
                        ? res.payload.message
                        : ("저장에 실패했습니다. (HTTP " + res.status + ")");
                    showSaveMsg(msg, "err");
                    refreshSaveButton(); // 여전히 dirty → 재시도 가능하도록 버튼 복구
                }
            })
            .catch(function () {
                saving = false;
                showSaveMsg("저장 요청에 실패했습니다. 네트워크 상태를 확인하세요.", "err");
                refreshSaveButton();
            });
    }

    // ---------- 생성(P4-5) ----------
    // 생성 = 파일쓰기(저장과 별개). POST /api/screens/{id}/generate → GEN_HIST 1행 기록.
    // 결과(파일 목록/성공·실패)는 textContent/createElement 로만 표시(innerHTML 미사용, XSS 0).
    var generating = false;

    function setGenStatus(text, kind) {
        var box = $("frg-gen-status");
        if (!box) { return; }
        box.textContent = String(text == null ? "" : text); // textContent 로만
        box.className = "frg-gen-status" + (kind ? " frg-gen-status-" + kind : "");
    }

    // resultCode → 상태 클래스 매핑(자유문자열을 클래스로 쓰지 않도록 화이트리스트).
    function genStatusKind(resultCode) {
        if (resultCode === "SUCCESS") { return "ok"; }
        if (resultCode === "PARTIAL") { return "partial"; }
        return "fail";
    }

    // 생성 파일 목록 렌더: 각 항목을 createElement + textContent 로만 조립.
    function renderGenFiles(files, failReason) {
        var ul = $("frg-gen-files");
        if (!ul) { return; }
        clear(ul);
        if (!Array.isArray(files) || !files.length) {
            ul.appendChild(el("li", "frg-gen-file-fail", "쓰인 파일이 없습니다."));
            // 실패 사유(예: 타겟 경로 미존재)를 표시해 원인을 바로 알 수 있게 한다. textContent 로만 삽입.
            if (failReason != null && String(failReason).length) {
                ul.appendChild(el("li", "frg-gen-file-reason", "사유: " + String(failReason)));
            }
            return;
        }
        files.forEach(function (f) {
            var ok = !!(f && f.success);
            var li = el("li", ok ? "frg-gen-file-ok" : "frg-gen-file-fail");
            // 서버응답 문자열(경로/사유)은 무조건 textContent 로만 삽입.
            li.appendChild(el("span", null, (ok ? "[OK] " : "[FAIL] ") + String(f && f.relativePath != null ? f.relativePath : "(경로 없음)")));
            if (!ok && f && f.reason != null) {
                li.appendChild(el("span", "frg-gen-file-reason", String(f.reason)));
            }
            ul.appendChild(li);
        });
    }

    // 생성 이력 목록 렌더(최신순 메타). 서버응답 문자열은 textContent 로만.
    function renderGenHistory(list) {
        var ul = $("frg-gen-history");
        if (!ul) { return; }
        clear(ul);
        if (!Array.isArray(list) || !list.length) {
            ul.appendChild(el("li", null, "생성 이력이 없습니다."));
            return;
        }
        list.forEach(function (h) {
            var li = el("li");
            li.appendChild(el("span", null, "#" + String(h && h.genHistId != null ? h.genHistId : "?")));
            li.appendChild(el("span", null, String(h && h.resultCode != null ? h.resultCode : "")));
            li.appendChild(el("span", null, String(h && h.genAt != null ? h.genAt : "")));
            ul.appendChild(li);
        });
    }

    // 화면 전환 시 생성 결과/이력 슬롯을 비우고 패널을 숨긴다.
    function resetGenPanel() {
        var panel = $("frg-gen-panel");
        if (panel) { panel.hidden = true; }
        setGenStatus("", null);
        var filesUl = $("frg-gen-files");
        if (filesUl) { clear(filesUl); }
        var histUl = $("frg-gen-history");
        if (histUl) { clear(histUl); }
    }

    function loadGenHistory(screenId) {
        var url = apiScreens + "/" + encodeURIComponent(screenId) + "/gen-history";
        fetch(url, { headers: { "Accept": "application/json" } })
            .then(function (r) { if (!r.ok) { throw new Error("http " + r.status); } return r.json(); })
            .then(function (list) { renderGenHistory(list); })
            .catch(function () { /* 이력 조회 실패는 조용히(패널 유지) */ });
    }

    function generateScreen() {
        if (generating) { return; }
        var screen = studio.getScreen();
        if (!screen || !screen.screenId) {
            return;
        }
        // dirty(미저장) 상태 경고: 생성은 DB 저장본 기준이라 미저장 변경은 반영되지 않는다.
        if (state.dirty) {
            confirmDialog("생성", "저장하지 않은 변경이 있습니다. 생성은 저장된 내용 기준으로 실행됩니다. 계속하시겠습니까?", function (ok) {
                if (ok) { showGeneratePlan(screen); }
            });
            return;
        }
        showGeneratePlan(screen);
    }

    // ---------- 생성 dry-run 계획 모달(P7-4) ----------
    // 파일을 쓰기 전에 "신규 n · 덮어쓰기 m" 목록을 보여주고 확정 시에만 실제 생성한다.
    // 서버응답 문자열(경로)은 전부 textContent 로만 삽입(XSS 0).
    var planBackdrop = null;

    function closePlanModal() {
        if (planBackdrop && planBackdrop.parentNode) {
            planBackdrop.parentNode.removeChild(planBackdrop);
        }
        planBackdrop = null;
    }

    // 생성 위치는 서버가 실행되는 컴퓨터의 절대경로다. 브라우저는 보안상 로컬 폴더의
    // 절대경로를 폴더 선택창에서 돌려주지 않으므로, 경로 입력 + 서버 최종검증 방식을 쓴다.
    function looksAbsolutePath(value) {
        return /^[a-zA-Z]:[\\/]/.test(value) || /^[\\/]{2}/.test(value) || /^\//.test(value);
    }

    function projectUpdateBody(project, targetRootPath) {
        return {
            projectName: project.projectName,
            targetRootPath: targetRootPath,
            packageBase: project.packageBase,
            jspBasePath: project.jspBasePath,
            jsBasePath: project.jsBasePath,
            cssBasePath: project.cssBasePath,
            dbTypeCode: project.dbTypeCode,
            runtimeVer: project.runtimeVer
        };
    }

    // 위치 변경은 임시 우회경로로 생성기에 넘기지 않고 프로젝트의 정식 PUT API로 저장한다.
    // 저장 후 새 루트 기준 dry-run을 다시 열어 덮어쓰기 여부를 재확인하게 한다.
    function saveGenerateLocation(screen, project, targetRootPath, runButton, errorBox) {
        runButton.disabled = true;
        runButton.textContent = "위치 저장 중…";
        errorBox.textContent = "";
        fetch(apiProjects + "/" + encodeURIComponent(project.projectId), {
            method: "PUT",
            headers: { "Content-Type": "application/json", "Accept": "application/json" },
            body: JSON.stringify(projectUpdateBody(project, targetRootPath))
        }).then(function (r) {
            return r.json().then(function (payload) {
                return { ok: r.ok, status: r.status, payload: payload };
            }, function () {
                return { ok: r.ok, status: r.status, payload: null };
            });
        }).then(function (res) {
            if (!res.ok || !res.payload) {
                runButton.disabled = false;
                runButton.textContent = "위치 적용 후 다시 확인";
                errorBox.textContent = (res.payload && res.payload.message)
                    ? String(res.payload.message)
                    : "저장 위치를 변경하지 못했습니다. (HTTP " + res.status + ")";
                return;
            }
            state.project = res.payload;
            projectsById[String(res.payload.projectId)] = res.payload;
            closePlanModal();
            snack("파일 생성 위치를 변경했습니다.");
            showGeneratePlan(screen);
        }).catch(function () {
            runButton.disabled = false;
            runButton.textContent = "위치 적용 후 다시 확인";
            errorBox.textContent = "네트워크 오류로 저장 위치를 변경하지 못했습니다.";
        });
    }

    // ---------- P12: 계획 항목 1건(드리프트 뱃지 + diff/복원) ----------

    function planFileRow(screen, f) {
        var li = el("li");
        var exists = !!(f && f.exists);
        li.appendChild(el("span", "frg-plan-tag " + (exists ? "frg-plan-tag-overwrite" : "frg-plan-tag-new"),
            exists ? "덮어쓰기" : "신규"));
        if (f && f.drift === "MODIFIED") {
            li.appendChild(el("span", "frg-plan-tag frg-plan-tag-drift", "외부 수정됨"));
        }
        li.appendChild(el("span", null, String(f && f.relativePath != null ? f.relativePath : "(경로 없음)")));

        if (!exists || !f.artifactKey) {
            return li;
        }

        var detail = el("div", "frg-plan-diff");
        detail.hidden = true;
        var toggle = el("button", "frg-btn frg-btn-secondary frg-btn-sm", "변경 보기");
        toggle.type = "button";
        toggle.addEventListener("click", function () {
            if (!detail.hidden) {
                detail.hidden = true;
                toggle.textContent = "변경 보기";
                return;
            }
            detail.hidden = false;
            toggle.textContent = "접기";
            if (detail.getAttribute("data-loaded")) { return; }
            detail.setAttribute("data-loaded", "1");
            loadDiff(screen, f.artifactKey, detail);
        });
        li.appendChild(toggle);
        li.appendChild(detail);
        return li;
    }

    function loadDiff(screen, artifactKey, container) {
        clear(container);
        container.appendChild(el("p", "frg-desc", "불러오는 중…"));
        var base = apiScreens + "/" + encodeURIComponent(screen.screenId) + "/generate";
        fetch(base + "/diff?artifactKey=" + encodeURIComponent(artifactKey),
            { headers: { "Accept": "application/json" } })
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function (view) {
                clear(container);
                if (!view) {
                    container.appendChild(el("p", "frg-desc", "변경 내용을 불러오지 못했습니다."));
                    return;
                }
                if (view.identical) {
                    container.appendChild(el("p", "frg-desc", "생성해도 내용이 바뀌지 않습니다."));
                } else if (view.tooLarge) {
                    container.appendChild(el("p", "frg-desc", "차이가 너무 커서 표시하지 않습니다."));
                } else {
                    (view.hunks || []).forEach(function (hunk) {
                        var pre = el("pre", "frg-diff");
                        (hunk.lines || []).forEach(function (line) {
                            var mark = String(line).charAt(0);
                            var cls = mark === "+" ? "frg-diff-add" : (mark === "-" ? "frg-diff-del" : "frg-diff-ctx");
                            pre.appendChild(el("span", cls, line));
                        });
                        container.appendChild(pre);
                    });
                }
                appendRestoreRow(screen, artifactKey, container, base);
            })
            .catch(function () {
                clear(container);
                container.appendChild(el("p", "frg-desc", "변경 내용을 불러오지 못했습니다."));
            });
    }

    /** 백업이 있으면 되돌리기 UI를 붙인다(P12 §16.5). 없으면 아무것도 그리지 않는다. */
    function appendRestoreRow(screen, artifactKey, container, base) {
        fetch(base + "/backups?artifactKey=" + encodeURIComponent(artifactKey),
            { headers: { "Accept": "application/json" } })
            .then(function (r) { return r.ok ? r.json() : []; })
            .then(function (backups) {
                if (!Array.isArray(backups) || !backups.length) { return; }
                var row = el("div", "frg-plan-restore");
                row.appendChild(el("span", "frg-desc", "백업에서 되돌리기"));
                var select = el("select", "frg-input");
                backups.forEach(function (b) {
                    var option = el("option", null, formatStamp(b.timestamp));
                    option.value = String(b.timestamp);
                    select.appendChild(option);
                });
                row.appendChild(select);
                var button = el("button", "frg-btn frg-btn-secondary frg-btn-sm", "되돌리기");
                button.type = "button";
                button.addEventListener("click", function () {
                    button.disabled = true;
                    fetch(base + "/restore", {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify({ artifactKey: artifactKey, timestamp: select.value })
                    }).then(function (r) {
                        button.disabled = false;
                        if (!r.ok) { snack("되돌리기에 실패했습니다."); return; }
                        snack("백업으로 되돌렸습니다.");
                        detailReload(screen, artifactKey, container);
                    }).catch(function () {
                        button.disabled = false;
                        snack("되돌리기에 실패했습니다.");
                    });
                });
                row.appendChild(button);
                container.appendChild(row);
            })
            .catch(function () { /* 백업 목록은 부가 정보 — 실패해도 diff 표시는 유지한다 */ });
    }

    function detailReload(screen, artifactKey, container) {
        container.removeAttribute("data-loaded");
        loadDiff(screen, artifactKey, container);
        container.setAttribute("data-loaded", "1");
    }

    /** yyyyMMddHHmmss → 읽기 쉬운 표기. */
    function formatStamp(stamp) {
        var s = String(stamp || "");
        if (s.length !== 14) { return s; }
        return s.slice(0, 4) + "-" + s.slice(4, 6) + "-" + s.slice(6, 8) + " "
            + s.slice(8, 10) + ":" + s.slice(10, 12) + ":" + s.slice(12, 14);
    }

    function openPlanModal(screen, plan) {
        closePlanModal();
        var project = state.project;
        var files = Array.isArray(plan.files) ? plan.files : [];
        var overwrite = files.filter(function (f) { return f && f.exists; }).length;
        var fresh = files.length - overwrite;

        var backdrop = el("div", "frg-modal-backdrop");
        var modal = el("div", "frg-modal");

        var head = el("div", "frg-modal-head");
        head.appendChild(el("span", null, "생성 미리보기 — " + String(screen.stem || "")));
        var close = el("button", "frg-modal-close", "×");
        close.type = "button";
        close.setAttribute("data-plan-close", "1");
        head.appendChild(close);
        modal.appendChild(head);

        var body = el("div", "frg-modal-body");
        var location = el("label", "frg-plan-location");
        location.appendChild(el("span", "frg-label", "파일 생성 위치"));
        var locationInput = el("input", "frg-input frg-mono");
        locationInput.type = "text";
        locationInput.maxLength = 1000;
        locationInput.autocomplete = "off";
        locationInput.placeholder = "예: C:\\parkDev\\my-target";
        locationInput.value = project && project.targetRootPath ? String(project.targetRootPath) : "";
        location.appendChild(locationInput);
        location.appendChild(el("small", "frg-hint",
            "이 폴더 아래에 JSP/JS/CSS가 생성됩니다. 위치를 바꾸면 프로젝트 설정에도 저장됩니다."));
        body.appendChild(location);
        // P12(계약 §16.2): 마지막 생성 이후 사람이 손댄 파일 수 — 덮어쓰기 결정의 핵심 정보.
        var modified = files.filter(function (f) { return f && f.drift === "MODIFIED"; }).length;

        var summary = el("p", "frg-plan-summary");
        summary.appendChild(el("span", "frg-plan-new", "신규 " + fresh));
        summary.appendChild(el("span", "frg-plan-overwrite", "덮어쓰기 " + overwrite));
        if (modified > 0) {
            summary.appendChild(el("span", "frg-plan-tag-drift", "외부 수정 " + modified));
        }
        if (plan.targetRootExists === false) {
            summary.appendChild(el("span", "frg-msg-err", "⚠ 타겟 루트 폴더가 없습니다 — 생성이 실패합니다."));
        }
        body.appendChild(summary);
        if (modified > 0) {
            body.appendChild(el("p", "frg-msg-warn",
                "⚠ 마지막 생성 이후 사람이 수정한 파일이 있습니다. '변경 보기'로 확인하세요. " +
                "보호구역(j-forge:custom) 안의 코드는 재생성해도 보존됩니다."));
        }
        var ul = el("ul", "frg-plan-list");
        files.forEach(function (f) {
            ul.appendChild(planFileRow(screen, f));
        });
        body.appendChild(ul);
        body.appendChild(el("p", "frg-desc",
            "번들 런타임(공통 JS/CSS)은 버전 정책에 따라 자동 동기화됩니다. 덮어쓰기는 .bak 백업 후 진행됩니다."));
        var locationError = el("p", "frg-plan-location-error");
        locationError.setAttribute("role", "alert");
        body.appendChild(locationError);
        modal.appendChild(body);

        var foot = el("div", "frg-modal-foot");
        var cancel = el("button", "frg-btn frg-btn-secondary", "취소");
        cancel.type = "button";
        cancel.setAttribute("data-plan-close", "1");
        foot.appendChild(cancel);
        var run = el("button", "frg-btn frg-btn-generate", "생성 실행 (파일쓰기)");
        run.type = "button";
        run.setAttribute("data-plan-run", "1");
        foot.appendChild(run);
        modal.appendChild(foot);

        backdrop.appendChild(modal);
        function refreshRunLabel() {
            var original = project && project.targetRootPath ? String(project.targetRootPath).trim() : "";
            run.textContent = locationInput.value.trim() === original
                ? "생성 실행 (파일쓰기)"
                : "위치 적용 후 다시 확인";
        }
        locationInput.addEventListener("input", function () {
            locationError.textContent = "";
            refreshRunLabel();
        });
        refreshRunLabel();
        backdrop.addEventListener("click", function (e) {
            var t = e.target;
            if (t === backdrop || (t.getAttribute && t.getAttribute("data-plan-close"))) {
                closePlanModal();
                return;
            }
            if (t.getAttribute && t.getAttribute("data-plan-run")) {
                var targetRootPath = locationInput.value.trim();
                if (!targetRootPath) {
                    locationError.textContent = "파일 생성 위치를 입력하세요.";
                    locationInput.focus();
                    return;
                }
                if (!looksAbsolutePath(targetRootPath)) {
                    locationError.textContent = "절대경로를 입력하세요. (예: C:\\path 또는 /path)";
                    locationInput.focus();
                    return;
                }
                var original = project && project.targetRootPath ? String(project.targetRootPath).trim() : "";
                if (targetRootPath !== original) {
                    if (!project || !project.projectId) {
                        locationError.textContent = "프로젝트 정보를 불러오지 못했습니다. 페이지를 새로고침해 주세요.";
                        return;
                    }
                    saveGenerateLocation(screen, project, targetRootPath, run, locationError);
                    return;
                }
                closePlanModal();
                runGenerate(screen);
            }
        });
        document.body.appendChild(backdrop);
        planBackdrop = backdrop;
    }

    function showGeneratePlan(screen) {
        var url = apiScreens + "/" + encodeURIComponent(screen.screenId) + "/generate/plan";
        fetch(url, { headers: { "Accept": "application/json" } })
            .then(function (r) {
                return r.json().then(function (payload) {
                    return { ok: r.ok, payload: payload };
                }, function () {
                    return { ok: r.ok, payload: null };
                });
            })
            .then(function (res) {
                if (!res.ok || !res.payload) {
                    // 계획 조회 실패 — 사용자가 원하면 계획 없이 생성(기존 P4-5 흐름 폴백).
                    confirmDialog("생성", "생성 계획을 조회하지 못했습니다. 계획 없이 생성할까요?", function (ok) {
                        if (ok) { runGenerate(screen); }
                    });
                    return;
                }
                if (res.payload.failReason) {
                    // 계획 단계에서 이미 실패 확정(검증/아키타입) — 생성해도 같은 사유로 실패한다.
                    var panel = $("frg-gen-panel");
                    if (panel) { panel.hidden = false; }
                    setGenStatus("생성 불가: " + String(res.payload.failReason), "fail");
                    return;
                }
                openPlanModal(screen, res.payload);
            })
            .catch(function () {
                confirmDialog("생성", "생성 계획을 조회하지 못했습니다. 계획 없이 생성할까요?", function (ok) {
                    if (ok) { runGenerate(screen); }
                });
            });
    }

    // 실제 생성 실행(확인창 통과 후). P7-4에서 dry-run(plan) 확인 흐름이 이 앞단에 결선된다.
    function runGenerate(screen) {
        if (generating) { return; }
        generating = true;
        refreshGenerateButton();
        var panel = $("frg-gen-panel");
        if (panel) { panel.hidden = false; }
        setGenStatus("생성 중…", null);
        var filesUl = $("frg-gen-files");
        if (filesUl) { clear(filesUl); }

        var url = apiScreens + "/" + encodeURIComponent(screen.screenId) + "/generate";
        fetch(url, {
            method: "POST",
            headers: { "Accept": "application/json" }
        })
            .then(function (r) {
                return r.json().then(function (payload) {
                    return { ok: r.ok, status: r.status, payload: payload };
                }, function () {
                    return { ok: r.ok, status: r.status, payload: null };
                });
            })
            .then(function (res) {
                generating = false;
                refreshGenerateButton();
                if (res.ok && res.payload) {
                    var code = res.payload.resultCode;
                    setGenStatus(String(code), genStatusKind(code));
                    renderGenFiles(res.payload.files, res.payload.failReason);
                    // 방금 기록된 이력 포함해 최신순 이력 재조회.
                    loadGenHistory(screen.screenId);
                } else {
                    var msg = (res.payload && res.payload.message)
                        ? res.payload.message
                        : ("생성에 실패했습니다. (HTTP " + res.status + ")");
                    setGenStatus(msg, "fail");
                }
            })
            .catch(function () {
                generating = false;
                refreshGenerateButton();
                setGenStatus("생성 요청에 실패했습니다. 네트워크 상태를 확인하세요.", "fail");
            });
    }

    // ---------- 이벤트 바인딩 ----------
    function bind() {
        $("frg-project-select").addEventListener("change", function (e) {
            var pid = e.target.value;
            state.projectId = pid || null;
            // 전체 메타는 API 응답을 메모리에 보관해 사용한다. option DOM에는 경로를 싣지 않는다.
            state.project = pid ? (projectsById[String(pid)] || null) : null;
            state.screen = null;
            state.definition = null;
            setDirty(false);
            resetHistory(); // 프로젝트 전환 = 히스토리 리셋(P7-3)
            $("frg-btn-new-screen").disabled = !pid;
            $("frg-btn-save").disabled = true;
            refreshGenerateButton(); // 프로젝트 전환 → 화면 미선택이면 생성 버튼 비활성
            resetGenPanel();
            showNewForm(false);
            var sel = $("frg-screen-select");
            if (pid) {
                loadScreens(pid);
            } else {
                clear(sel);
                sel.appendChild(opt("", "프로젝트를 먼저 선택하세요"));
                sel.disabled = true;
            }
        });

        $("frg-screen-select").addEventListener("change", function (e) {
            var sid = e.target.value;
            var sel = e.target;
            // 미저장 이탈 경고: dirty 상태에서 다른 화면으로 전환 시 확인(번들 confirm 위젯).
            if (state.dirty) {
                // 비동기 확인 동안 select 는 현재 화면으로 되돌려 놓는다(취소 시 그대로).
                sel.value = state.screen ? String(state.screen.screenId) : "";
                confirmDialog("변경 내용 삭제",
                    "저장하지 않은 변경이 있습니다. 이동하면 변경 내용이 사라집니다. 계속하시겠습니까?",
                    function (ok) {
                        if (!ok) { return; }
                        sel.value = sid;
                        if (sid) { loadScreen(sid); }
                    });
                return;
            }
            if (sid) { loadScreen(sid); }
        });

        // 저장 버튼(P3-6b)
        $("frg-btn-save").addEventListener("click", saveDefinition);
        // 생성 버튼(P4-5)
        $("frg-btn-generate").addEventListener("click", generateScreen);
        // 실행 미리보기(P9): 저장본 기준 — dirty 면 저장 안내 후 새 탭.
        var runPreviewBtn = $("frg-btn-run-preview");
        if (runPreviewBtn) {
            runPreviewBtn.addEventListener("click", function () {
                var screen = studio.getScreen();
                if (!screen || !screen.screenId) { return; }
                var open = function () {
                    window.open(ctx + "/admin/studio/run-preview/" + encodeURIComponent(screen.screenId),
                        "_blank", "noopener");
                };
                if (state.dirty) {
                    confirmDialog("실행 미리보기",
                        "저장하지 않은 변경이 있습니다. 미리보기는 저장된 내용 기준으로 열립니다. 계속하시겠습니까?",
                        function (ok) { if (ok) { open(); } });
                    return;
                }
                open();
            });
        }
        // undo/redo 버튼(P7-3)
        var undoBtn = $("frg-btn-undo");
        var redoBtn = $("frg-btn-redo");
        if (undoBtn) { undoBtn.addEventListener("click", function () { studio.undo(); }); }
        if (redoBtn) { redoBtn.addEventListener("click", function () { studio.redo(); }); }

        $("frg-btn-new-screen").addEventListener("click", function () {
            showNewForm($("frg-new-screen-form").hidden);
        });
        $("frg-nsf-cancel").addEventListener("click", function () { showNewForm(false); });
        $("frg-new-screen-form").addEventListener("submit", createScreen);

        bindShortcuts();
    }

    // ---------- 키보드 단축키(P7-3) ----------
    // Ctrl+S 저장 / Ctrl+Z undo / Ctrl+Y·Ctrl+Shift+Z redo / Delete 선택 모듈 삭제.
    // 입력 요소(폼 필드/contenteditable) 포커스 중엔 브라우저 기본 동작을 우선한다(Ctrl+S 제외).
    function isEditableTarget(t) {
        if (!t) { return false; }
        var tag = (t.tagName || "").toUpperCase();
        return tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || t.isContentEditable === true;
    }

    function bindShortcuts() {
        document.addEventListener("keydown", function (e) {
            var key = (e.key || "").toLowerCase();
            var mod = e.ctrlKey || e.metaKey;

            // Ctrl+S: 저장(브라우저 저장 다이얼로그 차단). 입력 중에도 동작.
            if (mod && key === "s") {
                e.preventDefault();
                if (state.screen && state.dirty && !saving) { saveDefinition(); }
                return;
            }
            if (isEditableTarget(e.target)) { return; } // 이하 단축키는 입력 중 무시

            if (mod && key === "z" && !e.shiftKey) {
                e.preventDefault();
                studio.undo();
                return;
            }
            if ((mod && key === "y") || (mod && e.shiftKey && key === "z")) {
                e.preventDefault();
                studio.redo();
                return;
            }
            if (mod && key === "d") {
                var duplicateBridge = window.JWorks_JSForgeAdminStudioPreviewBridge;
                var duplicateId = (duplicateBridge && typeof duplicateBridge.getSelected === "function")
                    ? duplicateBridge.getSelected() : null;
                if (duplicateId != null && duplicateBridge && typeof duplicateBridge.requestDuplicate === "function") {
                    e.preventDefault();
                    duplicateBridge.requestDuplicate(duplicateId);
                }
                return;
            }
            if (e.key === "Delete") {
                var bridge = window.JWorks_JSForgeAdminStudioPreviewBridge;
                var selId = (bridge && typeof bridge.getSelected === "function") ? bridge.getSelected() : null;
                if (selId != null && bridge && typeof bridge.requestDelete === "function") {
                    e.preventDefault();
                    bridge.requestDelete(selId);
                }
            }
        });
    }

    // ================= 오케스트레이터 공개 API =================
    studio.onScreenLoaded = function (cb) {
        if (typeof cb === "function") { subs.screenLoaded.push(cb); }
        return studio;
    };
    studio.onDefinitionChanged = function (cb) {
        if (typeof cb === "function") { subs.definitionChanged.push(cb); }
        return studio;
    };
    studio.getScreen = function () { return state.screen; };
    /** 선택된 프로젝트 id(P11: 타겟 DB 카탈로그 조회 대상). 미선택이면 null. */
    studio.getProjectId = function () { return state.projectId; };
    studio.getDefinitionJson = function () { return state.definition; };
    /**
     * 하위 모듈(팔레트/속성패널)이 in-memory DEFINITION_JSON 을 갱신할 때 호출.
     * dirty 플래그를 세우고 definitionChanged 구독자에게 통지한다.
     */
    studio.updateDefinitionJson = function (def, meta) {
        // 라이브 속성 편집은 한 입력 세션을 하나의 undo 단위로 묶는다.
        // meta.history === "merge" 는 직전 스냅샷을 이미 적재했으므로 추가 적재하지 않는다.
        if (!(meta && meta.history === "merge")) {
            pushHistory(state.definition); // P7-3: 직전 상태를 undo 스택에 적재
        }
        state.definition = def;
        setDirty(true);
        notify(subs.definitionChanged, state.definition, meta || { reason: "edit" });
        return studio;
    };
    studio.isDirty = function () { return state.dirty; };
    studio.markSaved = function () { setDirty(false); return studio; };

    // ---------- undo/redo(P7-3) ----------
    // 스냅샷 왕복은 JSON 직렬화 기준 무손상. 저장 여부와 무관하게 편집 이력을 되감는다(dirty 세움).
    studio.undo = function () {
        if (!state.screen || !history.past.length) { return studio; }
        var currentSnap = snapshot(state.definition);
        var prevSnap = history.past.pop();
        if (currentSnap != null) { history.future.push(currentSnap); }
        try { state.definition = JSON.parse(prevSnap); } catch (e) { refreshUndoButtons(); return studio; }
        setDirty(true);
        refreshUndoButtons();
        notify(subs.definitionChanged, state.definition, { reason: "undo" });
        return studio;
    };
    studio.redo = function () {
        if (!state.screen || !history.future.length) { return studio; }
        var currentSnap = snapshot(state.definition);
        var nextSnap = history.future.pop();
        if (currentSnap != null) { history.past.push(currentSnap); }
        try { state.definition = JSON.parse(nextSnap); } catch (e) { refreshUndoButtons(); return studio; }
        setDirty(true);
        refreshUndoButtons();
        notify(subs.definitionChanged, state.definition, { reason: "redo" });
        return studio;
    };
    studio.canUndo = function () { return history.past.length > 0; };
    studio.canRedo = function () { return history.future.length > 0; };

    /**
     * 프로젝트 목록 재조회(P7-5: 스튜디오 내 프로젝트 생성 후 갱신용).
     * @param selectProjectId 재조회 후 선택할 프로젝트 id — change 핸들러를 재사용해 화면 목록까지 잇는다.
     */
    studio.reloadProjects = function (selectProjectId) {
        return loadProjects().then(function () {
            if (selectProjectId != null) {
                var sel = $("frg-project-select");
                sel.value = String(selectProjectId);
                // 기존 change 핸들러(화면 목록 로드/상태 리셋)를 그대로 재사용.
                sel.dispatchEvent(new Event("change", { bubbles: true }));
            }
        });
    };

    /**
     * 화면 목록 재조회(P7-4: 이름변경/복제/삭제 후 갱신용).
     * @param selectScreenId 재조회 후 선택할 화면 id(단건 재로드). null 이면 선택 해제(빈 상태로).
     */
    studio.reloadScreens = function (selectScreenId) {
        if (!state.projectId) { return Promise.resolve(); }
        return loadScreens(state.projectId).then(function () {
            var sel = $("frg-screen-select");
            if (selectScreenId != null) {
                sel.value = String(selectScreenId);
                loadScreen(selectScreenId);
            } else {
                sel.value = "";
                state.screen = null;
                state.definition = null;
                setDirty(false);
                resetHistory();
                refreshGenerateButton();
                resetGenPanel();
                // 하위 모듈(캔버스/속성패널/팔레트)에 "빈 화면" 상태 배포.
                notify(subs.definitionChanged, null, { reason: "screenLoaded" });
            }
        });
    };
    // ==========================================================

    // 미저장 이탈 경고: dirty 상태에서 페이지 언로드 시 브라우저 기본 확인 다이얼로그(선택 기능).
    window.addEventListener("beforeunload", function (e) {
        if (state.dirty) {
            e.preventDefault();
            e.returnValue = ""; // 크롬 등에서 확인 다이얼로그 표시에 필요
            return "";
        }
    });

    document.addEventListener("DOMContentLoaded", function () {
        bind();
        loadProjects();
        refreshSaveButton();     // 초기 상태: 화면 미선택 → 저장 버튼 비활성 유지
        refreshGenerateButton(); // 초기 상태: 화면 미선택 → 생성 버튼 비활성 유지
        refreshUndoButtons();    // 초기 상태: 히스토리 없음 → undo/redo 비활성(P7-3)
    });
})(window.JWorks_JSForgeAdminStudio);
