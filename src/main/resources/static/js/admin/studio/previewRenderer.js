/* ===============================================================================================
Name : previewRenderer.js
Description : 중앙 인터랙티브 캔버스 iframe 내부 렌더러 (P3-6, P7-2 전면 개편).
             부모(previewBridge)로부터 postMessage 로 DEFINITION_JSON + 선택상태 + 아키타입 +
             배치 대기 상태(pending)를 수신해:
              1) 아키타입의 **슬롯 스캐폴드를 상시 렌더** — 빈 슬롯도 점선 존 + "+ 모듈 추가".
              2) 모듈 인스턴스를 **번들 런타임 CSS 클래스 마크업**(골든 산출물과 동형 구조) +
                 샘플 데이터로 실물급 시각화. 편집 오버레이(선택/삭제/드래그)는 위에 겹친다.
              3) 팔레트발 배치 대기(pending) 시 후보 슬롯을 하이라이트 — 슬롯 클릭으로 배치 확정,
                 Esc 로 취소. 슬롯 "+" 클릭 시 부모에 모듈 선택 요청.

★★★ 범위 경계 ★★★
 이 캔버스는 DEFINITION_JSON 의 근사 시각화이며 최종 생성물이 아니다(마크업 구조는 골든 산출물을
 근거로 재현하지만 샘플 데이터/아이콘은 프리뷰 전용). 배치는 아키타입이 정한 슬롯 단위로만.
 실제 파일과의 일치 검증은 생성 엔진 골든테스트 범위다.

🔒 신뢰경계(스키마_DEFINITION_JSON.md §5)
 - postMessage 수신 시 event.origin 검증(다르면 무시), 송신 targetOrigin = location.origin.
 - 표시 문자열(displayName/label 등 자유문자열)은 DOM 삽입 시 textContent/createElement 로만
   (innerH*ML/jQuery html 세터 금지). input type 등 속성값은 화이트리스트 통과값만.
 - instanceId/slotKey 는 data 속성/맵 조회로만 사용(§5.1). styleClass 는 cssToken 규칙
   (영숫자·하이픈·언더스코어)로 클라이언트 재검증 후에만 class 로 사용.

의존: slotMeta.js (MagicIAM_JSForgeAdminStudioSlotMeta — 슬롯 화이트리스트/라벨/순서 단일 소스)
=============================================================================================== */
window.MagicIAM_JSForgeAdminStudioPreview = window.MagicIAM_JSForgeAdminStudioPreview || {};
(function (mod) {
    "use strict";
    if (mod.__defined) { return; }
    mod.__defined = true;

    var slotMeta = window.MagicIAM_JSForgeAdminStudioSlotMeta;

    // 부모와 주고받는 메시지 타입(단일 origin 스튜디오 전용 규약).
    var MSG_DEFINITION = "frg:preview:definition";       // 부모 → iframe : def + selectedId + archetype + pending
    var MSG_SELECT = "frg:preview:select";               // iframe → 부모 : 인스턴스 선택
    var MSG_DELETE = "frg:preview:delete";               // iframe → 부모 : 인스턴스 삭제
    var MSG_DUPLICATE = "frg:preview:duplicate";         // iframe → 부모 : 인스턴스 복제
    var MSG_REORDER = "frg:preview:reorder";             // iframe → 부모 : 슬롯 내 순서이동
    var MSG_READY = "frg:preview:ready";                 // iframe → 부모 : 로드 완료
    var MSG_PLACE = "frg:preview:place";                 // iframe → 부모 : 배치 대기 → 슬롯 확정(클릭/드롭)
    var MSG_ADD = "frg:preview:addRequest";              // iframe → 부모 : 슬롯 "+ 모듈 추가"
    var MSG_CANCEL_PENDING = "frg:preview:cancelPending"; // iframe → 부모 : 배치 대기 취소(Esc)
    var MSG_RESIZE = "frg:preview:resize";               // iframe → 부모 : 크기 조절 확정(P8, §13 layout props)
    var MSG_CANVAS_LAYOUT = "frg:preview:canvasLayout";  // iframe → 부모 : 자유 배치 좌표 확정(P13, §17.2)

    // 최근 수신 상태(재렌더용).
    var lastDef = null;
    var lastSelectedId = null;
    var lastArchetype = null;
    var lastPending = null; // { moduleTypeCode, moduleName, slots: [slotKey...] } | null
    // 드래그 상태(같은 슬롯 내에서만 순서이동 허용).
    var dragSrc = null; // { slotKey, index }

    // ---------- DOM 헬퍼(textContent 로만) ----------
    function el(tag, className, text) {
        var node = document.createElement(tag);
        if (className) { node.className = className; }
        if (text != null) { node.textContent = String(text); }
        return node;
    }

    function clear(node) {
        while (node.firstChild) { node.removeChild(node.firstChild); }
    }

    function root() { return document.getElementById("frg-preview-root"); }

    function slotLabel(slotKey) {
        return slotMeta ? slotMeta.label(slotKey) : String(slotKey);
    }

    // cssToken 클라이언트 재검증(서버 GenEscaper.cssToken 과 동일 취지): 영숫자/하이픈/언더스코어
    // 토큰만 통과, 위반 토큰은 드롭. class 로 쓰이는 자유문자열은 반드시 이 함수를 거친다.
    function cssTokens(raw) {
        if (raw == null) { return ""; }
        return String(raw).split(/\s+/).filter(function (t) {
            return /^[a-zA-Z0-9_-]+$/.test(t);
        }).join(" ");
    }

    // 화이트리스트 input type(previewRenderer 자체 근사 — 서버 계약과 동일 목록).
    var FORM_INPUT_TYPES = {
        text: 1, number: 1, date: 1, email: 1, tel: 1, password: 1,
        select: 1, textarea: 1, checkbox: 1, radio: 1
    };

    function shortLabel(value, maxLength) {
        var text = value == null ? "" : String(value).trim();
        if (text.length <= maxLength) { return text; }
        return text.slice(0, Math.max(1, maxLength - 1)) + "…";
    }

    // 데이터·이벤트는 생성물에 포함될 설계 정보다. 프리뷰에서는 실제 요청을 보내지 않고
    // 요약 배지만 표시한다. 인증/업무 API를 편집 중에 호출하지 않는 안전한 경계다.
    function appendWiringSummary(block, inst) {
        var data = inst && inst.data && typeof inst.data === "object" ? inst.data : null;
        var handlers = inst && Array.isArray(inst.events) ? inst.events.filter(function (item) {
            return item && typeof item === "object" && String(item.action || "").trim() !== "";
        }) : [];
        // propsPanel은 활성·유효한 바인딩만 inst.data로 보존한다(enabled는 폼 전용 값).
        // 따라서 data 객체의 존재가 곧 생성 화면의 활성 데이터 바인딩이다.
        var enabled = !!data;
        var endpoint = data && typeof data.endpoint === "string" ? data.endpoint.trim() : "";

        if (!data && !handlers.length) { return; }

        var strip = el("div", "frg-prev-wiring");
        block.classList.add("has-wiring");

        if (data) {
            var method = data.method === "POST" ? "POST" : "GET";
            var dataText;
            if (enabled) {
                dataText = method + " " + (endpoint ? shortLabel(endpoint, 42) : "경로 미입력");
                if (data.autoLoad !== false) { dataText += " · 자동"; }
            } else {
                dataText = endpoint ? "데이터 꺼짐 · " + shortLabel(endpoint, 28) : "데이터 꺼짐";
            }
            var dataBadge = el("span", "frg-prev-wire frg-prev-wire-data" + (enabled ? " is-on" : " is-off"), dataText);
            dataBadge.title = enabled
                ? "생성 화면에서 같은 도메인 API를 조회합니다."
                : "데이터 바인딩 사용을 켜면 생성 화면에서 API를 조회합니다.";
            strip.appendChild(dataBadge);
        }

        handlers.slice(0, 2).forEach(function (handler) {
            var eventName = shortLabel(handler.event || "click", 16);
            var action = shortLabel(handler.action, 18);
            var eventBadge = el("span", "frg-prev-wire frg-prev-wire-event", eventName + " → " + action);
            eventBadge.title = "생성 화면에서 frg:design:" + String(handler.action) + " 이벤트를 발생시킵니다.";
            strip.appendChild(eventBadge);
        });
        if (handlers.length > 2) {
            strip.appendChild(el("span", "frg-prev-wire frg-prev-wire-more", "+" + (handlers.length - 2)));
        }
        block.appendChild(strip);
    }

    // ---------- 인스턴스 블록 셸(편집 오버레이: 선택/드래그/삭제) ----------
    function instanceBlock(slotKey, inst, index) {
        var block = el("section", "frg-prev-block");
        if (inst && inst.instanceId != null) {
            block.setAttribute("data-instance", String(inst.instanceId));
        }
        block.setAttribute("data-slot", String(slotKey));
        block.setAttribute("data-index", String(index));
        block.setAttribute("role", "button");
        block.tabIndex = 0;
        block.setAttribute("draggable", "true"); // 슬롯 내 순서이동

        var isSelected = inst && inst.instanceId != null
            && String(inst.instanceId) === String(lastSelectedId);
        if (isSelected) { block.classList.add("is-selected"); }

        // §13 레이아웃(P8): 저장된 크기 props 적용. 엔진과 동일 게이트(숫자 타입 + 유효범위)만 반영.
        var props = (inst && inst.props) || {};
        if (typeof props.layoutWidthPct === "number"
                && props.layoutWidthPct >= 10 && props.layoutWidthPct <= 100) {
            block.style.width = props.layoutWidthPct + "%";
        }
        if (typeof props.layoutHeightPx === "number"
                && props.layoutHeightPx >= 40 && props.layoutHeightPx <= 2000) {
            block.style.height = props.layoutHeightPx + "px";
        }

        var head = el("header", "frg-prev-block-head");
        head.appendChild(el("span", "frg-prev-grip", "⠿"));
        head.appendChild(el("span", "frg-prev-module", (inst && inst.moduleTypeCode) || "?"));
        if (canDuplicate(slotKey)) {
            var duplicate = el("button", "frg-prev-duplicate", "⧉");
            duplicate.type = "button";
            duplicate.title = "모듈 복제";
            duplicate.setAttribute("aria-label", "모듈 복제");
            duplicate.setAttribute("data-duplicate", (inst && inst.instanceId != null) ? String(inst.instanceId) : "");
            head.appendChild(duplicate);
        }
        var del = el("button", "frg-prev-del", "×");
        del.type = "button";
        del.title = "삭제";
        del.setAttribute("data-del", (inst && inst.instanceId != null) ? String(inst.instanceId) : "");
        head.appendChild(del);
        block.appendChild(head);
        appendWiringSummary(block, inst);

        // 리사이즈 핸들(P8): 선택된 블록에만 — 우측=폭(%), 하단=높이(px).
        // P13-0: 크기가 **실제로 생성 CSS 에 나가는** 슬롯에만 붙인다. 산출 대상이 아닌 곳
        // (상세영역·DUAL·POPUP·DASHBOARD)에서는 조절해봐야 프리뷰만 변하고 파일은 0바이트라,
        // 핸들 대신 그 사실을 알리는 배지를 둔다 — 에디터가 거짓말하지 않게.
        if (isSelected) {
            if (slotMeta && slotMeta.sizeCssEmitted(lastArchetype, slotKey, index)) {
                var he = el("span", "frg-resize frg-resize-e");
                he.setAttribute("data-resize", "e");
                he.title = "드래그해서 폭 조절";
                var hs = el("span", "frg-resize frg-resize-s");
                hs.setAttribute("data-resize", "s");
                hs.title = "드래그해서 높이 조절";
                block.appendChild(he);
                block.appendChild(hs);
            } else {
                var note = el("span", "frg-resize-na", "크기 고정");
                note.title = "이 영역의 크기는 공통 레이아웃이 정합니다 — 생성 파일에 반영되지 않아 조절을 제공하지 않습니다.";
                block.appendChild(note);
            }
        }
        return block;
    }

    function canDuplicate(slotKey) {
        var rules = slotMeta && lastArchetype && slotMeta.WHITELIST
            ? slotMeta.WHITELIST[lastArchetype] : null;
        return !!(rules && rules[slotKey] && rules[slotKey].multi);
    }

    // 실물 마크업 컨테이너: 클릭이 내부 컨트롤로 새지 않게 CSS 에서 pointer-events:none 처리.
    function realBody(extraClass) {
        return el("div", "frg-prev-real" + (extraClass ? " " + extraClass : ""));
    }

    // ---------- SEARCH_FILTER_BAR 실물 렌더(list.ftl §search 동형) ----------
    function renderSearchFilterBar(block, props) {
        var body = realBody();
        var sec = el("section", "search");
        var filterWrap = el("div", "filter");
        var filters = (props && Array.isArray(props.filters)) ? props.filters : [];
        filters.forEach(function (f) {
            var sel = document.createElement("select");
            sel.setAttribute("aria-label", String((f && f.label) || ""));
            sel.disabled = true;
            var first = document.createElement("option");
            first.value = "";
            first.textContent = String((f && f.label != null) ? f.label : (f && f.name) || "");
            sel.appendChild(first);
            var optStr = (f && typeof f.options === "string") ? f.options : "";
            optStr.split(",").forEach(function (pair) {
                var kv = pair.split(":");
                if (kv.length >= 2) {
                    var o = document.createElement("option");
                    o.value = kv[0].trim();
                    o.textContent = kv[1].trim();
                    sel.appendChild(o);
                }
            });
            filterWrap.appendChild(sel);
        });
        if (filters.length) { sec.appendChild(filterWrap); }
        if (props && props.keywordYn) {
            sec.appendChild(el("div", "input", "검색어 입력")); // contenteditable 은 프리뷰에서 비활성
        }
        if (props && props.dateRangeYn) {
            var dp = el("div", "filter-datepicker");
            var d1 = document.createElement("input");
            d1.type = "date"; d1.className = "datepicker-start"; d1.disabled = true;
            var d2 = document.createElement("input");
            d2.type = "date"; d2.className = "datepicker-end"; d2.disabled = true;
            dp.appendChild(d1); dp.appendChild(d2);
            sec.appendChild(dp);
        }
        if (!sec.childNodes.length) {
            body.appendChild(el("p", "frg-prev-empty", "필터가 정의되지 않았습니다."));
        } else {
            body.appendChild(sec);
        }
        block.appendChild(body);
    }

    // ---------- TOOLBAR 실물 렌더(list.ftl §list-toolbar / detail.ftl §detail-toolbar 동형) ----------
    function renderToolbar(block, props, slotKey) {
        var body = realBody();
        var secClass = (slotKey === "detailToolbar") ? "detail-toolbar" : "list-toolbar";
        var sec = el("section", secClass);
        var buttons = (props && Array.isArray(props.buttons)) ? props.buttons : [];
        if (!buttons.length) {
            body.appendChild(el("p", "frg-prev-empty", "버튼이 정의되지 않았습니다."));
            block.appendChild(body);
            return;
        }
        buttons.forEach(function (b) {
            var btn = el("button", null, (b && b.label != null) ? b.label : (b && b.actionCode));
            btn.type = "button";
            btn.disabled = true;
            var cls = cssTokens("btn " + ((b && b.styleClass) || ""));
            if (cls) { btn.className = cls; } // cssToken 재검증 통과 토큰만
            sec.appendChild(btn);
        });
        body.appendChild(sec);
        block.appendChild(body);
    }

    // ---------- TABLE_VIEW 실물 렌더(module/tableView.ftl 동형 + 샘플 3행) ----------
    function renderTableView(block, props) {
        var body = realBody();
        var columns = (props && Array.isArray(props.columns)) ? props.columns : [];
        if (!columns.length) {
            body.appendChild(el("p", "frg-prev-empty", "컬럼이 정의되지 않았습니다."));
            block.appendChild(body);
            return;
        }
        var visible = columns.filter(function (c) { return !(c && c.displayYn === false); });
        var selectMode = (props && typeof props.selectMode === "string") ? props.selectMode : "";
        var hasSelect = selectMode === "checkbox" || selectMode === "radio";

        var sec = el("section", "table-view");
        sec.setAttribute("id", "table-view");
        if (selectMode) { sec.setAttribute("data-select-mode", cssTokens(selectMode)); }

        if ((props && props.excelYn) || (props && props.csvYn)) {
            var actions = el("div", "table-view-actions");
            if (props.excelYn) {
                var ex = el("button", "btn-excel", "엑셀"); ex.type = "button"; ex.disabled = true;
                actions.appendChild(ex);
            }
            if (props.csvYn) {
                var cs = el("button", "btn-csv", "CSV"); cs.type = "button"; cs.disabled = true;
                actions.appendChild(cs);
            }
            sec.appendChild(actions);
        }

        var layoutBody = el("div", "layout-body");
        var table = document.createElement("table");
        var colgroup = el("colgroup");
        if (hasSelect) {
            var colSel = document.createElement("col"); colSel.className = "col-select";
            colgroup.appendChild(colSel);
        }
        visible.forEach(function () { colgroup.appendChild(document.createElement("col")); });
        table.appendChild(colgroup);

        var thead = el("thead");
        var trh = el("tr");
        if (hasSelect) {
            var tdSel = el("td", "col-select");
            if (selectMode === "checkbox") {
                var all = document.createElement("input");
                all.type = "checkbox"; all.disabled = true;
                tdSel.appendChild(all);
            }
            trh.appendChild(tdSel);
        }
        visible.forEach(function (col) {
            var td = document.createElement("td");
            td.setAttribute("data-name", String((col && col.name) || ""));
            var inner = el("div");
            inner.appendChild(el("span", null, (col && col.displayName != null) ? col.displayName : (col && col.name)));
            if (col && col.sortYn) {
                // 실제 산출물은 sort-icon img — 프리뷰는 이미지 자산 없이 텍스트 근사.
                inner.appendChild(el("span", "frg-sort-approx", "↕"));
            }
            td.appendChild(inner);
            trh.appendChild(td);
        });
        thead.appendChild(trh);
        table.appendChild(thead);

        var tbody = el("tbody");
        for (var r = 1; r <= 3; r++) {
            var tr = el("tr");
            if (hasSelect) {
                var td0 = el("td", "col-select");
                var cb = document.createElement("input");
                cb.type = (selectMode === "radio") ? "radio" : "checkbox";
                cb.disabled = true;
                td0.appendChild(cb);
                tr.appendChild(td0);
            }
            visible.forEach(function (col) {
                var base = (col && col.displayName != null && col.displayName !== "")
                    ? col.displayName : ((col && col.name) || "값");
                tr.appendChild(el("td", null, base + " " + r));
            });
            tbody.appendChild(tr);
        }
        table.appendChild(tbody);
        layoutBody.appendChild(table);
        sec.appendChild(layoutBody);

        if (props && props.pagingYn) {
            var pg = el("div", "pagination");
            pg.setAttribute("id", "pagination");
            var pgInner = el("span", "frg-page-approx", "‹  1  2  3  ›");
            pg.appendChild(pgInner);
            sec.appendChild(pg);
        }
        body.appendChild(sec);
        block.appendChild(body);
    }

    // ---------- CARD_VIEW 실물 렌더(module/cardView.ftl 골격 + 샘플 카드 3장) ----------
    function renderCardView(block, props) {
        var body = realBody();
        var sec = el("section", "card-compact on");
        sec.setAttribute("id", "card-view");
        if (props && typeof props.selectMode === "string") {
            sec.setAttribute("data-select-mode", cssTokens(props.selectMode));
        }
        var column = el("div", "layout-column");

        var header = el("div", "layout-header");
        var left = el("div", "layout-left");
        var total = el("section", "total");
        total.appendChild(el("span", "count", "3"));
        left.appendChild(total);
        if (props && props.categoryYn) {
            var cat = el("section", "category");
            var catSel = document.createElement("select");
            catSel.setAttribute("aria-label", "카테고리");
            catSel.disabled = true;
            var catOpt = document.createElement("option");
            catOpt.textContent = "카테고리";
            catSel.appendChild(catOpt);
            cat.appendChild(catSel);
            left.appendChild(cat);
        }
        var search = el("section", "search");
        search.appendChild(el("div", "input", "검색"));
        search.appendChild(el("div", "search-icon"));
        left.appendChild(search);
        header.appendChild(left);
        column.appendChild(header);

        var layoutBody = el("div", "layout-body");
        var titleField = (props && props.titleField != null && props.titleField !== "") ? props.titleField : "제목";
        var subtitleField = (props && props.subtitleField != null) ? props.subtitleField : "";
        var columns = (props && Array.isArray(props.columns)) ? props.columns : [];
        for (var i = 1; i <= 3; i++) {
            var card = el("div", "card frg-card-approx");
            card.appendChild(el("div", "frg-card-title", titleField + " " + i));
            if (subtitleField) { card.appendChild(el("div", "frg-card-sub", subtitleField + " " + i)); }
            columns.forEach(function (col) {
                if (col && col.displayYn === false) { return; }
                var row = el("div", "frg-card-row");
                row.appendChild(el("span", "frg-card-label",
                    (col && col.displayName != null) ? col.displayName : (col && col.name)));
                row.appendChild(el("span", "frg-card-val", "샘플"));
                card.appendChild(row);
            });
            layoutBody.appendChild(card);
        }
        column.appendChild(layoutBody);

        if (props && props.pagingYn) {
            var footer = el("div", "layout-footer");
            var pg = el("div", "pagination");
            pg.appendChild(el("span", "frg-page-approx", "‹  1  2  3  ›"));
            footer.appendChild(pg);
            column.appendChild(footer);
        }
        sec.appendChild(column);
        body.appendChild(sec);
        block.appendChild(body);
    }

    // ---------- TREE_VIEW 실물 렌더(module/treeView.ftl 골격 + 샘플 2단 노드) ----------
    function renderTreeView(block, props) {
        var body = realBody();
        var sec = el("section", "tree-compact");
        sec.setAttribute("id", "tree-view");
        var column = el("div", "layout-column");

        var header = el("div", "layout-header");
        var left = el("div", "layout-left");
        var total = el("section", "total");
        total.appendChild(el("span", "count", "5"));
        left.appendChild(total);
        if (props && props.searchYn !== false) {
            var search = el("section", "search");
            search.appendChild(el("div", "input", "검색"));
            search.appendChild(el("div", "search-icon"));
            left.appendChild(search);
        }
        header.appendChild(left);
        column.appendChild(header);

        var layoutBody = el("div", "layout-body");
        var rootLabel = (props && props.rootLabel != null && props.rootLabel !== "") ? props.rootLabel : "전체";
        var sample = (props && props.labelField != null && props.labelField !== "") ? props.labelField : "노드";
        var list = el("ul", "tree-list");
        function nodeLi(text, depth, selected) {
            var li = el("li");
            var label = el("div", "tree-node-label" + (selected ? " selected" : ""), text);
            label.style.paddingLeft = (depth * 1.1) + "rem";
            li.appendChild(label);
            return li;
        }
        list.appendChild(nodeLi(rootLabel, 0, false));
        list.appendChild(nodeLi(sample + " 1", 1, true));
        list.appendChild(nodeLi(sample + " 1.1", 2, false));
        list.appendChild(nodeLi(sample + " 2", 1, false));
        list.appendChild(nodeLi(sample + " 2.1", 2, false));
        layoutBody.appendChild(list);
        column.appendChild(layoutBody);
        sec.appendChild(column);
        body.appendChild(sec);
        block.appendChild(body);
    }

    // ---------- FORM_VIEW 실물 렌더(module/formView.ftl 동형) ----------
    function renderFormView(block, props) {
        var body = realBody();
        var sec = el("section", "form-compact");
        sec.setAttribute("id", "form-view");
        if (props && typeof props.selectionType === "string") {
            sec.setAttribute("data-selection-type", cssTokens(props.selectionType));
        }
        var column = el("div", "layout-column");

        if (props && props.selectionType === "checkbox") {
            var header = el("div", "layout-header");
            var left = el("div", "layout-left");
            var lbl = el("label", "select-all-label");
            var all = document.createElement("input");
            all.type = "checkbox"; all.className = "select-all"; all.disabled = true;
            lbl.appendChild(all);
            lbl.appendChild(el("span", null, "전체 선택"));
            left.appendChild(lbl);
            header.appendChild(left);
            column.appendChild(header);
        }

        var layoutBody = el("div", "layout-body");
        var form = el("form", "form-view-form");
        form.setAttribute("onsubmit", "return false;"); // 산출물 동형(정적 속성)
        var fields = (props && Array.isArray(props.fields)) ? props.fields : [];
        if (!fields.length) {
            layoutBody.appendChild(el("p", "frg-prev-empty", "폼 필드가 정의되지 않았습니다."));
        } else {
            fields.forEach(function (f) {
                var field = el("div", "form-field row-checkbox-scope");
                field.setAttribute("data-name", String((f && f.name) || ""));
                var label = el("label", "form-field-label",
                    (f && f.label != null && f.label !== "") ? f.label : (f && f.name));
                if (f && f.requiredYn) { label.appendChild(el("span", "required-mark", " *")); }
                field.appendChild(label);
                var rawType = (f && typeof f.type === "string" && FORM_INPUT_TYPES[f.type]) ? f.type : "text";
                var widget;
                if (rawType === "textarea") {
                    widget = document.createElement("textarea");
                } else if (rawType === "select") {
                    widget = document.createElement("select");
                } else {
                    widget = document.createElement("input");
                    widget.type = rawType; // 화이트리스트 통과 값만
                }
                widget.className = "form-field-input";
                widget.disabled = true;
                field.appendChild(widget);
                form.appendChild(field);
            });
        }
        layoutBody.appendChild(form);
        column.appendChild(layoutBody);
        sec.appendChild(column);
        body.appendChild(sec);
        block.appendChild(body);
    }

    // ---------- DETAIL_BASIC 실물 렌더(detail.ftl §basic-info view-mode 동형) ----------
    function renderDetailBasic(block, props) {
        var body = realBody();
        var sec = el("section", "view-mode basic-compact");
        sec.setAttribute("id", "basic-info");

        var view = el("div", "detail-info-view");
        var column = el("div", "layout-column");
        var fields = (props && Array.isArray(props.fields)) ? props.fields : [];
        if (!fields.length) {
            column.appendChild(el("p", "frg-prev-empty", "기본정보 필드가 정의되지 않았습니다."));
        } else {
            fields.forEach(function (f, i) {
                var row = el("div", "detail-field");
                row.setAttribute("data-name", String((f && f.name) || ""));
                row.appendChild(el("span", "label",
                    (f && f.label != null && f.label !== "") ? f.label : (f && f.name)));
                var base = (f && f.label != null && f.label !== "") ? f.label : ((f && f.name) || "값");
                row.appendChild(el("span", "value", base + " " + (i + 1)));
                column.appendChild(row);
            });
        }
        view.appendChild(column);
        if (props && props.attributeYn) {
            var attr = el("div", "attribute-area");
            attr.appendChild(el("span", "label", "속성"));
            var chips = el("div", "attribute-chip-container");
            chips.appendChild(el("span", null, "샘플속성"));
            attr.appendChild(chips);
            view.appendChild(attr);
        }
        sec.appendChild(view);

        if (props && props.editableYn !== false) {
            var buttons = el("div", "buttons");
            var upd = el("button", "update", "수정"); upd.type = "button"; upd.disabled = true;
            var del = el("button", "delete", "삭제"); del.type = "button"; del.disabled = true;
            buttons.appendChild(upd); buttons.appendChild(del);
            sec.appendChild(buttons);
        }
        body.appendChild(sec);
        block.appendChild(body);
    }

    // ---------- ASSOCIATE_TABS 실물 렌더(detail.ftl §associate-info 동형) ----------
    function renderAssociateTabs(block, props) {
        var body = realBody();
        var tabs = (props && Array.isArray(props.tabs)) ? props.tabs : [];
        if (!tabs.length) {
            body.appendChild(el("p", "frg-prev-empty", "연관 탭이 정의되지 않았습니다."));
            block.appendChild(body);
            return;
        }
        var sec = el("section", "associate-info with-tab");
        sec.setAttribute("id", "associate-info");
        var tabbar = el("div", "tabs");
        tabs.forEach(function (t, i) {
            var cls = cssTokens("tab " + ((t && t.tabClass) || "")) + (i === 0 ? " on" : "");
            var tab = el("div", null, (t && t.label != null && t.label !== "") ? t.label : (t && t.tabClass));
            tab.className = cls;
            tabbar.appendChild(tab);
        });
        sec.appendChild(tabbar);
        var contents = el("div", "contents");
        // iframe 내용은 도메인 화면 URL(tab.location)로 런타임 로드 — 프리뷰는 자리표시.
        contents.appendChild(el("div", "frg-frame-placeholder", "연관 화면(iframe) — 도메인 배선"));
        sec.appendChild(contents);
        body.appendChild(sec);
        block.appendChild(body);
    }

    // ---------- LAYOUT_FRAME 렌더(DUAL_LAYOUT 패인 — iframe 자리표시) ----------
    function renderLayoutFrame(block, props) {
        var body = realBody();
        var title = (props && props.title) ? props.title : (props && props.frameId) || "프레임";
        body.appendChild(el("div", "frg-frame-title", title));
        body.appendChild(el("div", "frg-frame-placeholder", "화면(iframe) — 도메인 URL 배선"));
        block.appendChild(body);
    }

    // ---------- POPUP_FORM 실물 렌더(MagicIAM overlay-popup 골격) ----------
    function renderPopupForm(block, props) {
        var body = realBody();
        var popup = el("section", "overlay-popup frg-popup-preview");
        var box = el("div", "box popup-size-" + cssTokens((props && props.size) || "medium"));
        var column = el("div", "layout-column");
        var header = el("div", "layout-header");
        header.appendChild(el("span", "font-heading-08", (props && props.popupTitle) || "정보 입력"));
        var close = el("button", "close", "×"); close.type = "button"; close.disabled = true;
        header.appendChild(close); column.appendChild(header);

        var layoutBody = el("div", "layout-body");
        if (props && props.bodyTitle) {
            layoutBody.appendChild(el("p", "title font-heading-06", props.bodyTitle));
        }
        var form = el("form", "popup-form");
        var fields = (props && Array.isArray(props.fields)) ? props.fields : [];
        fields.forEach(function (f) {
            var row = el("div", "popup-field");
            var label = el("label", null, (f && f.label) || (f && f.name) || "필드");
            if (f && f.requiredYn) { label.appendChild(el("span", "required-mark", " *")); }
            row.appendChild(label);
            var rawType = (f && FORM_INPUT_TYPES[f.type]) ? f.type : "text";
            var input = rawType === "textarea" ? document.createElement("textarea") :
                (rawType === "select" ? document.createElement("select") : document.createElement("input"));
            if (input.tagName === "INPUT") { input.type = rawType; }
            input.disabled = true; row.appendChild(input); form.appendChild(row);
        });
        if (!fields.length) { form.appendChild(el("p", "frg-prev-empty", "입력 필드를 추가하세요.")); }
        layoutBody.appendChild(form); column.appendChild(layoutBody);

        var footer = el("div", "layout-footer");
        var buttons = el("div", "buttons");
        if (!props || props.cancelYn !== false) {
            var cancel = el("button", "cancel", "취소"); cancel.type = "button"; cancel.disabled = true;
            buttons.appendChild(cancel);
        }
        var ok = el("button", "ok", (props && props.confirmText) || "확인");
        ok.type = "button"; ok.disabled = true; buttons.appendChild(ok);
        footer.appendChild(buttons); column.appendChild(footer);
        box.appendChild(column); popup.appendChild(box); body.appendChild(popup); block.appendChild(body);
    }

    function renderBarChart(block, props) {
        var body = realBody(), card = el("section", "frg-widget-card");
        var value = Math.max(0, Math.min(100, Number(props && props.value) || 0));
        card.appendChild(el("strong", null, (props && props.title) || "진행률"));
        var track = el("div", "frg-chart-track"), fill = el("span", "frg-chart-fill");
        fill.style.width = value + "%"; track.appendChild(fill); card.appendChild(track);
        card.appendChild(el("b", null, value + ((props && props.unit) || "%")));
        body.appendChild(card); block.appendChild(body);
    }

    function renderSemicircleChart(block, props) {
        var body = realBody(), card = el("section", "frg-widget-card");
        var value = Math.max(0, Math.min(100, Number(props && props.value) || 0));
        card.appendChild(el("strong", null, (props && props.title) || "달성률"));
        var gauge = el("div", "frg-semicircle-gauge");
        gauge.style.setProperty("--rate", (value * 1.8) + "deg"); card.appendChild(gauge);
        card.appendChild(el("b", null, value + ((props && props.unit) || "%")));
        body.appendChild(card); block.appendChild(body);
    }

    function renderEmptyState(block, props) {
        var body = realBody(), empty = el("section", "frg-widget-card frg-empty-widget");
        empty.appendChild(el("strong", null, (props && props.title) || "데이터가 없습니다"));
        empty.appendChild(el("p", null, (props && props.description) || "조건을 변경하세요."));
        empty.appendChild(el("button", null, (props && props.actionText) || "새로 만들기"));
        body.appendChild(empty); block.appendChild(body);
    }

    function renderChatWidget(block, props) {
        var body = realBody(), chat = el("section", "frg-widget-card frg-chat-widget");
        chat.appendChild(el("strong", null, (props && props.title) || "채팅 상담"));
        chat.appendChild(el("p", "frg-chat-message", (props && props.welcomeMessage) || "안녕하세요."));
        var input = document.createElement("input"); input.disabled = true;
        input.placeholder = (props && props.placeholder) || "메시지를 입력하세요"; chat.appendChild(input);
        body.appendChild(chat); block.appendChild(body);
    }

    // ---------- CONTROL 원자 컨트롤(P13, §17.1) — freeCanvas/item/*.ftl 과 동형 마크업 ----------
    function controlClass(base, props) {
        var extra = cssTokens((props && props.styleClass) || "");
        return base + (extra ? " " + extra : "");
    }

    function renderButton(block, props) {
        var body = realBody("frg-prev-control");
        var raw = String((props && props.variant) || "primary");
        var variant = (raw === "secondary" || raw === "danger") ? raw : "primary";
        var button = el("button", controlClass("frg-fc-button frg-fc-button-" + variant, props),
            (props && props.text != null) ? props.text : "버튼");
        button.type = "button";
        button.disabled = true;
        body.appendChild(button);
        block.appendChild(body);
    }

    function renderLabel(block, props) {
        var body = realBody("frg-prev-control");
        var raw = String((props && props.level) || "normal");
        var level = (raw === "title" || raw === "caption") ? raw : "normal";
        body.appendChild(el("span", controlClass("frg-fc-label frg-fc-label-" + level, props),
            (props && props.text != null) ? props.text : "라벨"));
        block.appendChild(body);
    }

    function renderTextInput(block, props) {
        var body = realBody("frg-prev-control");
        var wrap = el("div", controlClass("frg-fc-field", props));
        var label = (props && props.label != null) ? String(props.label) : "";
        if (label) { wrap.appendChild(el("label", null, label)); }
        var input = document.createElement("input");
        var raw = String((props && props.inputType) || "text");
        input.type = FORM_INPUT_TYPES[raw] ? raw : "text"; // 화이트리스트 통과값만
        input.disabled = true;
        if (props && props.placeholder != null) { input.placeholder = String(props.placeholder); }
        wrap.appendChild(input);
        body.appendChild(wrap);
        block.appendChild(body);
    }

    function renderImage(block, props) {
        var body = realBody("frg-prev-control");
        var raw = String((props && props.fit) || "contain");
        var fit = (raw === "cover" || raw === "fill") ? raw : "contain";
        // 프리뷰는 외부 요청을 만들지 않는다 — 경로는 자리표시 박스에 텍스트로만 보여 준다.
        var placeholder = el("div", controlClass("frg-fc-image-ph frg-fc-image-" + fit, props),
            (props && props.src) ? String(props.src) : "이미지");
        body.appendChild(placeholder);
        block.appendChild(body);
    }

    // ---------- LAYOUT/PANEL 중첩 컨테이너(§17.8) — freeCanvas/item/panel.ftl 과 동형 ----------
    // 자식 블록은 이 함수가 아니라 appendCanvasNodes 가 블록 안에 직접 넣는다(DOM 중첩 = 좌표 중첩).
    function renderPanel(block, props) {
        var panel = el("div", "frg-fc-panel"
            + (((props && props.borderYn) !== false) ? " frg-fc-panel-bordered" : "")
            + ((props && props.fillYn) ? " frg-fc-panel-filled" : ""));
        var extra = cssTokens((props && props.styleClass) || "");
        if (extra) { panel.className += " " + extra; }
        var title = (props && props.title != null) ? String(props.title) : "";
        if (title) { panel.appendChild(el("span", "frg-fc-panel-title", title)); }
        block.appendChild(panel);
    }

    var RENDERERS = {
        PANEL: renderPanel,
        BUTTON: renderButton,
        LABEL: renderLabel,
        TEXT_INPUT: renderTextInput,
        IMAGE: renderImage,
        TABLE_VIEW: renderTableView,
        SEARCH_FILTER_BAR: renderSearchFilterBar,
        TOOLBAR: renderToolbar,
        CARD_VIEW: renderCardView,
        TREE_VIEW: renderTreeView,
        FORM_VIEW: renderFormView,
        DETAIL_BASIC: renderDetailBasic,
        ASSOCIATE_TABS: renderAssociateTabs,
        LAYOUT_FRAME: renderLayoutFrame,
        POPUP_FORM: renderPopupForm,
        BAR_CHART: renderBarChart,
        SEMICIRCLE_CHART: renderSemicircleChart,
        EMPTY_STATE: renderEmptyState,
        CHAT_WIDGET: renderChatWidget
    };

    function renderUnknown(block, moduleTypeCode) {
        var body = el("div", "frg-prev-real");
        body.appendChild(el("p", "frg-prev-empty",
            "이 모듈(" + (moduleTypeCode || "?") + ")은 아직 캔버스 렌더를 지원하지 않습니다."));
        block.appendChild(body);
    }

    // ---------- 슬롯 스캐폴드 1칸 ----------
    function slotSection(slotKey, rule, instances) {
        var pendingSlots = (lastPending && Array.isArray(lastPending.slots)) ? lastPending.slots : null;
        var isTarget = !!(pendingSlots && pendingSlots.indexOf(slotKey) !== -1);
        var isDimmed = !!(pendingSlots && !isTarget);

        var sec = el("section", "frg-slot");
        sec.setAttribute("data-slotkey", String(slotKey));
        if (isTarget) { sec.classList.add("is-droptarget"); }
        if (isDimmed) { sec.classList.add("is-dimmed"); }

        var head = el("header", "frg-slot-head");
        head.appendChild(el("span", "frg-slot-name", slotLabel(slotKey)));
        head.appendChild(el("span", "frg-slot-key", slotKey));
        if (rule && rule.multi) { head.appendChild(el("span", "frg-slot-multi", "복수")); }
        // "+ 모듈 추가": 배치 대기 중이 아니고, (복수 슬롯 || 빈 단일 슬롯)일 때.
        if (!pendingSlots && rule && (rule.multi || !instances.length)) {
            var add = el("button", "frg-slot-add", "+ 모듈 추가");
            add.type = "button";
            add.setAttribute("data-add", String(slotKey));
            head.appendChild(add);
        }
        sec.appendChild(head);

        var bodyWrap = el("div", "frg-slot-body");
        if (isTarget) {
            bodyWrap.appendChild(el("p", "frg-slot-target-hint",
                "'" + (lastPending.moduleName || lastPending.moduleTypeCode) + "' 을(를) 여기에 배치 — 클릭"));
        }
        if (!instances.length) {
            if (!isTarget) {
                bodyWrap.appendChild(el("p", "frg-slot-empty-hint", "비어 있음"));
            }
        } else {
            instances.forEach(function (inst, index) {
                if (!inst || typeof inst !== "object") { return; }
                var block = instanceBlock(slotKey, inst, index);
                var fn = RENDERERS[inst.moduleTypeCode];
                if (fn) { fn(block, inst.props || {}, slotKey); }
                else { renderUnknown(block, inst.moduleTypeCode); }
                bodyWrap.appendChild(block);
            });
        }
        sec.appendChild(bodyWrap);
        return sec;
    }

    // ---------- FREE_CANVAS 자유 배치(§17, P13) ----------
    // 슬롯 스택이 아니라 **고정폭 시트 + absolute 블록**으로 그린다. 좌표는 인스턴스 props(§17.2)가
    // 갖고, 여기서 보이는 픽셀이 그대로 생성 CSS 로 나간다(WYSIWYG 일치).

    var CANVAS_SLOT = "canvasArea";

    function canvasItems(def) {
        var arr = def && def.slots ? def.slots[CANVAS_SLOT] : null;
        return Array.isArray(arr) ? arr : [];
    }

    function clampCanvas(key, value, fallback) {
        var n = slotMeta ? slotMeta.clampCanvas(key, value) : null;
        return n == null ? fallback : n;
    }

    /** 인스턴스의 좌표·크기. 값이 없으면 모듈 기본 크기 + 인덱스 계단 배치로 채운다. */
    function canvasGeometry(inst, index) {
        var props = (inst && inst.props) || {};
        var size = slotMeta ? slotMeta.defaultItemSize(inst && inst.moduleTypeCode) : { w: 320, h: 200 };
        var step = (index || 0) * 24;
        return {
            x: clampCanvas("layoutXPx", props.layoutXPx, 24 + step),
            y: clampCanvas("layoutYPx", props.layoutYPx, 24 + step),
            w: clampCanvas("layoutWPx", props.layoutWPx, size.w),
            h: clampCanvas("layoutHPx", props.layoutHPx, size.h),
            z: clampCanvas("layoutZ", props.layoutZ, 0)
        };
    }

    function sheetSize(def) {
        var sheet = slotMeta ? slotMeta.CANVAS_SHEET : null;
        var node = (def && def.canvas && typeof def.canvas === "object") ? def.canvas : {};
        var w = Number(node.widthPx);
        var h = Number(node.heightPx);
        if (!sheet) { return { w: 1280, h: 800 }; }
        return {
            w: (isFinite(w) && w >= sheet.minWidthPx && w <= sheet.maxWidthPx) ? Math.round(w) : sheet.defaultWidthPx,
            h: (isFinite(h) && h >= sheet.minHeightPx && h <= sheet.maxHeightPx) ? Math.round(h) : sheet.defaultHeightPx
        };
    }

    // 8방향 리사이즈 핸들(델파이 선택 핸들과 동일 배치).
    var CANVAS_HANDLES = ["nw", "n", "ne", "e", "se", "s", "sw", "w"];

    function canvasBlock(inst, index) {
        var block = el("section", "frg-prev-block frg-fc-block");
        if (inst && inst.instanceId != null) {
            block.setAttribute("data-instance", String(inst.instanceId));
        }
        block.setAttribute("data-slot", CANVAS_SLOT);
        block.setAttribute("data-index", String(index));
        // §17.8: 컨테이너 여부를 DOM 에 남겨 "패널에 끌어넣기"가 대상 판정에 쓴다.
        block.setAttribute("data-module-type", String((inst && inst.moduleTypeCode) || ""));
        if (slotMeta && slotMeta.isContainer(inst && inst.moduleTypeCode)) {
            block.classList.add("frg-fc-container");
        }
        block.setAttribute("role", "button");
        block.tabIndex = 0;

        var g = canvasGeometry(inst, index);
        block.style.left = g.x + "px";
        block.style.top = g.y + "px";
        block.style.width = g.w + "px";
        block.style.height = g.h + "px";
        block.style.zIndex = String(g.z);

        var isSelected = inst && inst.instanceId != null
            && String(inst.instanceId) === String(lastSelectedId);
        if (isSelected) { block.classList.add("is-selected"); }

        // 크롬은 hover/선택 시에만 보인다(CSS) — 평소엔 결과물 그대로 보이게.
        var head = el("header", "frg-prev-block-head");
        head.appendChild(el("span", "frg-prev-module", (inst && inst.moduleTypeCode) || "?"));
        var instanceId = (inst && inst.instanceId != null) ? String(inst.instanceId) : "";
        var front = el("button", "frg-fc-z", "▲");
        front.type = "button";
        front.title = "맨 앞으로";
        front.setAttribute("data-zfront", instanceId);
        head.appendChild(front);
        var back = el("button", "frg-fc-z", "▼");
        back.type = "button";
        back.title = "맨 뒤로";
        back.setAttribute("data-zback", instanceId);
        head.appendChild(back);
        var duplicate = el("button", "frg-prev-duplicate", "⧉");
        duplicate.type = "button";
        duplicate.title = "모듈 복제";
        duplicate.setAttribute("data-duplicate", instanceId);
        head.appendChild(duplicate);
        var del = el("button", "frg-prev-del", "×");
        del.type = "button";
        del.title = "삭제";
        del.setAttribute("data-del", instanceId);
        head.appendChild(del);
        block.appendChild(head);
        appendWiringSummary(block, inst);

        if (isSelected) {
            CANVAS_HANDLES.forEach(function (dir) {
                var handle = el("span", "frg-fc-handle frg-fc-handle-" + dir);
                handle.setAttribute("data-fc-resize", dir);
                block.appendChild(handle);
            });
        }
        return block;
    }

    /** 캔버스 트리를 DOM 으로 중첩 삽입(seq 순서 = 생성물 순서). */
    function appendCanvasNodes(parentEl, nodes) {
        nodes.forEach(function (node) {
            var inst = node.inst;
            var block = canvasBlock(inst, node.seq - 1);
            var fn = RENDERERS[inst.moduleTypeCode];
            if (fn) { fn(block, inst.props || {}, CANVAS_SLOT); }
            else { renderUnknown(block, inst.moduleTypeCode); }
            // §17.12: 컨테이너는 자식을 **전용 내용 상자** 안에 담는다 — 생성물(shell.ftl)과
            // 같은 모양이어야 한다. 캔버스에 보이는 구조가 곧 나갈 구조라는 게 이 프리뷰의 계약이다.
            if (slotMeta && slotMeta.isContainer(inst && inst.moduleTypeCode)) {
                var body = el("div", "frg-fc-panel-body");
                if (node.children.length) { appendCanvasNodes(body, node.children); }
                block.appendChild(body);
            } else if (node.children.length) {
                appendCanvasNodes(block, node.children);
            }
            parentEl.appendChild(block);
        });
    }

    function renderFreeCanvas(container, def) {
        var size = sheetSize(def);
        var sheet = el("div", "frg-fc-canvas");
        sheet.setAttribute("data-slotkey", CANVAS_SLOT);
        sheet.style.width = size.w + "px";
        sheet.style.height = size.h + "px";
        if (lastPending) { sheet.classList.add("is-droptarget"); }

        var items = canvasItems(def);
        // §17.8: 평면 배열 → 중첩 트리(서버 산출과 같은 규칙). DOM 을 중첩시키면 자식 좌표가
        // 부모 상자 기준이 되는 것은 브라우저가 알아서 한다 — 여기서 좌표를 변환하지 않는다.
        var tree = slotMeta ? slotMeta.buildCanvasTree(items) : [];
        appendCanvasNodes(sheet, tree);

        if (!items.length) {
            sheet.appendChild(el("p", "frg-fc-empty",
                "팔레트에서 부품을 고른 뒤 캔버스에서 놓을 자리를 클릭하세요. 끌어다 놓아도 됩니다."));
        }

        var scroller = el("div", "frg-fc-scroller");
        scroller.appendChild(sheet);
        container.appendChild(scroller);
    }

    // ---------- 전체 렌더(아키타입 슬롯 스캐폴드 상시) ----------
    function renderDefinition(def, selectedId, archetype, pending) {
        lastDef = def || null;
        if (selectedId !== undefined) { lastSelectedId = (selectedId == null ? null : String(selectedId)); }
        if (archetype !== undefined) { lastArchetype = (archetype == null ? null : String(archetype)); }
        if (pending !== undefined) { lastPending = pending || null; }

        var r = root();
        if (!r) { return; }
        clear(r);

        if (!def || !def.slots || typeof def.slots !== "object") {
            r.appendChild(el("p", "frg-prev-empty frg-prev-nodoc",
                "화면을 선택하면 슬롯 구조가 표시됩니다."));
            return;
        }

        // §17: 자유 배치는 슬롯 스캐폴드를 그리지 않는다(시트 1장 + absolute 블록).
        if (slotMeta && slotMeta.isFreeCanvas(lastArchetype)) {
            renderFreeCanvas(r, def);
            if (lastPending) { appendPendingBar(r); }
            return;
        }

        var wl = (slotMeta && lastArchetype && slotMeta.WHITELIST[lastArchetype])
            ? slotMeta.WHITELIST[lastArchetype] : null;

        // 표시 슬롯 = 아키타입 스캐폴드(빈 슬롯 포함) ∪ 문서에만 있는 슬롯(forward-compat).
        var scaffoldKeys = (slotMeta && wl) ? slotMeta.orderedSlots(lastArchetype) : [];
        var extraKeys = Object.keys(def.slots).filter(function (k) {
            return scaffoldKeys.indexOf(k) === -1;
        });

        // 화면 시트(라이트 페이퍼) — 생성물이 놓이는 종이 느낌의 컨테이너.
        var sheet = el("div", "frg-canvas-sheet");
        if (lastArchetype === "DUAL_LAYOUT") { sheet.classList.add("frg-canvas-dual"); }

        scaffoldKeys.concat(extraKeys).forEach(function (slotKey) {
            var rule = wl ? wl[slotKey] : null;
            var arr = Array.isArray(def.slots[slotKey]) ? def.slots[slotKey] : [];
            sheet.appendChild(slotSection(slotKey, rule, arr));
        });

        if (!scaffoldKeys.length && !extraKeys.length) {
            sheet.appendChild(el("p", "frg-prev-empty", "이 아키타입의 슬롯 정보를 알 수 없습니다."));
        }
        r.appendChild(sheet);

        // 배치 대기 중이면 상단 안내 바(Esc 취소).
        if (lastPending) { appendPendingBar(r); }
    }

    /** 배치 대기 안내 바(슬롯형·캔버스형 공용). */
    function appendPendingBar(r) {
        var isCanvas = slotMeta && slotMeta.isFreeCanvas(lastArchetype);
        var bar = el("div", "frg-pending-bar");
        bar.appendChild(el("span", null,
            "'" + (lastPending.moduleName || lastPending.moduleTypeCode) + "' 배치 — "
            + (isCanvas ? "캔버스에서 놓을 자리를 클릭하세요" : "하이라이트된 슬롯을 클릭하세요")));
        var cancel = el("button", "frg-pending-cancel", "취소 (Esc)");
        cancel.type = "button";
        cancel.setAttribute("data-cancel-pending", "1");
        bar.appendChild(cancel);
        r.insertBefore(bar, r.firstChild);
    }

    // ---------- 부모 통신 ----------
    function post(type, extra) {
        var msg = { type: type };
        if (extra) { Object.keys(extra).forEach(function (k) { msg[k] = extra[k]; }); }
        try { window.parent.postMessage(msg, window.location.origin); } catch (e) { /* 무시 */ }
    }

    function notifySelect(instanceId) {
        if (instanceId == null) { return; }
        lastSelectedId = String(instanceId);
        applySelectionHighlight();
        post(MSG_SELECT, { instanceId: String(instanceId) });
    }

    function applySelectionHighlight() {
        var r = root();
        if (!r) { return; }
        var blocks = r.querySelectorAll(".frg-prev-block");
        Array.prototype.forEach.call(blocks, function (b) {
            if (b.getAttribute("data-instance") === lastSelectedId) { b.classList.add("is-selected"); }
            else { b.classList.remove("is-selected"); }
        });
    }

    // ---------- 이벤트(선택 / 삭제 / 배치 / 드래그 순서이동) ----------
    function bindEvents() {
        var r = root();
        if (!r) { return; }

        r.addEventListener("click", function (e) {
            var t = e.target;
            // 배치 대기 취소 버튼.
            if (t && t.getAttribute && t.getAttribute("data-cancel-pending")) {
                post(MSG_CANCEL_PENDING);
                return;
            }
            // 슬롯 "+ 모듈 추가".
            var addSlot = t && t.getAttribute ? t.getAttribute("data-add") : null;
            if (addSlot) {
                e.stopPropagation();
                post(MSG_ADD, { slotKey: String(addSlot) });
                return;
            }
            // 삭제 버튼.
            var delId = t && t.getAttribute ? t.getAttribute("data-del") : null;
            if (delId) {
                e.stopPropagation();
                post(MSG_DELETE, { instanceId: String(delId) });
                return;
            }
            var duplicateId = t && t.getAttribute ? t.getAttribute("data-duplicate") : null;
            if (duplicateId) {
                e.stopPropagation();
                post(MSG_DUPLICATE, { instanceId: String(duplicateId) });
                return;
            }
            // P13: 자유 배치 z-order — 현재 캔버스의 최대/최소 z 를 기준으로 절대값을 보낸다.
            var zFront = t && t.getAttribute ? t.getAttribute("data-zfront") : null;
            var zBack = t && t.getAttribute ? t.getAttribute("data-zback") : null;
            if (zFront || zBack) {
                e.stopPropagation();
                postZOrder(zFront || zBack, !!zFront);
                return;
            }
            // 배치 대기 중: 하이라이트 슬롯 클릭 → 배치 확정.
            // moduleTypeCode 동봉(P8): 드래그 드롭 직후 부모의 pending 이 dragend 로 먼저 소거되는
            // 경합에서도 배치가 유실되지 않도록 iframe 이 아는 대기 모듈 코드를 함께 보낸다.
            // P13: 자유 배치는 클릭한 좌표가 곧 배치 위치다.
            if (lastPending && slotMeta && slotMeta.isFreeCanvas(lastArchetype)) {
                var sheet = t && t.closest ? t.closest(".frg-fc-canvas") : null;
                if (sheet) { postCanvasPlace(sheet, e.clientX, e.clientY); }
                return;
            }
            if (lastPending) {
                var slotSec = t && t.closest ? t.closest(".frg-slot.is-droptarget") : null;
                if (slotSec) {
                    post(MSG_PLACE, {
                        slotKey: String(slotSec.getAttribute("data-slotkey")),
                        moduleTypeCode: String(lastPending.moduleTypeCode || "")
                    });
                }
                return; // 대기 중엔 선택/기타 클릭 무시
            }
            // 인스턴스 선택.
            var block = t && t.closest ? t.closest("[data-instance]") : null;
            if (block) { notifySelect(block.getAttribute("data-instance")); }
        });

        r.addEventListener("keydown", function (e) {
            var block = e.target && e.target.closest ? e.target.closest("[data-instance]") : null;
            if (!block) { return; }
            if (e.key === "Enter" || e.key === " ") {
                e.preventDefault(); notifySelect(block.getAttribute("data-instance"));
            } else if (e.key === "Delete") {
                e.preventDefault();
                var id = block.getAttribute("data-instance");
                if (id) { post(MSG_DELETE, { instanceId: String(id) }); }
            }
        });

        // 배치 대기 취소(Esc) — iframe 포커스 시.
        document.addEventListener("keydown", function (e) {
            if (e.key === "Escape" && lastPending) { post(MSG_CANCEL_PENDING); }
        });

        // ---- 드래그 순서이동(같은 슬롯 내) + 팔레트발 외부 드롭(P8: 대기 슬롯에 놓기) ----
        r.addEventListener("dragstart", function (e) {
            // 리사이즈 핸들 위에서는 HTML5 드래그를 시작하지 않는다(포인터 리사이즈 우선).
            if (e.target && e.target.closest && e.target.closest("[data-resize]")) {
                e.preventDefault();
                return;
            }
            var block = e.target && e.target.closest ? e.target.closest(".frg-prev-block") : null;
            if (!block) { return; }
            dragSrc = { slotKey: block.getAttribute("data-slot"), index: Number(block.getAttribute("data-index")) };
            block.classList.add("is-dragging");
            try { e.dataTransfer.effectAllowed = "move"; e.dataTransfer.setData("text/plain", ""); } catch (ex) { /* 무시 */ }
        });

        r.addEventListener("dragover", function (e) {
            // 팔레트발 외부 드래그(P8): 대기(pending) 후보 슬롯 위에서만 드롭 허용.
            if (!dragSrc) {
                if (lastPending) {
                    var slot = e.target && e.target.closest ? e.target.closest(".frg-slot.is-droptarget") : null;
                    if (slot) {
                        e.preventDefault();
                        try { e.dataTransfer.dropEffect = "copy"; } catch (ex) { /* 무시 */ }
                    }
                }
                return;
            }
            var block = e.target && e.target.closest ? e.target.closest(".frg-prev-block") : null;
            if (!block || block.getAttribute("data-slot") !== dragSrc.slotKey) { return; }
            e.preventDefault(); // 같은 슬롯 위에서만 드롭 허용
            block.classList.add("is-drop-target");
        });

        r.addEventListener("dragleave", function (e) {
            var block = e.target && e.target.closest ? e.target.closest(".frg-prev-block") : null;
            if (block) { block.classList.remove("is-drop-target"); }
        });

        r.addEventListener("drop", function (e) {
            // 팔레트발 외부 드롭(P8): 대기 후보 슬롯에 배치 확정(moduleTypeCode 동봉 — 경합 방지).
            if (!dragSrc) {
                if (lastPending) {
                    var slot = e.target && e.target.closest ? e.target.closest(".frg-slot.is-droptarget") : null;
                    if (slot) {
                        e.preventDefault();
                        post(MSG_PLACE, {
                            slotKey: String(slot.getAttribute("data-slotkey")),
                            moduleTypeCode: String(lastPending.moduleTypeCode || "")
                        });
                    }
                }
                return;
            }
            var block = e.target && e.target.closest ? e.target.closest(".frg-prev-block") : null;
            if (!block || block.getAttribute("data-slot") !== dragSrc.slotKey) { return; }
            e.preventDefault();
            var toIndex = Number(block.getAttribute("data-index"));
            var fromIndex = dragSrc.index;
            block.classList.remove("is-drop-target");
            if (fromIndex !== toIndex) {
                post(MSG_REORDER, { slotKey: dragSrc.slotKey, fromIndex: fromIndex, toIndex: toIndex });
            }
        });

        r.addEventListener("dragend", function () {
            dragSrc = null;
            var r2 = root();
            if (!r2) { return; }
            Array.prototype.forEach.call(r2.querySelectorAll(".is-dragging,.is-drop-target"), function (b) {
                b.classList.remove("is-dragging"); b.classList.remove("is-drop-target");
            });
        });

        // ---- 리사이즈 핸들(P8): 선택 블록 우측=폭(%) / 하단=높이(px) ----
        // 라이브는 인라인 스타일로만 반영, 확정(pointerup) 시 부모에 §13 layout props 갱신 통지.
        var resizeState = null; // { mode, instanceId, block, startX, startY, startW, startH, containerW, pct?, px? }

        function clampNum(v, min, max) {
            return Math.min(max, Math.max(min, v));
        }

        r.addEventListener("pointerdown", function (e) {
            var handle = e.target && e.target.closest ? e.target.closest("[data-resize]") : null;
            if (!handle) { return; }
            var block = handle.closest(".frg-prev-block");
            if (!block) { return; }
            e.preventDefault();
            e.stopPropagation();
            var slotBody = block.closest(".frg-slot-body");
            var rect = block.getBoundingClientRect();
            resizeState = {
                mode: handle.getAttribute("data-resize"),
                instanceId: block.getAttribute("data-instance"),
                block: block,
                startX: e.clientX,
                startY: e.clientY,
                startW: rect.width,
                startH: rect.height,
                containerW: slotBody ? slotBody.getBoundingClientRect().width : rect.width
            };
            try { handle.setPointerCapture(e.pointerId); } catch (ex) { /* 무시 */ }
        });

        document.addEventListener("pointermove", function (e) {
            if (!resizeState) { return; }
            if (resizeState.mode === "e") {
                var w = resizeState.startW + (e.clientX - resizeState.startX);
                var pct = clampNum(Math.round(w / Math.max(1, resizeState.containerW) * 100), 10, 100);
                resizeState.block.style.width = pct + "%";
                resizeState.pct = pct;
            } else {
                var h = resizeState.startH + (e.clientY - resizeState.startY);
                var px = clampNum(Math.round(h), 40, 2000);
                resizeState.block.style.height = px + "px";
                resizeState.px = px;
            }
        });

        document.addEventListener("pointerup", function () {
            if (!resizeState) { return; }
            var st = resizeState;
            resizeState = null;
            if (st.instanceId == null) { return; }
            if (st.mode === "e" && st.pct != null) {
                post(MSG_RESIZE, { instanceId: String(st.instanceId), widthPct: st.pct });
            } else if (st.mode === "s" && st.px != null) {
                post(MSG_RESIZE, { instanceId: String(st.instanceId), heightPx: st.px });
            }
        });

        bindCanvasEvents(r);
    }

    // ---------- 자유 배치 조작(P13, §17) : 이동 · 8방향 리사이즈 · 스냅 · 방향키 · z-order ----------
    function bindCanvasEvents(r) {
        // { mode:"move"|리사이즈방향, instanceId, block, startX, startY, base:{x,y,w,h}, geo:{x,y,w,h} }
        var canvasState = null;

        function snap(v, useSnap) {
            var step = (slotMeta && slotMeta.CANVAS_SNAP) || 8;
            return useSnap ? Math.round(v / step) * step : Math.round(v);
        }

        function clamp(key, v) {
            var n = slotMeta ? slotMeta.clampCanvas(key, v) : null;
            return n == null ? 0 : n;
        }

        function applyGeo(block, geo) {
            block.style.left = geo.x + "px";
            block.style.top = geo.y + "px";
            block.style.width = geo.w + "px";
            block.style.height = geo.h + "px";
        }

        function commitGeo(instanceId, geo, reparent) {
            var msg = {
                instanceId: String(instanceId),
                x: geo.x, y: geo.y, w: geo.w, h: geo.h
            };
            // §17.8: 부모가 바뀌었을 때만 실어 보낸다(null = 캔버스 루트로 빼내기).
            if (reparent) { msg.parentId = reparent.parentId; }
            post(MSG_CANVAS_LAYOUT, msg);
        }

        /**
         * §17.8 "패널에 끌어넣기": 포인터 아래에서 **자기 자신도, 자기 자손도 아닌** 가장 안쪽
         * 컨테이너를 찾는다. 순환(자기 자손에 들어가기)은 여기서 원천 차단된다.
         */
        function dropContainerAt(dragged, clientX, clientY) {
            var stack = document.elementsFromPoint
                ? document.elementsFromPoint(clientX, clientY) : [];
            for (var i = 0; i < stack.length; i++) {
                var candidate = stack[i].closest ? stack[i].closest(".frg-fc-container") : null;
                if (!candidate) { continue; }
                if (candidate === dragged || dragged.contains(candidate)) { continue; }
                return candidate;
            }
            return null;
        }

        /** 이동 결과를 새 부모 기준 좌표로 환산한다. 부모가 그대로면 null. */
        function resolveReparent(st, clientX, clientY) {
            var dragged = st.block;
            var currentParent = dragged.parentNode
                && dragged.parentNode.closest ? dragged.parentNode.closest(".frg-fc-container") : null;
            var target = dropContainerAt(dragged, clientX, clientY);
            if (target === currentParent) { return null; }

            var rect = dragged.getBoundingClientRect();
            var baseRect = target ? target.getBoundingClientRect()
                : dragged.closest(".frg-fc-canvas").getBoundingClientRect();
            return {
                parentId: target ? target.getAttribute("data-instance") : null,
                x: clamp("layoutXPx", Math.round(rect.left - baseRect.left)),
                y: clamp("layoutYPx", Math.round(rect.top - baseRect.top))
            };
        }

        function currentGeo(block) {
            return {
                x: parseInt(block.style.left, 10) || 0,
                y: parseInt(block.style.top, 10) || 0,
                w: parseInt(block.style.width, 10) || 20,
                h: parseInt(block.style.height, 10) || 20
            };
        }

        r.addEventListener("pointerdown", function (e) {
            if (!slotMeta || !slotMeta.isFreeCanvas(lastArchetype) || lastPending) { return; }
            var block = e.target && e.target.closest ? e.target.closest(".frg-fc-block") : null;
            if (!block) { return; }
            // 크롬 버튼(삭제·복제·z)은 클릭으로 처리한다.
            if (e.target.closest && e.target.closest("button")) { return; }

            var handle = e.target.closest ? e.target.closest("[data-fc-resize]") : null;
            var instanceId = block.getAttribute("data-instance");
            // 아직 선택되지 않은 블록이면 먼저 선택(델파이와 동일 — 클릭이 곧 선택).
            if (String(instanceId) !== String(lastSelectedId)) { notifySelect(instanceId); }

            e.preventDefault();
            canvasState = {
                mode: handle ? handle.getAttribute("data-fc-resize") : "move",
                instanceId: instanceId,
                block: block,
                startX: e.clientX,
                startY: e.clientY,
                base: currentGeo(block)
            };
            canvasState.geo = currentGeo(block);
            block.classList.add("is-dragging");
            try { block.setPointerCapture(e.pointerId); } catch (ex) { /* 무시 */ }
        });

        document.addEventListener("pointermove", function (e) {
            if (!canvasState) { return; }
            var useSnap = !e.altKey; // Alt = 스냅 해제(미세 조정)
            var dx = e.clientX - canvasState.startX;
            var dy = e.clientY - canvasState.startY;
            var b = canvasState.base;
            var geo = { x: b.x, y: b.y, w: b.w, h: b.h };
            var mode = canvasState.mode;

            if (mode === "move") {
                geo.x = snap(b.x + dx, useSnap);
                geo.y = snap(b.y + dy, useSnap);
            } else {
                if (mode.indexOf("e") !== -1) { geo.w = snap(b.w + dx, useSnap); }
                if (mode.indexOf("s") !== -1) { geo.h = snap(b.h + dy, useSnap); }
                if (mode.indexOf("w") !== -1) {
                    geo.x = snap(b.x + dx, useSnap);
                    geo.w = b.w + (b.x - geo.x);
                }
                if (mode.indexOf("n") !== -1) {
                    geo.y = snap(b.y + dy, useSnap);
                    geo.h = b.h + (b.y - geo.y);
                }
            }
            geo.x = clamp("layoutXPx", geo.x);
            geo.y = clamp("layoutYPx", geo.y);
            geo.w = clamp("layoutWPx", geo.w);
            geo.h = clamp("layoutHPx", geo.h);
            canvasState.geo = geo;
            canvasState.lastX = e.clientX;
            canvasState.lastY = e.clientY;
            applyGeo(canvasState.block, geo);

            // §17.8: 넣을 수 있는 패널 위에 있으면 그 패널을 강조한다(놓기 전에 보이게).
            var hover = (mode === "move") ? dropContainerAt(canvasState.block, e.clientX, e.clientY) : null;
            highlightDropContainer(hover);
        });

        document.addEventListener("pointerup", function () {
            if (!canvasState) { return; }
            var st = canvasState;
            canvasState = null;
            st.block.classList.remove("is-dragging");
            highlightDropContainer(null);
            if (st.instanceId == null) { return; }
            var g = st.geo;
            var b = st.base;

            // 이동이었다면 부모가 바뀌었는지 본다(패널에 넣기 / 패널에서 빼기).
            var reparent = null;
            if (st.mode === "move" && st.lastX != null) {
                reparent = resolveReparent(st, st.lastX, st.lastY);
            }
            if (!reparent && g.x === b.x && g.y === b.y && g.w === b.w && g.h === b.h) {
                return; // 순수 클릭
            }
            if (reparent) { g = { x: reparent.x, y: reparent.y, w: g.w, h: g.h }; }
            commitGeo(st.instanceId, g, reparent);
        });

        function highlightDropContainer(target) {
            var r2 = root();
            if (!r2) { return; }
            Array.prototype.forEach.call(r2.querySelectorAll(".is-drop-container"), function (b) {
                if (b !== target) { b.classList.remove("is-drop-container"); }
            });
            if (target) { target.classList.add("is-drop-container"); }
        }

        // 방향키 미세 이동(선택 블록). Shift = 스냅 단위, 기본 = 1px.
        r.addEventListener("keydown", function (e) {
            if (!slotMeta || !slotMeta.isFreeCanvas(lastArchetype)) { return; }
            var block = e.target && e.target.closest ? e.target.closest(".frg-fc-block") : null;
            if (!block) { return; }
            var step = e.shiftKey ? ((slotMeta.CANVAS_SNAP) || 8) : 1;
            var dx = 0;
            var dy = 0;
            if (e.key === "ArrowLeft") { dx = -step; }
            else if (e.key === "ArrowRight") { dx = step; }
            else if (e.key === "ArrowUp") { dy = -step; }
            else if (e.key === "ArrowDown") { dy = step; }
            else { return; }
            e.preventDefault();
            var geo = currentGeo(block);
            geo.x = clamp("layoutXPx", geo.x + dx);
            geo.y = clamp("layoutYPx", geo.y + dy);
            applyGeo(block, geo);
            commitGeo(block.getAttribute("data-instance"), geo);
        });

        // 팔레트발 드롭: 커서 위치가 곧 좌표다(§17 — 놓은 자리에 그대로).
        r.addEventListener("dragover", function (e) {
            if (!slotMeta || !slotMeta.isFreeCanvas(lastArchetype) || !lastPending) { return; }
            var sheet = e.target && e.target.closest ? e.target.closest(".frg-fc-canvas") : null;
            if (!sheet) { return; }
            e.preventDefault();
            try { e.dataTransfer.dropEffect = "copy"; } catch (ex) { /* 무시 */ }
        });

        r.addEventListener("drop", function (e) {
            if (!slotMeta || !slotMeta.isFreeCanvas(lastArchetype) || !lastPending) { return; }
            var sheet = e.target && e.target.closest ? e.target.closest(".frg-fc-canvas") : null;
            if (!sheet) { return; }
            e.preventDefault();
            postCanvasPlace(sheet, e.clientX, e.clientY);
        });
    }

    /** 맨 앞으로/맨 뒤로 — 현재 캔버스 인스턴스들의 z 범위를 보고 절대값을 정해 보낸다(§17.2 범위 클램프). */
    function postZOrder(instanceId, toFront) {
        if (!instanceId) { return; }
        var items = canvasItems(lastDef);
        var max = 0;
        var min = 0;
        items.forEach(function (inst, index) {
            var z = canvasGeometry(inst, index).z;
            if (z > max) { max = z; }
            if (z < min) { min = z; }
        });
        var next = toFront ? max + 1 : min - 1;
        var z = slotMeta ? slotMeta.clampCanvas("layoutZ", next) : next;
        post(MSG_CANVAS_LAYOUT, { instanceId: String(instanceId), z: z });
    }

    /**
     * 캔버스 좌표로 배치 확정(드롭·클릭 공용). §17.8: 패널 위에 놓으면 그 패널의 자식이 되고
     * 좌표는 패널 기준이 된다 — 기준 상자만 바뀌고 계산은 같다.
     */
    function postCanvasPlace(sheet, clientX, clientY) {
        var container = null;
        var stack = document.elementsFromPoint ? document.elementsFromPoint(clientX, clientY) : [];
        for (var i = 0; i < stack.length && !container; i++) {
            container = stack[i].closest ? stack[i].closest(".frg-fc-container") : null;
        }
        var base = container || sheet;
        var rect = base.getBoundingClientRect();
        var step = (slotMeta && slotMeta.CANVAS_SNAP) || 8;
        var x = Math.round((clientX - rect.left) / step) * step;
        var y = Math.round((clientY - rect.top) / step) * step;
        post(MSG_PLACE, {
            slotKey: CANVAS_SLOT,
            moduleTypeCode: String(lastPending.moduleTypeCode || ""),
            x: Math.max(0, x),
            y: Math.max(0, y),
            parentId: container ? container.getAttribute("data-instance") : null
        });
    }

    // ---------- 부모 → iframe 메시지 수신(🔒 origin 검증) ----------
    function onMessage(event) {
        if (event.origin !== window.location.origin) { return; }
        var data = event.data;
        if (!data || data.type !== MSG_DEFINITION) { return; }
        renderDefinition(data.definition, data.selectedId, data.archetype, data.pending);
    }

    function init() {
        window.addEventListener("message", onMessage, false);
        bindEvents();
        post(MSG_READY);
    }

    mod.renderDefinition = renderDefinition;

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})(window.MagicIAM_JSForgeAdminStudioPreview);
