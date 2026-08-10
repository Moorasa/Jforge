<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<div id="userMgmt-list" class="common-list" data-stem="userMgmt">

    <section class="search">
        <div class="filter">
            <select name="status" data-filter-name="status" aria-label="상태">
                <option value="">상태</option>
                <option value="A">활성</option>
                <option value="I">비활성</option>
            </select>
        </div>
        <div class="input" contenteditable="true" data-role="search-keyword"></div>
        <div class="filter-datepicker">
            <input type="date" class="datepicker-start" name="startDate" />
            <input type="date" class="datepicker-end" name="endDate" />
        </div>
    </section>

    <section class="list-toolbar">
            <button type="button" class="btn btn-primary" data-action="add">추가</button>
            <button type="button" class="btn btn-secondary" data-action="delete">삭제</button>
    </section>

<section class="list-area" id="list-area">
    <jsp:include page="./userMgmtListTableView.jsp" />
</section>

</div>
