<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<div id="userMgmt-list" class="common-list" data-stem="userMgmt">

    <section class="search">
        <div class="filter">
            <select name="x&quot;&gt;&lt;script&gt;alert(1)&lt;/script&gt;" data-filter-name="x&quot;&gt;&lt;script&gt;alert(1)&lt;/script&gt;" aria-label="&lt;img src=x onerror=alert(1)&gt;">
                <option value="">&lt;img src=x onerror=alert(1)&gt;</option>
                <option value="a">&lt;b&gt;</option>
                <option value="c">&#36;{7*7}</option>
            </select>
        </div>
        <div class="input" contenteditable="true" data-role="search-keyword"></div>
        <div class="filter-datepicker">
            <input type="date" class="datepicker-start" name="startDate" />
            <input type="date" class="datepicker-end" name="endDate" />
        </div>
    </section>

    <section class="list-toolbar">
            <button type="button" class="btn btn" data-action="add&quot; onclick=&quot;alert(1)">&lt;/script&gt;&lt;script&gt;evil()&lt;/script&gt;</button>
    </section>

<section class="list-area" id="list-area">
    <jsp:include page="./userMgmtListTableView.jsp" />
</section>

</div>
