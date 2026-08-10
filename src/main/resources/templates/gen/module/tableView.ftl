<#-- P4 MVP: MGMT_LIST_DETAIL+TABLE_VIEW 1뷰. 다른 뷰/모듈은 P5. -->
<#--
  아티팩트: ListTableView (JSP) → {stem}ListTableView.jsp (계약 §1.1 #5)
  역할: TABLE_VIEW props를 정적 테이블 골격으로 산출. 번들 commonListTableView.js가
        section#table-view / .layout-body / table thead·tbody 를 타겟(런타임 render가 채움).
  🔒 자유문자열 전량 GenEscaper 경유:
    - col.displayName → htmlText(헤더 텍스트)
    - col.name       → htmlAttr(data-name 속성)
  displayYn=false 컬럼 스킵. selectMode(checkbox/radio) 선택 컬럼 반영. pagingYn/excelYn/csvYn 반영.
  스크립트릿 0 / 배너 0.
-->
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<#assign tvInst = slots["listArea"][0]>
<#assign props = tvInst.props>
<#assign columns = (props["columns"])![]>
<#assign selectMode = (props["selectMode"])!"none">
<#assign pagingYn = (props["pagingYn"])!false>
<#assign excelYn = (props["excelYn"])!false>
<#assign csvYn = (props["csvYn"])!false>
<section id="table-view" class="table-view" data-select-mode="${htmlAttr(selectMode)}"<#if (tvInst["data"])?? || ((tvInst["events"])?? && tvInst["events"]?is_sequence && tvInst["events"]?size gt 0)> data-frg-instance-id="${htmlAttr(tvInst.instanceId!"")}" data-frg-module-type="${htmlAttr(tvInst.moduleTypeCode!"")}"</#if>>
<#if excelYn || csvYn>
    <div class="table-view-actions">
    <#if excelYn>
        <button type="button" class="btn-excel" data-action="excelDownload">엑셀</button>
    </#if>
    <#if csvYn>
        <button type="button" class="btn-csv" data-action="csvUpload">CSV</button>
    </#if>
    </div>
</#if>
    <div class="layout-body">
        <table>
            <colgroup>
            <#if selectMode == "checkbox" || selectMode == "radio">
                <col class="col-select" />
            </#if>
            <#list columns as col>
                <#if (col["displayYn"]!true)>
                <col />
                </#if>
            </#list>
            </colgroup>
            <thead>
                <tr>
                <#if selectMode == "checkbox">
                    <td class="col-select"><input type="checkbox" id="select-all" /></td>
                <#elseif selectMode == "radio">
                    <td class="col-select"></td>
                </#if>
                <#list columns as col>
                    <#if (col["displayYn"]!true)>
                    <td data-name="${htmlAttr(col["name"]!"")}">
                        <div>
                            <span>${htmlText(col["displayName"]!"")}</span>
                            <#if (col["sortYn"]!false)>
                            <div class="sort-icon">
                                <img class="sort-asc" alt="오름차순" src="/images/admin/icon-sort-asc-disable.png" />
                                <img class="sort-desc" alt="내림차순" src="/images/admin/icon-sort-desc-disable.png" />
                            </div>
                            </#if>
                        </div>
                    </td>
                    </#if>
                </#list>
                </tr>
            </thead>
            <tbody></tbody>
        </table>
    </div>
<#if pagingYn>
    <div class="pagination" id="pagination"></div>
</#if>
</section>
