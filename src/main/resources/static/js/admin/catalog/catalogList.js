/* ===============================================================================================
Name : catalogList.js
Description : 모듈 카탈로그 도그푸딩 화면 (P2-5). 좌측 목록(/api/module-types) 선택 시
             우측 슬롯에 PROP_SCHEMA_JSON 속성폼을 렌더(schemaFormRenderer 위임).

XSS: 모든 텍스트 삽입은 textContent/createTextNode 로만. innerHTML(및 jQuery .html()) 미사용.
=============================================================================================== */
window.JWorks_JSForgeAdminCatalog = window.JWorks_JSForgeAdminCatalog || {};
(function (page) {
    "use strict";
    if (page.__defined) { return; }
    page.__defined = true;

    var ctx = (window.JWorks_JSForge && window.JWorks_JSForge.contextPath) || "";
    var api = ctx + "/api/module-types";
    var renderer = window.JWorks_JSForgeSchemaRenderer;

    function elText(tag, className, text) {
        var node = document.createElement(tag);
        if (className) { node.className = className; }
        if (text != null) { node.textContent = String(text); } // textContent 로만
        return node;
    }

    function clear(node) {
        while (node.firstChild) { node.removeChild(node.firstChild); }
    }

    function slotMessage(msg) {
        var slot = document.getElementById("frg-schema-slot");
        clear(slot);
        slot.appendChild(elText("p", "frg-empty", msg));
    }

    // --- 좌측 목록 렌더 ---
    function renderList(list) {
        var ul = document.getElementById("frg-catalog-items");
        clear(ul);
        if (!Array.isArray(list) || !list.length) {
            ul.appendChild(elText("li", "frg-empty", "등록된 모듈이 없습니다."));
            return;
        }
        list.forEach(function (m) {
            var li = document.createElement("li");
            li.className = "frg-catalog-item";
            li.setAttribute("data-code", m.moduleTypeCode); // 코드값 setAttribute
            li.setAttribute("role", "option");
            li.tabIndex = 0;
            // 모듈명 + 카테고리 뱃지 (모두 textContent)
            li.appendChild(elText("span", "frg-item-name", m.moduleName));
            var badge = elText("span", "frg-item-badge", m.categoryCode);
            // 카테고리 색 구분(forge-theme [data-cat] 셀렉터). setAttribute 값 — HTML 파싱 없음.
            badge.setAttribute("data-cat", String(m.categoryCode == null ? "" : m.categoryCode));
            li.appendChild(badge);
            li.appendChild(elText("span", "frg-item-code", m.moduleTypeCode));
            ul.appendChild(li);
        });
    }

    function markActive(code) {
        var items = document.querySelectorAll("#frg-catalog-items .frg-catalog-item");
        Array.prototype.forEach.call(items, function (li) {
            if (li.getAttribute("data-code") === code) { li.classList.add("is-active"); }
            else { li.classList.remove("is-active"); }
        });
    }

    // --- 우측: 선택 모듈 스키마 로드 후 폼 렌더 ---
    function loadSchema(code) {
        markActive(code);
        slotMessage("불러오는 중…");
        fetch(api + "/" + encodeURIComponent(code), { headers: { "Accept": "application/json" } })
            .then(function (r) {
                if (!r.ok) { throw new Error("http " + r.status); }
                return r.json();
            })
            .then(function (m) {
                var slot = document.getElementById("frg-schema-slot");
                clear(slot);
                // propSchemaJson 은 서버가 원문 JSON 트리로 전달(도메인 @JsonRawValue) → 이미 객체.
                var schema = m.propSchemaJson;
                if (typeof schema === "string") {
                    // 방어: 혹시 문자열이면 파싱(정상 경로에선 객체)
                    try { schema = JSON.parse(schema); } catch (e) { schema = null; }
                }
                slot.appendChild(renderer.render(schema));
            })
            .catch(function () { slotMessage("스키마를 불러오지 못했습니다."); });
    }

    function load() {
        fetch(api, { headers: { "Accept": "application/json" } })
            .then(function (r) { return r.json(); })
            .then(renderList)
            .catch(function () {
                var ul = document.getElementById("frg-catalog-items");
                clear(ul);
                ul.appendChild(elText("li", "frg-empty", "목록을 불러오지 못했습니다."));
            });
    }

    function bind() {
        var ul = document.getElementById("frg-catalog-items");
        ul.addEventListener("click", function (e) {
            var li = e.target.closest ? e.target.closest(".frg-catalog-item") : null;
            if (li) { loadSchema(li.getAttribute("data-code")); }
        });
        ul.addEventListener("keydown", function (e) {
            if (e.key !== "Enter" && e.key !== " ") { return; }
            var li = e.target.closest ? e.target.closest(".frg-catalog-item") : null;
            if (li) { e.preventDefault(); loadSchema(li.getAttribute("data-code")); }
        });
    }

    document.addEventListener("DOMContentLoaded", function () {
        if (!renderer || typeof renderer.render !== "function") {
            slotMessage("렌더러 로드 실패");
            return;
        }
        bind();
        load();
    });
})(window.JWorks_JSForgeAdminCatalog);
