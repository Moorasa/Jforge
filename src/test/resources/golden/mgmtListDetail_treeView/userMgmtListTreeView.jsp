<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<section id="tree-view" class="tree-compact"
    data-select-mode="checkbox"
    data-label-field="orgName"
    data-id-field="orgNo"
    data-parent-field="parentNo"
    data-icon-field="orgType"
    data-ordering="true">
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
        <div class="layout-body" data-root-label="전체 조직" data-root-icon-class="icon-org-root">
        </div>
    </div>
</section>
