<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html lang="ko">
<head>
<%-- 번들 런타임 매니페스트(jQuery 3.7.1 + jworks 6종 + commonSection/commonPopup/commonList*). 로컬 참조만. --%>
<jsp:include page="../common/header.jsp" />
</head>
<body class="dual-layout">
<%-- 좌우 2단 iframe 호스트. 리사이저는 commonSection.js가 자동 바인딩(#dual-layout-area .layout-middle.resizer). --%>
<div id="dual-layout-area" class="dual-layout-area" data-stem="orgDual" data-archetype="DUAL_LAYOUT" data-role="admin">
    <div class="layout-left">
        <iframe title="조직 목록" id="leftListFrame" class="dual-frame pane-list" data-module="LAYOUT_FRAME"></iframe>
    </div>
    <div class="layout-middle resizer">
        <div class="resizer-bar"></div>
        <button type="button" class="collapse-left" aria-label="좌측 접기"></button>
        <button type="button" class="collapse-right" aria-label="우측 접기"></button>
        <button type="button" class="expand" aria-label="펼치기"></button>
    </div>
    <div class="layout-right">
        <iframe title="조직 상세" id="rightDetailFrame" class="dual-frame" data-module="LAYOUT_FRAME"></iframe>
    </div>
</div>
<script defer src="${ctx}/js/admin/orgDual/orgDual.js"></script>
</body>
</html>
