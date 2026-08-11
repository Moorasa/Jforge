<#--
  FREE_CANVAS per-screen JS (계약 §17.4). 인스턴스 초기화를 **이 파일 하나**에 모은다
  (모듈별 JS 파일을 만들지 않으므로 같은 모듈을 N개 놓아도 파일 충돌이 없다).
  §17.8 중첩: canvasTree 를 shell/shellCss 와 같은 순서로 걸어 seq 를 그대로 쓴다.
  🔒 문자열은 jsString() 으로만. 번호는 서버가 만든 정수(데이터 아님).
-->
<#assign nodes = (canvasTree)![]>
<#assign Domain = stem?cap_first>
<#macro fcInit items>
<#list items as inst>
        // [${inst.seq?c}] ${jsString((inst.moduleTypeCode)!"")}
<#if (inst.moduleTypeCode)! == "TABLE_VIEW">
        // TODO: 목록 조회 API 를 연결한다(§14 서버 바인딩 또는 {stem}Design.js).
<#elseif (inst.moduleTypeCode)! == "BUTTON">
        screen.item(${inst.seq?c}).find("button").on("click", function() {
            // TODO: 동작을 연결한다.
        });
<#elseif (inst.moduleTypeCode)! == "SEARCH_FILTER_BAR">
        // TODO: 검색 조건 수집 후 목록을 다시 조회한다.
</#if>
<#if (inst.children)?? && (inst.children?size gt 0)>
<@fcInit items=inst.children/>
</#if>
</#list>
</#macro>
window.JWorks_JS${Domain}Canvas = window.JWorks_JS${Domain}Canvas || {};
(function(screen) {
    "use strict";
    if (screen.__defined) { return; }
    screen.__defined = true;

    var $root = $("#${stem}-canvas");

    /** 캔버스 항목 1건을 위치 클래스(.frg-fc-N)로 집는다 — 좌표는 CSS 가 갖는다(§17.3). */
    screen.item = function(index) {
        return $root.find(".frg-fc-" + index);
    };

    screen.init = function() {
<@fcInit items=nodes/>
    };

    $(screen.init);
})(window.JWorks_JS${Domain}Canvas);
