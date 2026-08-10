<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%-- J-FORGE 공통 셸 헤더 파셜 (P7-1). 사용:
     <jsp:include page="../common/shell-top.jsp"><jsp:param name="active" value="studio"/></jsp:include>
     active ∈ home | projects | catalog | studio (그 외/누락 시 활성 표시 없음).
     스크립트릿 없음 — EL 조건식으로만 활성 클래스 분기. --%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<header class="frg-shell-header">
    <a class="frg-brand" href="${ctx}/admin">J-FORGE<span class="frg-brand-sub">SCREEN BUILDER</span></a>
    <nav class="frg-menu" aria-label="주 메뉴">
        <a href="${ctx}/admin" class="${param.active == 'home' ? 'is-active' : ''}">대시보드</a>
        <a href="${ctx}/admin/projects" class="${param.active == 'projects' ? 'is-active' : ''}">프로젝트</a>
        <a href="${ctx}/admin/catalog" class="${param.active == 'catalog' ? 'is-active' : ''}">모듈 카탈로그</a>
        <a href="${ctx}/admin/studio" class="${param.active == 'studio' ? 'is-active' : ''}">스튜디오</a>
    </nav>
</header>
