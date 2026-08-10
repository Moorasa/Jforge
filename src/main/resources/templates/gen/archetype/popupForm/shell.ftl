<#assign inst = slots["popupBody"][0]>
<#assign props = inst.props>
<#assign popupTitle = htmlText((props["popupTitle"])!"정보 입력")>
<#assign bodyTitle = htmlText((props["bodyTitle"])!"")>
<#assign confirmText = htmlText((props["confirmText"])!"확인")>
<#assign cancelYn = (props["cancelYn"])!true>
<#assign fields = (props["fields"])![]>
<#assign sizeRaw = (props["size"])!"medium">
<#assign size = "medium"><#if sizeRaw == "small"><#assign size="small"><#elseif sizeRaw == "large"><#assign size="large"></#if>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${r"${pageContext.request.contextPath}"}" />
<section id="${stem}-popup" class="overlay-popup popup-size-${size}"<#if (inst["data"])?? || ((inst["events"])?? && inst["events"]?is_sequence && inst["events"]?size gt 0)> data-frg-instance-id="${htmlAttr(inst.instanceId!"")}" data-frg-module-type="${htmlAttr(inst.moduleTypeCode!"")}"</#if>>
    <link rel="stylesheet" href="${r"${ctx}"}/css/${htmlAttr(role)}/${stem}/${stem}.css" />
    <script defer src="${r"${ctx}"}/js/${htmlAttr(role)}/${stem}/${stem}.js"></script>
<#if (hasDesignMetadata)!false>
    <script defer src="${r"${ctx}"}/js/${htmlAttr(role)}/${stem}/${stem}Design.js"></script>
</#if>
    <div class="overlay-container">
        <div class="box">
            <div class="layout-column">
                <div class="layout-header">
                    <span class="font-heading-08">${popupTitle}</span>
                    <button type="button" class="close" aria-label="닫기">×</button>
                </div>
                <div class="layout-body">
                <#if bodyTitle?length gt 0><p class="title font-heading-06">${bodyTitle}</p></#if>
                    <form class="popup-form" onsubmit="return false;">
                    <#list fields as field>
                        <#assign name = htmlAttr(field["name"]!"")>
                        <#assign label = htmlText(field["label"]!"")>
                        <#assign rawType = field["type"]!"text"><#assign type="text">
                        <#if rawType == "number" || rawType == "date" || rawType == "email"><#assign type=rawType></#if>
                        <div class="popup-field" data-name="${name}">
                            <label for="pf-${name}">${label}<#if field["requiredYn"]!false><span class="required-mark"> *</span></#if></label>
                        <#if rawType == "textarea">
                            <textarea id="pf-${name}" name="${name}"<#if field["requiredYn"]!false> required</#if>></textarea>
                        <#elseif rawType == "select">
                            <select id="pf-${name}" name="${name}"<#if field["requiredYn"]!false> required</#if>></select>
                        <#else>
                            <input type="${type}" id="pf-${name}" name="${name}"<#if field["requiredYn"]!false> required</#if> />
                        </#if>
                        </div>
                    </#list>
                    </form>
                </div>
                <div class="layout-footer">
                    <div class="buttons">
                    <#if cancelYn><button type="button" class="cancel">취소</button></#if>
                        <button type="button" class="ok">${confirmText}</button>
                    </div>
                </div>
            </div>
        </div>
    </div>
</section>
