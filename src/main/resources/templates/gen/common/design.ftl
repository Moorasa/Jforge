<#-- P9: 화면별 선언형 data/events 메타. 이 파일은 값을 실행하지 않고 안전하게 노출만 한다. -->
<#assign emitted = false>
window.JWorks_Design = window.JWorks_Design || {};
window.JWorks_Design["${jsString(stem)}"] = {
    modules: {
<#list slots?values as instances>
<#list instances as inst>
<#assign hasData = (inst["data"])?? && inst["data"]?is_hash>
<#assign hasEvents = (inst["events"])?? && inst["events"]?is_sequence && inst["events"]?size gt 0>
<#if hasData || hasEvents>
<#if emitted>,</#if>
        "${jsString((inst["instanceId"])!"")}": {
            moduleTypeCode: "${jsString((inst["moduleTypeCode"])!"")}",
<#if hasData>
            data: {
                endpoint: "${jsString((inst["data"]["endpoint"])!"")}",
                method: "${jsString((inst["data"]["method"])!"GET")}",
                resultPath: "${jsString((inst["data"]["resultPath"])!"")}",
                autoLoad: <#if ((inst["data"]["autoLoad"])!true)>true<#else>false</#if>
            }<#if hasEvents>,</#if>
</#if>
<#if hasEvents>
            events: [
<#list inst["events"] as handler>
                { event: "${jsString((handler["event"])!"")}", action: "${jsString((handler["action"])!"")}", target: "${jsString((handler["target"])!"")}" }<#if handler?has_next>,</#if>
</#list>
            ]
</#if>
        }
<#assign emitted = true>
</#if>
</#list>
</#list>
    }
};

