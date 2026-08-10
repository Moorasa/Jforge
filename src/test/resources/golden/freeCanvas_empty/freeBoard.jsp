<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<section id="freeBoard-canvas" class="frg-fc-screen">
    <link rel="stylesheet" href="${ctx}/css/admin/freeBoard/freeBoard.css" />
    <script defer src="${ctx}/js/admin/freeBoard/freeBoard.js"></script>
    <div class="frg-fc-sheet">
    </div>
</section>
