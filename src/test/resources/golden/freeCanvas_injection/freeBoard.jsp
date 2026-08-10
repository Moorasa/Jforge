<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<section id="freeBoard-canvas" class="frg-fc-screen">
    <link rel="stylesheet" href="${ctx}/css/admin/freeBoard/freeBoard.css" />
    <script defer src="${ctx}/js/admin/freeBoard/freeBoard.js"></script>
    <div class="frg-fc-sheet">
        <div class="frg-fc-item frg-fc-container frg-fc-1">
            <div class="frg-fc-panel frg-fc-panel-bordered">
                <span class="frg-fc-panel-title">&lt;script&gt;alert(1)&lt;/script&gt;</span>
            </div>
            <div class="frg-fc-panel-body">
                <div class="frg-fc-item frg-fc-2">
                    <button type="button" class="frg-fc-button frg-fc-button-primary">&quot;&gt;&lt;img src=x onerror=alert(1)&gt;</button>
                </div>
            </div>
        </div>
        <div class="frg-fc-item frg-fc-3">
            <span class="frg-fc-label frg-fc-label-normal">EL:&#36;{7*7} DEFERRED:&#35;{2+2} SEP:  END:&lt;/script&gt;</span>
        </div>
    </div>
</section>
