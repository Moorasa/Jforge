<#--
  FREE_CANVAS shell (계약 §17.4/§17.8) — 자유 배치 화면의 단일 JSP.
  캔버스 인스턴스 본문은 **여기 인라인**으로 찍는다(모듈별 파일 include 없음) → 같은 모듈을
  몇 개 놓아도 파일명 충돌이 없다(§17.4). 좌표는 CSS(.frg-fc-N)가 담당한다.

  §17.8 중첩: canvasTree(부모→자식)를 재귀로 걸으며 DOM 을 그대로 중첩시킨다. 자식이 부모 상자
  기준으로 놓이는 것은 CSS 의 absolute 컨테이닝 블록이 자동으로 해 준다 — 좌표 변환 코드 0.
  seq 는 TemplateContextBuilder 가 부여한 값을 그대로 쓴다(shellCss 와 같은 번호 — 드리프트 불가).

  🔒 자유문자열은 htmlText/htmlAttr 로만. 클래스의 번호는 서버가 만든 정수(데이터 아님).
-->
<#assign nodes = (canvasTree)![]>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${r"${pageContext.request.contextPath}"}" />
<#macro fcItems items depth=1>
<#list items as inst>
<#assign props = (inst.props)!{}>
<#assign idx = inst.seq>
<#-- 🔒 재귀 매크로라 반드시 local — assign 은 템플릿 전역이라 자식 호출이 부모 값을 덮는다. -->
<#local fcPad = ""?left_pad(8 + 4 * (depth - 1))>
<#assign code = (inst.moduleTypeCode)!"">
<#--
  파셜 본문을 일단 잡아 두고, 깊이만큼 통째로 민다. 파셜 19종은 전부 markup 기준 들여쓰기가
  12칸이므로 "줄바꿈 + 12칸"을 "줄바꿈 + fcPad + 4칸"으로 바꾸면 파셜 내부의 더 깊은 들여쓰기
  (16/20칸)는 그 차이만큼 그대로 보존된다. 파셜은 한 줄도 고치지 않는다.
-->
<#local fcBody>
<#if code == "PANEL">
    <#include "item/panel.ftl">
<#elseif code == "BUTTON">
    <#include "item/button.ftl">
<#elseif code == "LABEL">
    <#include "item/label.ftl">
<#elseif code == "TEXT_INPUT">
    <#include "item/textInput.ftl">
<#elseif code == "IMAGE">
    <#include "item/image.ftl">
<#elseif code == "TABLE_VIEW">
    <#include "item/tableView.ftl">
<#elseif code == "CARD_VIEW">
    <#include "item/cardView.ftl">
<#elseif code == "TREE_VIEW">
    <#include "item/treeView.ftl">
<#elseif code == "FORM_VIEW">
    <#include "item/formView.ftl">
<#elseif code == "DETAIL_BASIC">
    <#include "item/detailBasic.ftl">
<#elseif code == "ASSOCIATE_TABS">
    <#include "item/associateTabs.ftl">
<#elseif code == "POPUP_FORM">
    <#include "item/popupForm.ftl">
<#elseif code == "LAYOUT_FRAME">
    <#include "item/layoutFrame.ftl">
<#elseif code == "TOOLBAR">
    <#include "item/toolbar.ftl">
<#elseif code == "SEARCH_FILTER_BAR">
    <#include "item/searchFilterBar.ftl">
<#elseif code == "BAR_CHART">
    <#include "item/barChart.ftl">
<#elseif code == "SEMICIRCLE_CHART">
    <#include "item/semicircleChart.ftl">
<#elseif code == "EMPTY_STATE">
    <#include "item/emptyState.ftl">
<#elseif code == "CHAT_WIDGET">
    <#include "item/chatWidget.ftl">
<#else>
            <!-- 미지원 모듈: ${htmlText(code)} (§17.4 파셜 미제공 — 자리만 유지) -->
</#if>
</#local>
${fcPad}<div class="frg-fc-item<#if (inst.isCanvasContainer)!false> frg-fc-container</#if> frg-fc-${inst.seq?c}"<#if (inst["data"])?? || ((inst["events"])?? && inst["events"]?is_sequence && inst["events"]?size gt 0)> data-frg-instance-id="${htmlAttr(inst.instanceId!"")}" data-frg-module-type="${htmlAttr(inst.moduleTypeCode!"")}"</#if>>${("\n" + fcBody?chop_linebreak)?replace("\n            ", "\n" + fcPad + "    ")}
<#--
  §17.12 컨테이너는 자식을 **전용 내용 상자**(.frg-fc-panel-body) 안에 담는다 — "상자 안 상자".
  왜 패널 div 안에 직접 넣지 않는가: `.frg-fc-panel` 은 테두리를 갖는다. 절대배치 자식의 기준
  상자는 위치 지정 조상의 **패딩 박스**라, 패널 안에 넣으면 좌표가 테두리 두께만큼 밀리고
  그 이동량이 borderYn 값에 따라 달라진다. 내용 상자는 테두리가 없어 좌표가 그대로다.
-->
<#if (inst.isCanvasContainer)!false>
${fcPad}    <div class="frg-fc-panel-body">
<#if (inst.children)?? && (inst.children?size gt 0)>
<@fcItems items=inst.children depth=depth+2/>
</#if>
${fcPad}    </div>
<#elseif (inst.children)?? && (inst.children?size gt 0)>
<#-- 컨테이너가 아닌데 자식이 남아 있으면(데이터 손상) 종전대로 래퍼 직계로 둔다 — 화면을 지우지 않는다. -->
<@fcItems items=inst.children depth=depth+1/>
</#if>
${fcPad}</div>
</#list>
</#macro>
<section id="${stem}-canvas" class="frg-fc-screen">
    <link rel="stylesheet" href="${r"${ctx}"}/css/${htmlAttr(role)}/${stem}/${stem}.css" />
    <script defer src="${r"${ctx}"}/js/${htmlAttr(role)}/${stem}/${stem}.js"></script>
<#if (hasDesignMetadata)!false>
    <script defer src="${r"${ctx}"}/js/${htmlAttr(role)}/${stem}/${stem}Design.js"></script>
</#if>
    <div class="frg-fc-sheet">
<@fcItems items=nodes/>
    </div>
</section>
