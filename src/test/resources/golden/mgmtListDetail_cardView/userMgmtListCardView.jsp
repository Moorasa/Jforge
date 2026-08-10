<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<section id="card-view" class="card-compact"
    data-select-mode="checkbox"
    data-title-field="userName"
    data-subtitle-field="userId"
    data-image-field="avatar">
    <div class="layout-column">
        <div class="layout-header">
            <div class="layout-left">
                <section class="total"><span class="count">0</span></section>
                <section class="category">
                    <select aria-label="카테고리"></select>
                </section>
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
