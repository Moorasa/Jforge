/* ===============================================================================================
Name : schemaFormRenderer.js
Description : PROP_SCHEMA_JSON 선언 스키마 -> 속성폼 DOM 을 그리는 순수 렌더 함수 (P2-5).
             화면(list.jsp)과 결합하지 않는 재사용 모듈. P3 스튜디오 우측 속성패널로 승격 예정.

XSS 신뢰경계(스키마_PROP_SCHEMA.md §2.1): label/title/options[].label/columns[].label 등 표시
문자열은 서버가 이스케이프하지 않고 원문 전달한다. 따라서 이 렌더러는 모든 텍스트를
document.createTextNode / .textContent 로만 삽입하며 innerHTML(및 jQuery .html())을 절대 쓰지 않는다.

지원 type: text / number / boolean / select / columns / chips.
미지원 type 은 해당 필드만 스킵하고 console.warn (에러 미발생, forward-compat).

값 바인딩 확장(P3-4):
 - render(schema[, values]) — 선택적 2번째 인자. values 가 있으면 각 필드 초기 표시값을
   values[field.key](없으면 field.default)로 채운다. 미지정 시 기존 default 동작(하위호환).
 - collect(rootEl, schema) — 렌더된 폼 DOM(rootEl)에서 값을 읽어 {key: value} 로 직렬화하는
   순수 함수(rootEl 인자로만 DOM 접근, 전역 DOM 결합 금지). type 별 정확 역직렬화.
=============================================================================================== */
window.MagicIAM_JSForgeSchemaRenderer = window.MagicIAM_JSForgeSchemaRenderer || {};
(function (mod) {
    "use strict";
    if (mod.__defined) { return; }
    mod.__defined = true;

    var SUPPORTED = { text: 1, number: 1, boolean: 1, select: 1, columns: 1, chips: 1 };

    // --- 안전한 DOM 헬퍼: 텍스트는 반드시 textContent/createTextNode 로만 주입 ---
    function el(tag, className) {
        var node = document.createElement(tag);
        if (className) { node.className = className; }
        return node;
    }

    // label(사용자스키마 문자열) 을 안전하게 텍스트로만 넣는다. required 면 별표를 별도 노드로 부착.
    function fieldLabel(text, required) {
        var lab = el("label", "frg-fld-label");
        lab.textContent = text == null ? "" : String(text); // innerHTML 금지
        if (required) {
            var star = el("span", "frg-required");
            star.textContent = "*";
            star.setAttribute("aria-label", "필수");
            lab.appendChild(star);
        }
        return lab;
    }

    function fieldWrap(field) {
        var wrap = el("div", "frg-fld frg-fld-" + field.type);
        wrap.setAttribute("data-key", field.key); // key 는 화이트리스트 통과 식별자(setAttribute 안전)
        wrap.appendChild(fieldLabel(field.label, field.required));
        return wrap;
    }

    // --- 값 해석: values 가 주어지고 해당 key 를 가지면 그 값, 아니면 field.default ---
    // (values 미지정 시 undefined 반환 → 각 렌더러가 field.default 로 폴백. 하위호환 유지.)
    function resolveValue(field, values) {
        if (values && Object.prototype.hasOwnProperty.call(values, field.key)) {
            return values[field.key];
        }
        return undefined;
    }

    // --- type 별 렌더러: value(해석된 초기값, undefined 면 field.default 폴백) ---
    function renderText(field, value) {
        var wrap = fieldWrap(field);
        var input = el("input", "frg-input");
        input.type = "text";
        input.name = field.key;
        if (field.placeholder != null) { input.placeholder = String(field.placeholder); }
        var v = value !== undefined ? value : field.default;
        if (v != null) { input.value = String(v); }
        wrap.appendChild(input);
        return wrap;
    }

    function renderNumber(field, value) {
        var wrap = fieldWrap(field);
        var input = el("input", "frg-input");
        input.type = "number";
        input.name = field.key;
        var v = value !== undefined ? value : field.default;
        if (v != null && v !== "") { input.value = String(v); }
        wrap.appendChild(input);
        return wrap;
    }

    function renderBoolean(field, value) {
        var wrap = el("div", "frg-fld frg-fld-boolean");
        wrap.setAttribute("data-key", field.key);
        var lab = el("label", "frg-check-label");
        var input = el("input", "frg-check");
        input.type = "checkbox";
        input.name = field.key;
        var v = value !== undefined ? value : field.default;
        if (v === true) { input.checked = true; }
        lab.appendChild(input);
        // 체크박스 라벨 텍스트 — textContent 로만
        var span = el("span");
        span.textContent = field.label == null ? "" : String(field.label);
        lab.appendChild(span);
        if (field.required) {
            var star = el("span", "frg-required");
            star.textContent = "*";
            lab.appendChild(star);
        }
        wrap.appendChild(lab);
        return wrap;
    }

    function renderSelect(field, value) {
        var wrap = fieldWrap(field);
        var sel = el("select", "frg-input");
        sel.name = field.key;
        var v = value !== undefined ? value : field.default;
        var opts = Array.isArray(field.options) ? field.options : [];
        opts.forEach(function (o) {
            var opt = el("option");
            opt.value = o && o.value != null ? String(o.value) : "";
            opt.textContent = o && o.label != null ? String(o.label) : ""; // 표시문자열 textContent
            if (v != null && String(v) === opt.value) { opt.selected = true; }
            sel.appendChild(opt);
        });
        wrap.appendChild(sel);
        return wrap;
    }

    // columns: 반복행 그리드. 헤더행 + 값 행들. 셀 type 은 단순타입(text/number/boolean/select)만.
    // 편집 동작(행 추가·복제·삭제·순서)은 하단 handleAction()이 DOM 수준에서 처리하고,
    // 속성 패널이 collect() 결과를 DEFINITION_JSON으로 반영한다.
    function renderColumns(field, value) {
        var wrap = fieldWrap(field);
        var cols = Array.isArray(field.columns) ? field.columns : [];
        var actions = el("div", "frg-grid-actions");
        var add = el("button", "frg-btn frg-btn-secondary frg-grid-add", "+ 행 추가");
        add.type = "button";
        add.setAttribute("data-grid-action", "add");
        add.setAttribute("data-grid-key", String(field.key == null ? "" : field.key));
        actions.appendChild(add);
        wrap.appendChild(actions);
        var table = el("table", "frg-grid");

        var thead = el("thead");
        var htr = el("tr");
        cols.forEach(function (c) {
            var th = el("th");
            th.textContent = c && c.label != null ? String(c.label) : ""; // 컬럼 라벨 textContent
            htr.appendChild(th);
        });
        htr.appendChild(el("th", "frg-grid-actions-head", "편집"));
        thead.appendChild(htr);
        table.appendChild(thead);

        var tbody = el("tbody");
        tbody.className = "frg-grid-body";
        var src = value !== undefined ? value : field.default;
        var rows = Array.isArray(src) ? src : [];
        if (!rows.length) {
            var etr = el("tr");
            var etd = el("td", "frg-empty");
            etd.colSpan = Math.max(cols.length + 1, 1);
            etd.textContent = "행이 없습니다.";
            etr.appendChild(etd);
            tbody.appendChild(etr);
        } else {
            rows.forEach(function (row) {
                tbody.appendChild(buildGridRow(cols, row, field.key));
            });
        }
        table.appendChild(tbody);
        wrap.appendChild(table);
        return wrap;
    }

    function buildGridRow(cols, row, fieldKey) {
        var tr = el("tr", "frg-grid-row");
        cols.forEach(function (c) {
            var td = el("td");
            var cellKey = c && c.key != null ? String(c.key) : "";
            var cellType = c && c.type != null ? String(c.type) : "text";
            var val = row ? row[cellKey] : undefined;
            td.appendChild(buildCell(cellType, fieldKey + "." + cellKey, cellKey, val, c));
            tr.appendChild(td);
        });
        var actionCell = el("td", "frg-grid-row-actions");
        [
            ["up", "↑", "위로 이동"],
            ["down", "↓", "아래로 이동"],
            ["duplicate", "⧉", "행 복제"],
            ["remove", "×", "행 삭제"]
        ].forEach(function (spec) {
            var button = el("button", "frg-grid-row-btn frg-grid-row-btn-" + spec[0], spec[1]);
            button.type = "button";
            button.title = spec[2];
            button.setAttribute("aria-label", spec[2]);
            button.setAttribute("data-grid-action", spec[0]);
            button.setAttribute("data-grid-key", String(fieldKey == null ? "" : fieldKey));
            actionCell.appendChild(button);
        });
        tr.appendChild(actionCell);
        return tr;
    }

    function blankGridRow(cols) {
        var out = {};
        cols.forEach(function (c) {
            if (!c || c.key == null) { return; }
            if (Object.prototype.hasOwnProperty.call(c, "default")) {
                out[c.key] = c.default;
            } else if (c.type === "boolean") {
                out[c.key] = false;
            } else if (c.type === "select" && Array.isArray(c.options) && c.options.length) {
                out[c.key] = c.options[0] && c.options[0].value != null ? c.options[0].value : "";
            } else {
                out[c.key] = "";
            }
        });
        return out;
    }

    function buildCell(cellType, name, cellKey, val, column) {
        var input;
        if (cellType === "boolean") {
            input = el("input", "frg-check frg-cell");
            input.type = "checkbox";
            input.name = name;
            if (val === true) { input.checked = true; }
        } else if (cellType === "number") {
            input = el("input", "frg-input frg-cell");
            input.type = "number";
            input.name = name;
            if (val != null && val !== "") { input.value = String(val); }
        } else if (cellType === "select") {
            input = el("select", "frg-input frg-cell");
            input.name = name;
            var options = column && Array.isArray(column.options) ? column.options : [];
            options.forEach(function (option) {
                var opt = el("option");
                opt.value = option && option.value != null ? String(option.value) : "";
                opt.textContent = option && option.label != null ? String(option.label) : "";
                if (val != null && String(val) === opt.value) { opt.selected = true; }
                input.appendChild(opt);
            });
        } else {
            // text(및 그 외 단순타입) 기본 처리
            input = el("input", "frg-input frg-cell");
            input.type = "text";
            input.name = name;
            if (val != null) { input.value = String(val); }
        }
        // collect() 역직렬화용 셀 메타. cellKey/cellType 은 스키마 화이트리스트 식별자(setAttribute 안전).
        input.setAttribute("data-cell-key", cellKey);
        input.setAttribute("data-cell-type", cellType);
        return input;
    }

    // chips: 문자열 배열을 태그(chip) 로 표시 + 신규입력. 각 chip 텍스트는 textContent.
    function renderChips(field, value) {
        var wrap = fieldWrap(field);
        var box = el("div", "frg-chips");
        var src = value !== undefined ? value : field.default;
        var items = Array.isArray(src) ? src : [];
        items.forEach(function (v) {
            var chip = el("button", "frg-chip");
            chip.type = "button";
            chip.title = "값 삭제";
            chip.setAttribute("aria-label", "값 삭제: " + String(v == null ? "" : v));
            chip.setAttribute("data-chip-remove", "1");
            chip.setAttribute("data-chip-value", v == null ? "" : String(v)); // collect() 용
            chip.textContent = String(v == null ? "" : v) + " ×"; // chip 값 textContent
            box.appendChild(chip);
        });
        var input = el("input", "frg-input frg-chip-input");
        input.type = "text";
        input.name = field.key;
        input.placeholder = "값 입력 후 Enter";
        box.appendChild(input);
        wrap.appendChild(box);
        return wrap;
    }

    function fieldWrapByKey(root, fieldKey) {
        return root ? root.querySelector('.frg-fld[data-key="' + cssKey(fieldKey) + '"]') : null;
    }

    function gridColumns(schema, fieldKey) {
        var fields = schema && Array.isArray(schema.fields) ? schema.fields : [];
        for (var i = 0; i < fields.length; i++) {
            if (fields[i] && fields[i].key === fieldKey && fields[i].type === "columns") {
                return Array.isArray(fields[i].columns) ? fields[i].columns : [];
            }
        }
        return [];
    }

    function removeGridEmptyRow(tbody) {
        if (!tbody) { return; }
        var empty = tbody.querySelector("tr:not(.frg-grid-row)");
        if (empty && empty.parentNode) { empty.parentNode.removeChild(empty); }
    }

    function addGridEmptyRow(tbody, cols) {
        if (!tbody || tbody.querySelector(".frg-grid-row")) { return; }
        var tr = el("tr");
        var td = el("td", "frg-empty", "행이 없습니다.");
        td.colSpan = Math.max(cols.length + 1, 1);
        tr.appendChild(td);
        tbody.appendChild(tr);
    }

    /**
     * 속성 패널의 클릭 이벤트에서 호출한다. 스키마로 허용된 columns/chips DOM만 조작하고
     * true를 반환하면 호출자가 collect() 결과를 즉시 저장 상태에 반영한다.
     */
    function handleAction(root, schema, event) {
        var target = event && event.target;
        var actionButton = target && target.closest ? target.closest("[data-grid-action]") : null;
        if (actionButton) {
            var action = actionButton.getAttribute("data-grid-action");
            var fieldKey = actionButton.getAttribute("data-grid-key");
            var wrap = fieldWrapByKey(root, fieldKey);
            var tbody = wrap ? wrap.querySelector(".frg-grid-body") : null;
            var cols = gridColumns(schema, fieldKey);
            if (!tbody || !cols.length) { return false; }
            var row = actionButton.closest ? actionButton.closest(".frg-grid-row") : null;
            if (action === "add") {
                removeGridEmptyRow(tbody);
                tbody.appendChild(buildGridRow(cols, blankGridRow(cols), fieldKey));
            } else if (!row) {
                return false;
            } else if (action === "remove") {
                row.parentNode.removeChild(row);
                addGridEmptyRow(tbody, cols);
            } else if (action === "duplicate") {
                var copy = collectGridRow(row);
                var duplicate = buildGridRow(cols, copy, fieldKey);
                row.parentNode.insertBefore(duplicate, row.nextSibling);
            } else if (action === "up") {
                var prev = row.previousElementSibling;
                if (prev && prev.classList.contains("frg-grid-row")) {
                    row.parentNode.insertBefore(row, prev);
                }
            } else if (action === "down") {
                var next = row.nextElementSibling;
                if (next && next.classList.contains("frg-grid-row")) {
                    row.parentNode.insertBefore(next, row);
                }
            } else {
                return false;
            }
            return true;
        }

        var chip = target && target.closest ? target.closest("[data-chip-remove]") : null;
        if (chip && chip.parentNode) {
            chip.parentNode.removeChild(chip);
            return true;
        }
        return false;
    }

    function handleKeydown(root, event) {
        var target = event && event.target;
        if (!target || event.key !== "Enter" || !target.classList || !target.classList.contains("frg-chip-input")) {
            return false;
        }
        var value = String(target.value == null ? "" : target.value).trim();
        if (!value) { return false; }
        var chip = el("button", "frg-chip");
        chip.type = "button";
        chip.title = "값 삭제";
        chip.setAttribute("aria-label", "값 삭제: " + value);
        chip.setAttribute("data-chip-remove", "1");
        chip.setAttribute("data-chip-value", value);
        chip.textContent = value + " ×";
        target.parentNode.insertBefore(chip, target);
        target.value = "";
        return true;
    }

    var DISPATCH = {
        text: renderText,
        number: renderNumber,
        boolean: renderBoolean,
        select: renderSelect,
        columns: renderColumns,
        chips: renderChips
    };

    /**
     * PROP_SCHEMA_JSON 객체 -> 폼 DOM(fragment) 을 생성해 반환하는 순수 함수.
     * @param {{title:string, fields:Array}} schema
     * @param {Object} [values] 선택적. 각 필드의 초기 표시값을 values[field.key](없으면 field.default)로
     *                          채운다. 미지정 시 기존처럼 field.default 만 사용(P2-5 하위호환).
     * @returns {DocumentFragment}
     */
    function render(schema, values) {
        var frag = document.createDocumentFragment();
        if (!schema || typeof schema !== "object") {
            var warn = el("p", "frg-empty");
            warn.textContent = "유효한 스키마가 없습니다.";
            frag.appendChild(warn);
            return frag;
        }

        var titleNode = el("h3", "frg-schema-title");
        titleNode.textContent = schema.title == null ? "" : String(schema.title); // title textContent
        frag.appendChild(titleNode);

        var form = el("form", "frg-schema-form");
        form.setAttribute("autocomplete", "off");
        form.addEventListener("submit", function (e) { e.preventDefault(); });

        var fields = Array.isArray(schema.fields) ? schema.fields : [];
        fields.forEach(function (field) {
            if (!field || typeof field !== "object") { return; }
            var type = String(field.type);
            if (!SUPPORTED[type]) {
                // §1.3 미지원 type: 스킵 + 경고(에러 미발생)
                console.warn("[PROP_SCHEMA] unsupported field type '" + type + "' (key=" + field.key + "), skipped");
                return;
            }
            // values 미지정이면 resolveValue 가 undefined → 렌더러가 field.default 로 폴백(하위호환).
            form.appendChild(DISPATCH[type](field, resolveValue(field, values)));
        });
        frag.appendChild(form);
        return frag;
    }

    // =============================================================================================
    // collect(rootEl, schema): 렌더된 폼 DOM(rootEl) 에서 값을 읽어 {key: value} 오브젝트로 직렬화.
    //   - 순수 함수: rootEl 을 인자로 받아서만 DOM 접근(전역 document 미결합).
    //   - type 별 정확 역직렬화: text→string, number→number(또는 ""는 스킵), boolean→bool,
    //     select→선택 value(string), columns→행 오브젝트 배열, chips→string[].
    //   - 스키마 fields[].key 에 정의된 키만 수집(§3 키 제약). 미지원 type 필드는 렌더 안 됐으므로 스킵.
    // =============================================================================================
    function collectText(root, field) {
        var input = root.querySelector('.frg-fld[data-key="' + cssKey(field.key) + '"] input[name="' + cssKey(field.key) + '"]');
        return input ? input.value : undefined;
    }

    function collectNumber(root, field) {
        var input = root.querySelector('.frg-fld[data-key="' + cssKey(field.key) + '"] input[name="' + cssKey(field.key) + '"]');
        if (!input) { return undefined; }
        var raw = input.value;
        if (raw === "" || raw == null) { return undefined; } // 빈값은 미설정으로 취급
        var n = Number(raw);
        return isNaN(n) ? undefined : n;
    }

    function collectBoolean(root, field) {
        var input = root.querySelector('.frg-fld-boolean[data-key="' + cssKey(field.key) + '"] input[type="checkbox"]');
        return input ? !!input.checked : undefined;
    }

    function collectSelect(root, field) {
        var sel = root.querySelector('.frg-fld[data-key="' + cssKey(field.key) + '"] select[name="' + cssKey(field.key) + '"]');
        return sel ? sel.value : undefined;
    }

    function collectColumns(root, field) {
        var wrap = root.querySelector('.frg-fld[data-key="' + cssKey(field.key) + '"]');
        if (!wrap) { return undefined; }
        var trs = wrap.querySelectorAll("tbody .frg-grid-row");
        var out = [];
        Array.prototype.forEach.call(trs, function (tr) {
            out.push(collectGridRow(tr));
        });
        return out;
    }

    function collectGridRow(tr) {
        var cells = tr ? tr.querySelectorAll("[data-cell-key]") : [];
        var rowObj = {};
        Array.prototype.forEach.call(cells, function (input) {
            var k = input.getAttribute("data-cell-key");
            var t = input.getAttribute("data-cell-type");
            if (!k) { return; }
            if (t === "boolean") {
                rowObj[k] = !!input.checked;
            } else if (t === "number") {
                var raw = input.value;
                if (raw === "" || raw == null) { rowObj[k] = null; }
                else { var n = Number(raw); rowObj[k] = isNaN(n) ? null : n; }
            } else {
                rowObj[k] = input.value;
            }
        });
        return rowObj;
    }

    function collectChips(root, field) {
        var wrap = root.querySelector('.frg-fld[data-key="' + cssKey(field.key) + '"]');
        if (!wrap) { return undefined; }
        var chips = wrap.querySelectorAll(".frg-chip[data-chip-value]");
        var out = [];
        Array.prototype.forEach.call(chips, function (c) {
            out.push(c.getAttribute("data-chip-value"));
        });
        return out;
    }

    // key 를 CSS 속성 셀렉터에 안전히 넣기 위한 이스케이프. key 는 이미 ^[a-z][a-zA-Z0-9]*$
    // 화이트리스트 통과값이지만(PROP_SCHEMA §1.1), 방어적으로 " 와 \\ 를 이스케이프한다.
    function cssKey(key) {
        return String(key == null ? "" : key).replace(/\\/g, "\\\\").replace(/"/g, '\\"');
    }

    var COLLECT = {
        text: collectText,
        number: collectNumber,
        boolean: collectBoolean,
        select: collectSelect,
        columns: collectColumns,
        chips: collectChips
    };

    /**
     * 렌더된 폼 DOM 에서 값을 읽어 {key: value} 로 직렬화한다.
     * @param {Element} rootEl render() 결과가 들어간 컨테이너(또는 그 상위)
     * @param {{fields:Array}} schema 렌더에 사용한 동일 스키마
     * @returns {Object} 스키마 fields[].key 에 대응하는 값 맵
     */
    function collect(rootEl, schema) {
        var out = {};
        if (!rootEl || !schema || typeof schema !== "object") { return out; }
        var fields = Array.isArray(schema.fields) ? schema.fields : [];
        fields.forEach(function (field) {
            if (!field || typeof field !== "object") { return; }
            var type = String(field.type);
            var fn = COLLECT[type];
            if (!fn) { return; } // 미지원 type 은 렌더 안 됨 → 수집 스킵
            var v = fn(rootEl, field);
            if (v !== undefined) { out[field.key] = v; }
        });
        return out;
    }

    mod.render = render;
    mod.collect = collect;
    mod.handleAction = handleAction;
    mod.handleKeydown = handleKeydown;
})(window.MagicIAM_JSForgeSchemaRenderer);
