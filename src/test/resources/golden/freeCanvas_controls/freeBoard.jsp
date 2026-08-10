<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<section id="freeBoard-canvas" class="frg-fc-screen">
    <link rel="stylesheet" href="${ctx}/css/admin/freeBoard/freeBoard.css" />
    <script defer src="${ctx}/js/admin/freeBoard/freeBoard.js"></script>
    <div class="frg-fc-sheet">
        <div class="frg-fc-item frg-fc-1">
            <button type="button" class="frg-fc-button frg-fc-button-primary btn-primary">저장</button>
        </div>
        <div class="frg-fc-item frg-fc-2">
            <span class="frg-fc-label frg-fc-label-normal">게시판 관리</span>
        </div>
        <div class="frg-fc-item frg-fc-3">
            <div class="frg-fc-field">
                <input type="text" id="fc-3-" name="" placeholder="제목을 입력하세요" />
            </div>
        </div>
    </div>
</section>
