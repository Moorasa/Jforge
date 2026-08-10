<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${r"${pageContext.request.contextPath}"}" />
<#list slots["widgetArea"]![] as inst>
  <#switch inst.moduleTypeCode>
    <#case "BAR_CHART"><link rel="stylesheet" href="${r"${ctx}"}/css/${htmlAttr(role)}/${stem}/${stem}BarChart.css" /><script defer src="${r"${ctx}"}/js/${htmlAttr(role)}/${stem}/${stem}BarChart.js"></script><#break>
    <#case "SEMICIRCLE_CHART"><link rel="stylesheet" href="${r"${ctx}"}/css/${htmlAttr(role)}/${stem}/${stem}SemicircleChart.css" /><script defer src="${r"${ctx}"}/js/${htmlAttr(role)}/${stem}/${stem}SemicircleChart.js"></script><#break>
    <#case "EMPTY_STATE"><link rel="stylesheet" href="${r"${ctx}"}/css/${htmlAttr(role)}/${stem}/${stem}EmptyState.css" /><script defer src="${r"${ctx}"}/js/${htmlAttr(role)}/${stem}/${stem}EmptyState.js"></script><#break>
    <#case "CHAT_WIDGET"><link rel="stylesheet" href="${r"${ctx}"}/css/${htmlAttr(role)}/${stem}/${stem}ChatWidget.css" /><script defer src="${r"${ctx}"}/js/${htmlAttr(role)}/${stem}/${stem}ChatWidget.js"></script><#break>
  </#switch>
</#list>
<#if (hasDesignMetadata)!false>
<script defer src="${r"${ctx}"}/js/${htmlAttr(role)}/${stem}/${stem}Design.js"></script>
</#if>
<main id="${stem}-dashboard" class="dashboard-grid">
<#list slots["widgetArea"]![] as inst>
  <#switch inst.moduleTypeCode>
    <#case "BAR_CHART"><jsp:include page="./${stem}BarChart.jsp" /><#break>
    <#case "SEMICIRCLE_CHART"><jsp:include page="./${stem}SemicircleChart.jsp" /><#break>
    <#case "EMPTY_STATE"><jsp:include page="./${stem}EmptyState.jsp" /><#break>
    <#case "CHAT_WIDGET"><jsp:include page="./${stem}ChatWidget.jsp" /><#break>
  </#switch>
</#list>
</main>
