<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html lang="ko">
<head>
    <jsp:include page="../common/header.jsp" />
    <link rel="stylesheet" href="${ctx}/css/admin/common/forge-theme.css?v=${assetVer}" />
    <link rel="stylesheet" href="${ctx}/css/admin/common/schemaForm.css" />
    <link rel="stylesheet" href="${ctx}/css/admin/catalog/catalogList.css" />
    <script defer src="${ctx}/js/admin/catalog/schemaFormRenderer.js"></script>
    <script defer src="${ctx}/js/admin/catalog/catalogList.js"></script>
</head>
<body class="frg-app">
    <jsp:include page="../common/shell-top.jsp"><jsp:param name="active" value="catalog" /></jsp:include>
    <nav class="frg-breadcrumb" aria-label="위치"><span>관리</span> &rsaquo; <span>모듈 카탈로그</span></nav>

    <main id="frg-content" class="frg-content frg-catalog">
        <section class="frg-section frg-catalog-list">
            <h2>모듈 카탈로그</h2>
            <p class="frg-desc">모듈을 선택하면 우측에 속성 스키마 폼이 렌더됩니다.</p>
            <ul id="frg-catalog-items" class="frg-catalog-items" role="listbox" aria-label="모듈 목록">
                <li class="frg-empty">로딩 중…</li>
            </ul>
        </section>

        <section class="frg-section frg-catalog-form">
            <h2>속성 폼 프리뷰</h2>
            <div id="frg-schema-slot" class="frg-schema-slot" aria-live="polite">
                <p class="frg-empty">왼쪽에서 모듈을 선택하세요.</p>
            </div>
        </section>
    </main>
</body>
</html>
