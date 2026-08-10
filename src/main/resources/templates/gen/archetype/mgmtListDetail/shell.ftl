<#-- P4 MVP: MGMT_LIST_DETAIL+TABLE_VIEW 1뷰. 다른 뷰/모듈은 P5. -->
<#--
  아티팩트: shell → {stem}.jsp (계약 §1.1 #1)
  역할: layout + common/header.jsp 매니페스트 참조 + List 컨테이너/iframe 배선.
  🔒 규약: 스크립트릿 0 / JWORKS 배너 0 / jQuery 3.7.1(header.jsp 매니페스트) / 외부 CDN 0.
  자유문자열(props값)은 shell 단계에서 삽입하지 않는다. stem/role/archetype은 TemplateContextBuilder에서
  화이트리스트(§1.1/§5.1) 재검증된 구조값이나, 계약 §3.3 원문삽입금지 원칙에 따라 HTML 속성 삽입은
  htmlAttr()를 경유한다(벨트앤서스펜더스 심층방어).
-->
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${r"${pageContext.request.contextPath}"}" />
<!DOCTYPE html>
<html lang="ko">
<head>
<%-- 번들 런타임 매니페스트(jQuery 3.7.1 + jworks 6종 + commonList* + js-singleton/js-constants). 로컬 참조만. --%>
<jsp:include page="../common/header.jsp" />
<#-- 🔒 자산 URL은 파이프라인 쓰기경로 {cssBasePath}/{role}/{stem}/ 와 반드시 일치해야 한다(계약 §1.2).
     정적 리터럴 admin을 넣지 않는다 — role 세그먼트가 그 역할을 하므로 중복 시 서빙 404. -->
<link rel="stylesheet" href="${r"${ctx}"}/css/${htmlAttr(role)}/${stem}/${stem}List.css" />
</head>
<body>
<#-- hasDetail(계약 §9.2, ScreenGenerator 단일소스)면 상세영역 {stem}Detail.jsp를 조건부 배선한다.
     detail 슬롯 미배치면 아래 조건부 블록은 whitespace_stripping으로 0바이트 → 기존 골든 바이트 무손상.
     ※ 이 주석은 FTL 주석이라 산출물에 남지 않는다(JSP 주석은 verbatim 출력이므로 골든 보존 위해 원문 유지). -->
<%-- shell → List → List{View} 조립 컨테이너. List는 iframe 없이 동일 문서 내 include 배선(MVP). --%>
<div id="${stem}-shell" class="page-shell<#if (hasDetail)!false> with-detail</#if>" data-stem="${htmlAttr(stem)}" data-archetype="${htmlAttr(archetype)}" data-role="${htmlAttr(role)}">
    <jsp:include page="./${stem}List.jsp" />
<#if (hasDetail)!false>
    <jsp:include page="./${stem}Detail.jsp" />
</#if>
</div>
<script defer src="${r"${ctx}"}/js/${htmlAttr(role)}/${stem}/${stem}List.js"></script>
<#if (hasDesignMetadata)!false>
<script defer src="${r"${ctx}"}/js/${htmlAttr(role)}/${stem}/${stem}Design.js"></script>
</#if>
</body>
</html>
