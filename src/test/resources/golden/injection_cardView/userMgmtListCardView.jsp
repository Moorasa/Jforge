<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<section id="card-view" class="card"
    data-select-mode="checkbox"
    data-title-field="t&quot;&gt;&lt;/section&gt;&lt;script&gt;alert(1)&lt;/script&gt;"
    data-subtitle-field="line sep &lt;/script&gt;&lt;script&gt;evil()&lt;/script&gt; &#36;{7*7}"
    data-image-field="i&quot; onerror=&quot;alert(1)">
    <div class="layout-column">
        <div class="layout-header">
            <div class="layout-left">
                <section class="total"><span class="count">0</span></section>
                <section class="search">
                    <div class="input" contenteditable="true" data-placeholder="검색어를 입력하세요"></div>
                    <div class="search-icon"></div>
                </section>
            </div>
        </div>
        <div class="layout-body">
            <input type="checkbox" id="select-all" class="select-all" />
        </div>
        <div class="layout-footer">
            <div class="pagination" id="pagination"></div>
        </div>
    </div>
</section>
