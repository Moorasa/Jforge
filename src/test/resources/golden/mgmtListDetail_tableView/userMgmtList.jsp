<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<div id="userMgmt-list" class="common-list" data-stem="userMgmt">

    <section class="search">
        <div class="filter">
            <select name="useYn" data-filter-name="useYn" aria-label="사용여부">
                <option value="">사용여부</option>
                <option value="Y">사용</option>
                <option value="N">미사용</option>
            </select>
        </div>
        <div class="input" contenteditable="true" data-role="search-keyword"></div>
    </section>

    <section class="list-toolbar">
            <button type="button" class="btn btn-primary" data-action="add">추가</button>
            <button type="button" class="btn btn-secondary" data-action="delete">삭제</button>
    </section>

<section class="list-area" id="list-area">
    <jsp:include page="./userMgmtListTableView.jsp" />
</section>

</div>
