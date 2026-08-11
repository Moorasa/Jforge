<#-- P5-2: MGMT_LIST_DETAIL+CARD_VIEW 뷰. JWorks commonListCardView.js 1:1 배선. -->
<#--
  아티팩트: ListCardView (JSP) → {stem}ListCardView.jsp (계약 §8.2)
  역할: CARD_VIEW props를 정적 카드 골격으로 산출. 번들 commonListCardView.js가
        section#card-view / section.total .count(260행) / section.search .input·.search-icon(55~56행) /
        section.category select(92행) / .layout-body(262행)를 타겟(런타임 render/renderCallback가 카드 채움).
  ⚠ 개별 카드 마크업은 apiInfo.renderCallback이 채운다(commonListCardView.js 243행). JSP는 정적 셸만.
  🔒 자유문자열 전량 GenEscaper 경유(계약 §8.4 Card 문맥표):
    - selectMode                 → htmlAttr(data-select-mode 속성)
    - titleField/subtitleField/imageField → htmlAttr(data-* 배선 힌트 속성)
    - col.displayName            → htmlText(라벨 텍스트)
    - col.name                   → htmlAttr(data-name 속성 / data-item 속성)
    - cardStyleClass             → cssToken(class 토큰 화이트리스트, 위반 드롭)
  imageField는 URL이 아니라 데이터 필드명(런타임이 경로 조립) — URL 직접수신 props 없음(§8.4).
  스크립트릿 0 / 배너 0.
-->
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<#assign cvInst = slots["listArea"][0]>
<#assign props = cvInst.props>
<#assign selectMode = (props["selectMode"])!"none">
<#assign pagingYn = (props["pagingYn"])!false>
<#assign categoryYn = (props["categoryYn"])!false>
<#assign titleField = (props["titleField"])!"">
<#assign subtitleField = (props["subtitleField"])!"">
<#assign imageField = (props["imageField"])!"">
<#assign cardStyleClass = cssToken((props["cardStyleClass"])!"")>
<section id="card-view"<#if (cardStyleClass?length > 0)> class="${cardStyleClass}"</#if>
    data-select-mode="${htmlAttr(selectMode)}"
    data-title-field="${htmlAttr(titleField)}"
    data-subtitle-field="${htmlAttr(subtitleField)}"
    data-image-field="${htmlAttr(imageField)}"<#if (cvInst["data"])?? || ((cvInst["events"])?? && cvInst["events"]?is_sequence && cvInst["events"]?size gt 0)> data-frg-instance-id="${htmlAttr(cvInst.instanceId!"")}" data-frg-module-type="${htmlAttr(cvInst.moduleTypeCode!"")}"</#if>>
    <div class="layout-column">
        <div class="layout-header">
            <div class="layout-left">
                <section class="total"><span class="count">0</span></section>
            <#if categoryYn>
                <section class="category">
                    <select aria-label="카테고리"></select>
                </section>
            </#if>
                <section class="search">
                    <div class="input" contenteditable="true" data-placeholder="검색어를 입력하세요"></div>
                    <div class="search-icon"></div>
                </section>
            </div>
        </div>
        <div class="layout-body">
        <#if selectMode == "checkbox">
            <input type="checkbox" id="select-all" class="select-all" />
        </#if>
        <#-- 개별 카드 본문은 런타임 apiInfo.renderCallback이 .layout-body에 채운다(commonListCardView.js 243·262행). -->
        </div>
    <#if pagingYn>
        <div class="layout-footer">
            <div class="pagination" id="pagination"></div>
        </div>
    </#if>
    </div>
</section>
