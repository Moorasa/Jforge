<#-- P5-4: MGMT_LIST_DETAIL+FORM_VIEW 뷰. MagicIAM commonListFormView.js 1:1 배선. -->
<#--
  아티팩트: ListFormView (JSP) → {stem}ListFormView.jsp (계약 §8.2)
  역할: FORM_VIEW props를 정적 폼 골격으로 산출. 번들 commonListFormView.js가
        section#form-view / #select-all·.row-checkbox(42행) / .empty-case(76행)를 타겟.
  ⚠ commonListFormView.js render(data)는 비어 있다(127~128행) — 폼 본문 마크업을 런타임이
     채우지 않으므로, 입력 필드(label/input)는 이 JSP가 정적으로 그린다(fields props 기반).
     팝업 모드는 런타임 message 수신(SET_EMPTY_MODE/mode==='popup', 72~81행).
  🔒 자유문자열 전량 GenEscaper 문맥별 경유(계약 §8.4 Form 문맥표) — 필드가 반복되므로 각 속성마다 정확히:
    - selectionType              → htmlAttr(data-selection-type 속성)
    - field.label                → htmlText(<label> 텍스트 노드)
    - field.name                 → htmlAttr(name/for/id·data-name 속성)
    - field.type                 → 허용목록 리터럴 매핑(<#switch>)으로만 type 속성에 삽입
                                   (원문 직접삽입 0 — 미허용 type은 기본값 text). 속성탈출 차단.
    - field.styleClass/formStyleClass → cssToken(class 토큰 화이트리스트, 위반 드롭)
    - field.requiredYn           → boolean(required 속성 유무만)
  URL/href/src 직접수신 props 없음(§8.4) — name/type은 데이터 바인딩/입력유형이지 URL 아님.
  스크립트릿 0 / 배너 0.
-->
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<#assign fvInst = slots["listArea"][0]>
<#assign props = fvInst.props>
<#assign selectionType = (props["selectionType"])!"checkbox">
<#assign fields = (props["fields"])![]>
<#assign formStyleClass = cssToken((props["formStyleClass"])!"")>
<section id="form-view"<#if (formStyleClass?length > 0)> class="${formStyleClass}"</#if>
    data-selection-type="${htmlAttr(selectionType)}"<#if (fvInst["data"])?? || ((fvInst["events"])?? && fvInst["events"]?is_sequence && fvInst["events"]?size gt 0)> data-frg-instance-id="${htmlAttr(fvInst.instanceId!"")}" data-frg-module-type="${htmlAttr(fvInst.moduleTypeCode!"")}"</#if>>
    <div class="layout-column">
        <div class="layout-header">
            <div class="layout-left">
            <#if selectionType == "checkbox">
                <label class="select-all-label">
                    <input type="checkbox" id="select-all" class="select-all" />
                    <span>전체 선택</span>
                </label>
            </#if>
            </div>
        </div>
        <div class="layout-body">
            <form class="form-view-form" onsubmit="return false;">
            <#list fields as field>
                <#assign fName = htmlAttr(field["name"]!"")>
                <#assign fLabel = htmlText(field["label"]!"")>
                <#assign fStyle = cssToken(field["styleClass"]!"")>
                <#assign fRequired = (field["requiredYn"]!false)>
                <#-- 🔒 input type 화이트리스트 리터럴 매핑: 원문을 type 속성에 직접 넣지 않는다.
                     미허용 값은 기본 text로 수렴(속성탈출·미지원 위젯 차단). -->
                <#assign rawType = (field["type"]!"text")>
                <#assign inputType = "text">
                <#switch rawType>
                    <#case "number"><#assign inputType = "number"><#break>
                    <#case "date"><#assign inputType = "date"><#break>
                    <#case "email"><#assign inputType = "email"><#break>
                    <#case "tel"><#assign inputType = "tel"><#break>
                    <#case "password"><#assign inputType = "password"><#break>
                    <#case "checkbox"><#assign inputType = "checkbox"><#break>
                    <#case "radio"><#assign inputType = "radio"><#break>
                    <#case "select"><#assign inputType = "select"><#break>
                    <#case "textarea"><#assign inputType = "textarea"><#break>
                    <#default><#assign inputType = "text">
                </#switch>
                <div class="form-field row-checkbox-scope"<#if (fStyle?length > 0)> data-field-class="${fStyle}"</#if> data-name="${fName}">
                    <label class="form-field-label" for="ff-${fName}">${fLabel}<#if fRequired> <span class="required-mark">*</span></#if></label>
                <#if inputType == "select">
                    <select id="ff-${fName}" name="${fName}" class="form-field-input<#if (fStyle?length > 0)> ${fStyle}</#if>"<#if fRequired> required</#if>></select>
                <#elseif inputType == "textarea">
                    <textarea id="ff-${fName}" name="${fName}" class="form-field-input<#if (fStyle?length > 0)> ${fStyle}</#if>"<#if fRequired> required</#if>></textarea>
                <#else>
                    <input type="${inputType}" id="ff-${fName}" name="${fName}" class="form-field-input<#if (fStyle?length > 0)> ${fStyle}</#if>"<#if fRequired> required</#if> />
                </#if>
                </div>
            </#list>
            </form>
            <#-- .empty-case: 데이터 없음/팝업 빈 모드 대상(commonListFormView.js 76행). -->
            <div class="empty-case" style="display:none;"></div>
        </div>
    </div>
</section>
