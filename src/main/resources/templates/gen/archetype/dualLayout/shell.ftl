<#-- P5-5c: DUAL_LAYOUT(좌우 2단) 아키타입 shell. JWorks commonSection.js dual 런타임 1:1(계약 §10). -->
<#--
  아티팩트: dualShell → {stem}.jsp (계약 §10.1)
  역할: body.dual-layout + #dual-layout-area(.layout-left>iframe / .layout-middle.resizer / .layout-right>iframe).
        리사이저 드래그·비율조정은 commonSection.js 자체 $(function)(364행)가 자동 바인딩 → shell은 DOM만.
        각 패인 iframe은 leftArea/rightArea의 LAYOUT_FRAME 인스턴스 1개 = iframe 1개(405행 > iframe 계약).
  🔒 자유문자열 전량 GenEscaper 경유(계약 §10.3):
    - frame.frameId → htmlAttr(iframe id) / frame.title → htmlAttr(iframe title) / frame.paneClass → cssToken
    - stem/role/archetype → htmlAttr(구조값 재검증 통과, 심층방어)
  iframe src: §10.3 에서는 산출하지 않았다(도메인 배선점). §19 부터 `frameSrc` props 가
  게이트(common/frameSrc.ftl)를 통과할 때만 같은 출처 절대경로로 산출한다 — 없으면 종전과 동일.
  스크립트릿 0 / JWORKS 배너 0 / jQuery 3.7.1(header.jsp) / 외부 CDN 0.
-->
<#import "/common/frameSrc.ftl" as frameLib>
<#macro paneFrames slotKey side>
<#if (slots[slotKey])?? && (slots[slotKey]?size > 0)>
<#list slots[slotKey] as frame>
<#-- §10.4: 패인은 iframe 이므로 LAYOUT_FRAME 만 렌더한다. 다른 모듈이 (구 정의 등으로) 남아 있으면
     빈 iframe 을 만들지 않고 건너뛴다 — 예전에는 무엇이든 iframe 으로 찍어, 놓은 모듈은 사라지고
     빈 프레임만 남는 결과를 조용히 만들었다. -->
<#if (frame.moduleTypeCode)! != "LAYOUT_FRAME">
        <!-- 패인에 놓을 수 없는 모듈이라 건너뜀: ${htmlText((frame.moduleTypeCode)!"")} (§10.4) -->
<#else>
<#assign fprops = frame.props!{}>
<#assign fidRaw = (fprops["frameId"])!"">
<#if (fidRaw?length > 0)>
<#assign fid = htmlAttr(fidRaw)>
<#else>
<#assign fid = "dual-" + side + "-frame-" + frame?index>
</#if>
<#assign paneCls = cssToken((fprops["paneClass"])!"")>
        <#-- §19: frameSrc 가 게이트를 통과할 때만 src 를 산출한다. 없으면 종전대로 미지정
             (도메인 배선점 — §10.3). 게이트는 common/frameSrc.ftl 단일 소스. -->
<#assign fsrc = frameLib.safeFrameSrc(fprops)>
        <iframe title="${htmlAttr((fprops["title"])!"")}" id="${fid}" class="dual-frame<#if (paneCls?length > 0)> ${paneCls}</#if>"<#if (fsrc?length > 0)> src="${r"${ctx}"}${htmlAttr(fsrc)}"</#if> data-module="${htmlAttr(frame.moduleTypeCode!"")}"<#if (frame["data"])?? || ((frame["events"])?? && frame["events"]?is_sequence && frame["events"]?size gt 0)> data-frg-instance-id="${htmlAttr(frame.instanceId!"")}" data-frg-module-type="${htmlAttr(frame.moduleTypeCode!"")}"</#if>></iframe>
</#if>
</#list>
</#if>
</#macro>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${r"${pageContext.request.contextPath}"}" />
<!DOCTYPE html>
<html lang="ko">
<head>
<%-- 번들 런타임 매니페스트(jQuery 3.7.1 + jworks 6종 + commonSection/commonPopup/commonList*). 로컬 참조만. --%>
<jsp:include page="../common/header.jsp" />
<#-- P6-2: 듀얼 레이아웃 CSS는 공통추출(commonScreenLayout.css, header.jsp 매니페스트가 링크) → per-screen link 불필요. -->
</head>
<body class="dual-layout">
<%-- 좌우 2단 iframe 호스트. 리사이저는 commonSection.js가 자동 바인딩(#dual-layout-area .layout-middle.resizer). --%>
<div id="dual-layout-area" class="dual-layout-area" data-stem="${htmlAttr(stem)}" data-archetype="${htmlAttr(archetype)}" data-role="${htmlAttr(role)}">
    <div class="layout-left">
<@paneFrames slotKey="leftArea" side="left" />
    </div>
    <div class="layout-middle resizer">
        <div class="resizer-bar"></div>
        <button type="button" class="collapse-left" aria-label="좌측 접기"></button>
        <button type="button" class="collapse-right" aria-label="우측 접기"></button>
        <button type="button" class="expand" aria-label="펼치기"></button>
    </div>
    <div class="layout-right">
<@paneFrames slotKey="rightArea" side="right" />
    </div>
</div>
<script defer src="${r"${ctx}"}/js/${htmlAttr(role)}/${stem}/${stem}.js"></script>
<#if (hasDesignMetadata)!false>
<script defer src="${r"${ctx}"}/js/${htmlAttr(role)}/${stem}/${stem}Design.js"></script>
</#if>
</body>
</html>
