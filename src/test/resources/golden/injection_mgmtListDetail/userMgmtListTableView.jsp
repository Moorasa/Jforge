<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<section id="table-view" class="table-view" data-select-mode="checkbox">
    <div class="table-view-actions">
        <button type="button" class="btn-excel" data-action="excelDownload">엑셀</button>
        <button type="button" class="btn-csv" data-action="csvUpload">CSV</button>
    </div>
    <div class="layout-body">
        <table>
            <colgroup>
                <col class="col-select" />
                <col />
            </colgroup>
            <thead>
                <tr>
                    <td class="col-select"><input type="checkbox" id="select-all" /></td>
                    <td data-name="u&quot;&gt;&lt;/td&gt;&lt;script&gt;x&lt;/script&gt;">
                        <div>
                            <span>line sep &lt;/script&gt;&lt;script&gt;alert(1)&lt;/script&gt; &#36;{9*9}</span>
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
