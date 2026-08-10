<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<div id="userMgmt-list" class="common-list" data-stem="userMgmt">

    <section class="search">
        <div class="filter">
            <select name="st&quot;onx" data-filter-name="st&quot;onx" aria-label="&lt;script&gt;alert(1)&lt;/script&gt;">
                <option value="">&lt;script&gt;alert(1)&lt;/script&gt;</option>
                <option value="A">&lt;b&gt;on&lt;/b&gt;</option>
                <option value="I">x</option>
            </select>
        </div>
        <div class="input" contenteditable="true" data-role="search-keyword"></div>
    </section>

    <section class="list-toolbar">
            <button type="button" class="btn" data-action="a&quot;b">&lt;/script&gt;&#36;{7*7}</button>
    </section>

<section class="list-area" id="list-area">
    <jsp:include page="./userMgmtListTableView.jsp" />
</section>

</div>