<#-- P10: 선언형 설계 런타임. 같은 도메인 API만 조회하고, DOM에는 textContent로만 반영한다. -->
(function(win, doc, screenKey) {
    "use strict";
    var screen = win.JWorks_Design[screenKey];
    if (!screen || !screen.modules || win.JWorks_DesignRuntime && win.JWorks_DesignRuntime[screenKey]) {
        return;
    }

    var hasOwn = Object.prototype.hasOwnProperty;
    var dataStore = win.JWorks_DesignData = win.JWorks_DesignData || Object.create(null);
    var screenData = dataStore[screenKey] = dataStore[screenKey] || Object.create(null);
    var runtime = win.JWorks_DesignRuntime = win.JWorks_DesignRuntime || Object.create(null);

    function hasModule(instanceId) {
        return hasOwn.call(screen.modules, instanceId);
    }

    function rootFor(instanceId) {
        var roots = doc.querySelectorAll("[data-frg-instance-id]");
        for (var i = 0; i < roots.length; i += 1) {
            if (roots[i].getAttribute("data-frg-instance-id") === instanceId) {
                return roots[i];
            }
        }
        return null;
    }

    function emit(name, instanceId, extra) {
        var detail = { screen: screenKey, instanceId: instanceId, module: screen.modules[instanceId] || null };
        Object.keys(extra || {}).forEach(function(key) { detail[key] = extra[key]; });
        doc.dispatchEvent(new CustomEvent("frg:design:" + name, { detail: detail }));
        var root = rootFor(instanceId);
        if (root) {
            root.dispatchEvent(new CustomEvent("frg:design:" + name, { detail: detail }));
        }
    }

    function sameOriginPath(endpoint) {
        return typeof endpoint === "string" && /^\/(?!\/)[^\s\\]*$/.test(endpoint);
    }

    function valueAtPath(value, path) {
        if (!path) { return value; }
        var current = value;
        var parts = String(path).split(".");
        for (var i = 0; i < parts.length; i += 1) {
            if (current === null || typeof current !== "object" || !hasOwn.call(current, parts[i])) {
                return undefined;
            }
            current = current[parts[i]];
        }
        return current;
    }

    function displayValue(value) {
        if (value == null) { return ""; }
        if (typeof value !== "object") { return String(value); }
        try { return JSON.stringify(value); }
        catch (ignore) { return ""; }
    }

    function renderTable(root, rows) {
        if (!root || !Array.isArray(rows)) { return; }
        var body = root.querySelector("tbody");
        var headers = root.querySelectorAll("thead [data-name]");
        if (!body || !headers.length) { return; }
        var hasSelection = !!root.querySelector("thead .col-select");
        while (body.firstChild) { body.removeChild(body.firstChild); }
        rows.forEach(function(row) {
            var item = row && typeof row === "object" ? row : {};
            var tr = doc.createElement("tr");
            if (hasSelection) {
                var selectCell = doc.createElement("td");
                selectCell.className = "col-select";
                tr.appendChild(selectCell);
            }
            Array.prototype.forEach.call(headers, function(header) {
                var td = doc.createElement("td");
                var field = header.getAttribute("data-name") || "";
                td.textContent = displayValue(item[field]);
                tr.appendChild(td);
            });
            body.appendChild(tr);
        });
    }

    function applyData(instanceId, value) {
        var module = screen.modules[instanceId];
        var root = rootFor(instanceId);
        if (!module || !root) { return; }
        root.setAttribute("data-frg-data-state", "loaded");
        if (module.moduleTypeCode === "TABLE_VIEW") {
            renderTable(root, value);
        }
        emit("data", instanceId, { data: value });
    }

    function load(instanceId) {
        var module = hasModule(instanceId) ? screen.modules[instanceId] : null;
        var binding = module && module.data;
        if (!binding || !sameOriginPath(binding.endpoint)) {
            emit("error", instanceId, { error: "같은 도메인의 /... API 경로가 필요합니다." });
            return Promise.resolve(null);
        }
        var root = rootFor(instanceId);
        if (root) { root.setAttribute("data-frg-data-state", "loading"); }
        emit("before-load", instanceId, { endpoint: binding.endpoint });
        return win.fetch(binding.endpoint, {
            method: binding.method === "POST" ? "POST" : "GET",
            headers: { "Accept": "application/json" },
            credentials: "same-origin"
        }).then(function(response) {
            if (!response.ok) { throw new Error("HTTP " + response.status); }
            return response.json();
        }).then(function(responseData) {
            var value = valueAtPath(responseData, binding.resultPath);
            screenData[instanceId] = value;
            applyData(instanceId, value);
            return value;
        }).catch(function(error) {
            if (root) { root.setAttribute("data-frg-data-state", "error"); }
            emit("error", instanceId, { error: error && error.message ? error.message : "조회에 실패했습니다." });
            return null;
        });
    }

    function runAction(instanceId, handler, sourceEvent) {
        var target = handler.target || "";
        if (handler.action === "reload") {
            var targetId = hasModule(target) ? target : instanceId;
            emit("reload", instanceId, { target: targetId, sourceEvent: sourceEvent.type });
            load(targetId);
            return;
        }
        emit(handler.action, instanceId, { target: target, sourceEvent: sourceEvent.type });
    }

    function bindEvents(instanceId, module) {
        var root = rootFor(instanceId);
        if (!root || !Array.isArray(module.events)) { return; }
        module.events.forEach(function(handler) {
            if (!handler || !handler.event || !handler.action) { return; }
            root.addEventListener(handler.event, function(sourceEvent) {
                runAction(instanceId, handler, sourceEvent);
            });
        });
    }

    function init() {
        Object.keys(screen.modules).forEach(function(instanceId) {
            var module = screen.modules[instanceId];
            bindEvents(instanceId, module);
            if (module.data && module.data.autoLoad) { load(instanceId); }
        });
        emit("ready", "", {});
    }

    runtime[screenKey] = { load: load, getData: function(instanceId) { return screenData[instanceId]; } };
    if (doc.readyState === "loading") { doc.addEventListener("DOMContentLoaded", init); }
    else { init(); }
})(window, document, "${jsString(stem)}");
