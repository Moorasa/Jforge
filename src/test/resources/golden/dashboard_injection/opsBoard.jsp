<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<link rel="stylesheet" href="${ctx}/css/admin/opsBoard/opsBoardBarChart.css" /><script defer src="${ctx}/js/admin/opsBoard/opsBoardBarChart.js"></script><link rel="stylesheet" href="${ctx}/css/admin/opsBoard/opsBoardEmptyState.css" /><script defer src="${ctx}/js/admin/opsBoard/opsBoardEmptyState.js"></script><main id="opsBoard-dashboard" class="dashboard-grid">
<jsp:include page="./opsBoardBarChart.jsp" /><jsp:include page="./opsBoardEmptyState.jsp" /></main>
