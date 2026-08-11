<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<section id="freeBoard-canvas" class="frg-fc-screen">
    <link rel="stylesheet" href="${ctx}/css/admin/freeBoard/freeBoard.css" />
    <script defer src="${ctx}/js/admin/freeBoard/freeBoard.js"></script>
    <div class="frg-fc-sheet">
        <div class="frg-fc-item frg-fc-1">
            <section id="table-view" class="table-view" data-select-mode="none">
                <div class="layout-body">
                    <table>
                        <colgroup>
                            <col />
                            <col />
                        </colgroup>
                        <thead>
                            <tr>
                                <td data-name="boardNm"><div><span>게시판명</span></div></td>
                                <td data-name="useYn"><div><span>사용여부</span></div></td>
                            </tr>
                        </thead>
                        <tbody></tbody>
                    </table>
                </div>
                <div class="pagination"></div>
            </section>
        </div>
        <div class="frg-fc-item frg-fc-2">
            <section id="form-view" class="form-view">
                <div class="layout-body">
                    <form class="frg-fc-form" onsubmit="return false;">
                        <div class="form-field" data-name="boardNm">
                            <label for="fc-2-boardNm">게시판명</label>
                            <input type="text" id="fc-2-boardNm" name="boardNm" />
                        </div>
                    </form>
                </div>
            </section>
        </div>
        <div class="frg-fc-item frg-fc-3">
            <button type="button" class="frg-fc-button frg-fc-button-primary">첫째</button>
        </div>
        <div class="frg-fc-item frg-fc-4">
            <button type="button" class="frg-fc-button frg-fc-button-primary">둘째</button>
        </div>
        <div class="frg-fc-item frg-fc-5">
            <button type="button" class="frg-fc-button frg-fc-button-primary">셋째</button>
        </div>
    </div>
</section>
