<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<section id="table-view" class="table-view" data-select-mode="checkbox">
    <div class="table-view-actions">
        <button type="button" class="btn-excel" data-action="excelDownload">엑셀</button>
    </div>
    <div class="layout-body">
        <table>
            <colgroup>
                <col class="col-select" />
                <col />
                <col />
                <col />
            </colgroup>
            <thead>
                <tr>
                    <td class="col-select"><input type="checkbox" id="select-all" /></td>
                    <td data-name="userId">
                        <div>
                            <span>사용자ID</span>
                            <div class="sort-icon">
                                <img class="sort-asc" alt="오름차순" src="/images/admin/icon-sort-asc-disable.png" />
                                <img class="sort-desc" alt="내림차순" src="/images/admin/icon-sort-desc-disable.png" />
                            </div>
                        </div>
                    </td>
                    <td data-name="userName">
                        <div>
                            <span>이름</span>
                            <div class="sort-icon">
                                <img class="sort-asc" alt="오름차순" src="/images/admin/icon-sort-asc-disable.png" />
                                <img class="sort-desc" alt="내림차순" src="/images/admin/icon-sort-desc-disable.png" />
                            </div>
                        </div>
                    </td>
                    <td data-name="regDtm">
                        <div>
                            <span>등록일시</span>
                        </div>
                    </td>
                </tr>
            </thead>
            <tbody></tbody>
        </table>
    </div>
    <div class="pagination" id="pagination"></div>
</section>
