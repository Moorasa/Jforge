<#-- P4 MVP: MGMT_LIST_DETAIL+TABLE_VIEW 1뷰. 다른 뷰/모듈은 P5. -->
<#--
  아티팩트: List (JSP) → {stem}List.jsp (계약 §1.1 #2)
  역할: 뷰 전환 컨트롤러(commonList 참조) 컨테이너 + searchArea/listToolbar 조건부 렌더 + listArea 뷰 include.
  🔒 모든 자유문자열은 GenEscaper 경유:
    - filters[].label → htmlText, filters[].name → htmlAttr, filters[].options 파싱값 → htmlAttr/htmlText
    - buttons[].label → htmlText, buttons[].styleClass → cssToken, buttons[].actionCode → htmlAttr
  스크립트릿 0 / 배너 0.
-->
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<div id="${stem}-list" class="common-list" data-stem="${htmlAttr(stem)}">

<#-- searchArea(SEARCH_FILTER_BAR): 존재 시에만 조건부 렌더. 미배치 시 깨지지 않게 생략. -->
<#if (slots["searchArea"])?? && (slots["searchArea"]?size > 0)>
    <#assign searchInst = slots["searchArea"][0]>
    <#assign sprops = searchInst.props>
    <section class="search"<#if (searchInst["data"])?? || ((searchInst["events"])?? && searchInst["events"]?is_sequence && searchInst["events"]?size gt 0)> data-frg-instance-id="${htmlAttr(searchInst.instanceId!"")}" data-frg-module-type="${htmlAttr(searchInst.moduleTypeCode!"")}"</#if>>
        <div class="filter">
        <#if (sprops["filters"])??>
            <#list sprops["filters"] as f>
            <select name="${htmlAttr(f["name"]!"")}" data-filter-name="${htmlAttr(f["name"]!"")}" aria-label="${htmlAttr(f["label"]!"")}">
                <option value="">${htmlText(f["label"]!"")}</option>
                <#if (f["options"])?? && f["options"]?is_string && (f["options"]?length > 0)>
                    <#list f["options"]?split(",") as pair>
                        <#assign kv = pair?split(":")>
                        <#if (kv?size >= 2)>
                <option value="${htmlAttr(kv[0]?trim)}">${htmlText(kv[1]?trim)}</option>
                        </#if>
                    </#list>
                </#if>
            </select>
            </#list>
        </#if>
        </div>
        <#if (sprops["keywordYn"]!false)>
        <div class="input" contenteditable="true" data-role="search-keyword"></div>
        </#if>
        <#if (sprops["dateRangeYn"]!false)>
        <div class="filter-datepicker">
            <input type="date" class="datepicker-start" name="startDate" />
            <input type="date" class="datepicker-end" name="endDate" />
        </div>
        </#if>
    </section>
</#if>

<#-- listToolbar(TOOLBAR): 존재 시에만 조건부 렌더. -->
<#if (slots["listToolbar"])?? && (slots["listToolbar"]?size > 0)>
    <#assign toolbarInst = slots["listToolbar"][0]>
    <#assign tprops = toolbarInst.props>
    <#if (tprops["buttons"])??>
    <section class="list-toolbar"<#if (toolbarInst["data"])?? || ((toolbarInst["events"])?? && toolbarInst["events"]?is_sequence && toolbarInst["events"]?size gt 0)> data-frg-instance-id="${htmlAttr(toolbarInst.instanceId!"")}" data-frg-module-type="${htmlAttr(toolbarInst.moduleTypeCode!"")}"</#if>>
        <#list tprops["buttons"] as btn>
            <#assign btnClass = cssToken("btn " + (btn["styleClass"]!""))>
            <button type="button"<#if (btnClass?length > 0)> class="${btnClass}"</#if> data-action="${htmlAttr(btn["actionCode"]!"")}">${htmlText(btn["label"]!"")}</button>
        </#list>
    </section>
    </#if>
</#if>

<#-- listArea(단일 뷰, 필수): 뷰 세트 본문 include(뷰 전환 컨트롤러=commonList가 제어).
     🔒 파일명 접미사 listAreaViewSuffix는 GenArtifacts 화이트리스트 맵에서 파생된 정적값(계약 §8.1)
        — moduleTypeCode 원문 조립·평가 0. 미지원/미배치(null·빈값)면 본문 include를 생략(forward-compat). -->
<section class="list-area" id="list-area"<#if (slots["listArea"])?? && (slots["listArea"]?size > 0) && ((slots["listArea"][0]["data"])?? || ((slots["listArea"][0]["events"])?? && slots["listArea"][0]["events"]?is_sequence && slots["listArea"][0]["events"]?size gt 0))> data-frg-instance-id="${htmlAttr(slots["listArea"][0].instanceId!"")}" data-frg-module-type="${htmlAttr(slots["listArea"][0].moduleTypeCode!"")}"</#if>>
<#if (listAreaViewSuffix)?? && (listAreaViewSuffix?length > 0)>
    <jsp:include page="./${stem}${listAreaViewSuffix}.jsp" />
</#if>
</section>

</div>
