<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<section id="table-view" class="table-view" data-select-mode="checkbox">
    <div class="layout-body">
        <table>
            <colgroup>
                <col class="col-select" />
                <col />
            </colgroup>
            <thead>
                <tr>
                    <td class="col-select"><input type="checkbox" id="select-all" /></td>
                    <td data-name="c&quot;d">
                        <div>
                            <span>&lt;img onerror=1&gt;</span>
                            <div class="sort-icon">
                                <img class="sort-asc" alt="오름차순" src="/images/admin/icon-sort-asc-disable.png" />
                                <img class="sort-desc" alt="내림차순" src="/images/admin/icon-sort-desc-disable.png" />
                            </div>
                        </div>
                    </td>
                </tr>
            </thead>
            <tbody></tbody>
        </table>
    </div>
    <div class="pagination" id="pagination"></div>
</section>
