<#-- P5-3: MGMT_LIST_DETAIL+TREE_VIEW 뷰. JWorks commonListTreeView.js 1:1 배선. -->
<#--
  아티팩트: ListTreeView (JSP) → {stem}ListTreeView.jsp (계약 §8.2)
  역할: TREE_VIEW props를 정적 트리 골격으로 산출. 번들 commonListTreeView.js가
        section#tree-view / section.total .count(471행) / section.search .input(139행) /
        .layout-body(469행)를 타겟(런타임 render/parser/renderCallback가 계층 노드 채움).
  ⚠ 계층 노드(.tree-static-root/.tree-list/.tree-node/.tree-subtree)는 런타임이 채운다
     (commonListTreeView.js render 465행·createTreeHtmlString 509행·apiInfo.renderCallback 461행).
     JSP는 정적 셸만 — 계층 마크업 원문 삽입 0.
  🔒 자유문자열 전량 GenEscaper 경유(계약 §8.4 Tree 문맥표):
    - selectMode                         → htmlAttr(data-select-mode 속성)
    - labelField/idField/parentField/iconField → htmlAttr(data-* 배선 힌트 속성)
    - rootLabel                          → htmlText(루트 라벨 텍스트)
    - rootIconClass/treeStyleClass       → cssToken(class 토큰 화이트리스트, 위반 드롭)
  iconField는 URL이 아니라 데이터 필드명(런타임이 경로 조립), rootIconClass는 cssToken 검증
  클래스 토큰(commonListTreeView.js 485행 root.iconClass) — URL 직접수신 props 없음(§8.4).
  스크립트릿 0 / 배너 0.
-->
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<#assign tvInst = slots["listArea"][0]>
<#assign props = tvInst.props>
<#assign selectMode = (props["selectMode"])!"single">
<#assign labelField = (props["labelField"])!"">
<#assign idField = (props["idField"])!"">
<#assign parentField = (props["parentField"])!"">
<#assign iconField = (props["iconField"])!"">
<#assign rootLabel = (props["rootLabel"])!"">
<#assign searchYn = (props["searchYn"])!false>
<#assign orderingYn = (props["orderingYn"])!false>
<#assign rootIconClass = cssToken((props["rootIconClass"])!"")>
<#assign treeStyleClass = cssToken((props["treeStyleClass"])!"")>
<section id="tree-view"<#if (treeStyleClass?length > 0)> class="${treeStyleClass}"</#if>
    data-select-mode="${htmlAttr(selectMode)}"
    data-label-field="${htmlAttr(labelField)}"
    data-id-field="${htmlAttr(idField)}"
    data-parent-field="${htmlAttr(parentField)}"
    data-icon-field="${htmlAttr(iconField)}"
    data-ordering="<#if orderingYn>true<#else>false</#if>"<#if (tvInst["data"])?? || ((tvInst["events"])?? && tvInst["events"]?is_sequence && tvInst["events"]?size gt 0)> data-frg-instance-id="${htmlAttr(tvInst.instanceId!"")}" data-frg-module-type="${htmlAttr(tvInst.moduleTypeCode!"")}"</#if>>
    <div class="layout-column">
        <div class="layout-header">
            <div class="layout-left">
                <section class="total"><span class="count">0</span></section>
            <#if searchYn>
                <section class="search">
                    <div class="input" contenteditable="true" data-placeholder="검색어를 입력하세요"></div>
                    <div class="search-icon"></div>
                </section>
            </#if>
            </div>
        </div>
        <div class="layout-body" data-root-label="${htmlAttr(rootLabel)}"<#if (rootIconClass?length > 0)> data-root-icon-class="${htmlAttr(rootIconClass)}"</#if>>
        <#-- 계층 트리(.tree-static-root/.tree-list/.tree-node)는 런타임 render/parser/renderCallback이
             .layout-body에 채운다(commonListTreeView.js 465·494·509·461행). -->
        </div>
    </div>
</section>
