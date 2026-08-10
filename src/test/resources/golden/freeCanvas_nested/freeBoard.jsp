<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<section id="freeBoard-canvas" class="frg-fc-screen">
    <link rel="stylesheet" href="${ctx}/css/admin/freeBoard/freeBoard.css" />
    <script defer src="${ctx}/js/admin/freeBoard/freeBoard.js"></script>
    <div class="frg-fc-sheet">
        <div class="frg-fc-item frg-fc-container frg-fc-1">
            <div class="frg-fc-panel frg-fc-panel-bordered">
                <span class="frg-fc-panel-title">바깥 패널</span>
            </div>
            <div class="frg-fc-panel-body">
                <div class="frg-fc-item frg-fc-container frg-fc-2">
                    <div class="frg-fc-panel frg-fc-panel-bordered frg-fc-panel-filled">
                        <span class="frg-fc-panel-title">안쪽 패널</span>
                    </div>
                    <div class="frg-fc-panel-body">
                        <div class="frg-fc-item frg-fc-3">
                            <button type="button" class="frg-fc-button frg-fc-button-primary">깊은 버튼</button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <div class="frg-fc-item frg-fc-4">
            <button type="button" class="frg-fc-button frg-fc-button-primary">바깥 버튼</button>
        </div>
    </div>
</section>
