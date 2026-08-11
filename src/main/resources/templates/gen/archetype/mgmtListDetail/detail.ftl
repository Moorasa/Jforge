<#-- P5.5a/b: MGMT_LIST_DETAIL 상세영역. JWorks commonSection.js 1:1 배선(계약 §9). -->
<#--
  아티팩트: Detail (JSP) → {stem}Detail.jsp (계약 §9.2)
  역할: 상세영역 정적 골격 산출. 번들 commonSection.js(JWorks_JSCommonSection)가 타겟:
    - detailToolbar(TOOLBAR)  → section.detail-toolbar 액션바(선택, list.ftl listToolbar 동형)
    - detailBasic(DETAIL_BASIC) → section#basic-info(보기/수정 2모드·접기·속성칩, registEventBasicInfo 88행)
    - detailTabs(ASSOCIATE_TABS) → section#associate-info(.tabs .tab + .contents iframe.{tabClass},
      registEventAssociateInfo 247행). 탭 iframe src=tab.location은 도메인 채움 TODO(§9 (B)).
  ⚠ 값 바인딩(보기값·폼값·탭 iframe 로드)은 런타임/도메인 콜백 소관 — JSP는 정적 셸만.
  🔒 자유문자열 전량 GenEscaper 문맥별 경유(계약 §9.3):
    - basic field.label → htmlText / field.name → htmlAttr / field.type → 허용목록 리터럴(<#switch>)
      / field.styleClass·basicStyleClass → cssToken / field.requiredYn → boolean
    - tab.label → htmlText / tab.tabClass → cssToken / tab.frameId → htmlAttr
    - toolbar btn.label → htmlText / btn.styleClass → cssToken / btn.actionCode → htmlAttr
  URL 직접수신 props 없음(§9.3): tab.location은 props 아님(TODO 배선점). 스크립트릿 0 / 배너 0.
-->
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<section id="${stem}-detail" class="detail-area">

<#-- detailToolbar(TOOLBAR): 존재 시에만 조건부 렌더(list.ftl listToolbar 동형). -->
<#if (slots["detailToolbar"])?? && (slots["detailToolbar"]?size > 0)>
    <#assign dtInst = slots["detailToolbar"][0]>
    <#assign dtProps = dtInst.props>
    <#if (dtProps["buttons"])??>
    <section class="detail-toolbar"<#if (dtInst["data"])?? || ((dtInst["events"])?? && dtInst["events"]?is_sequence && dtInst["events"]?size gt 0)> data-frg-instance-id="${htmlAttr(dtInst.instanceId!"")}" data-frg-module-type="${htmlAttr(dtInst.moduleTypeCode!"")}"</#if>>
        <#list dtProps["buttons"] as btn>
            <#assign dbtnClass = cssToken("btn " + (btn["styleClass"]!""))>
        <button type="button"<#if (dbtnClass?length > 0)> class="${dbtnClass}"</#if> data-action="${htmlAttr(btn["actionCode"]!"")}">${htmlText(btn["label"]!"")}</button>
        </#list>
    </section>
    </#if>
</#if>

<#-- detailBasic(DETAIL_BASIC): 기본정보 섹션. registEventBasicInfo(commonSection.js 88행) 배선.
     보기모드(.detail-info-view) 라벨/값 + 수정모드(.detail-info-edit) 라벨/입력 + 버튼(.buttons/.buttons-edit). -->
<#if (slots["detailBasic"])?? && (slots["detailBasic"]?size > 0)>
    <#assign biInst = slots["detailBasic"][0]>
    <#assign bprops = biInst.props>
    <#assign biFields = (bprops["fields"])![]>
    <#assign biStyle = cssToken((bprops["basicStyleClass"])!"")>
    <#assign editableYn = (bprops["editableYn"])!true>
    <#assign attributeYn = (bprops["attributeYn"])!false>
    <section id="basic-info" class="view-mode<#if (biStyle?length > 0)> ${biStyle}</#if>"<#if (biInst["data"])?? || ((biInst["events"])?? && biInst["events"]?is_sequence && biInst["events"]?size gt 0)> data-frg-instance-id="${htmlAttr(biInst.instanceId!"")}" data-frg-module-type="${htmlAttr(biInst.moduleTypeCode!"")}"</#if>>
        <div class="detail-header">
            <button type="button" class="button-detail-collapse" aria-label="상세 접기"></button>
            <button type="button" class="button-detail-expand" aria-label="상세 펼치기"></button>
        </div>
        <div class="detail-info-view">
            <div class="layout-column">
            <#list biFields as f>
                <div class="detail-field" data-name="${htmlAttr(f["name"]!"")}">
                    <span class="label">${htmlText(f["label"]!"")}</span>
                    <span class="value">-</span>
                </div>
            </#list>
            </div>
        <#if attributeYn>
            <div class="attribute-area">
                <span class="label">속성</span>
                <div class="attribute-chip-container"><span>-</span></div>
            </div>
        </#if>
        </div>
    <#if editableYn>
        <div class="detail-info-edit">
            <div class="layout-column">
            <#list biFields as f>
                <#assign fName = htmlAttr(f["name"]!"")>
                <#assign fLabel = htmlText(f["label"]!"")>
                <#assign fStyle = cssToken(f["styleClass"]!"")>
                <#assign fRequired = (f["requiredYn"]!false)>
                <#-- 🔒 input type 허용목록 리터럴 매핑(formView.ftl 동형): 원문 직접삽입 0, 미허용→text. -->
                <#assign inputType = "text">
                <#switch (f["type"]!"text")>
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
                <div class="edit-field" data-name="${fName}">
                    <label class="clearable" for="bi-${fName}">${fLabel}<#if fRequired> <span class="required-mark">*</span></#if></label>
                <#if inputType == "select">
                    <select id="bi-${fName}" name="${fName}" class="detail-input<#if (fStyle?length > 0)> ${fStyle}</#if>"<#if fRequired> required</#if>></select>
                <#elseif inputType == "textarea">
                    <textarea id="bi-${fName}" name="${fName}" class="detail-input<#if (fStyle?length > 0)> ${fStyle}</#if>"<#if fRequired> required</#if>></textarea>
                <#else>
                    <input type="${inputType}" id="bi-${fName}" name="${fName}" class="detail-input<#if (fStyle?length > 0)> ${fStyle}</#if>"<#if fRequired> required</#if> />
                </#if>
                </div>
            </#list>
            </div>
        </div>
        <div class="buttons">
            <button type="button" class="update">수정</button>
            <button type="button" class="delete">삭제</button>
        </div>
        <div class="buttons-edit">
            <button type="button" class="save">저장</button>
            <button type="button" class="cancel">취소</button>
        </div>
    </#if>
    </section>
</#if>

<#-- detailTabs(ASSOCIATE_TABS): 연관정보 탭. registEventAssociateInfo(commonSection.js 247행) 배선.
     각 인스턴스의 props.tabs[]를 하나의 section#associate-info에 모아 렌더(단일 연관섹션·다중 탭).
     iframe src(tab.location)은 도메인 채움 TODO 배선점 → src 미삽입(§9 (B)). 첫 탭은 on. -->
<#if (slots["detailTabs"])?? && (slots["detailTabs"]?size > 0)>
    <#assign firstTabsInst = slots["detailTabs"][0]>
    <section id="associate-info" class="associate-info with-tab"<#if (firstTabsInst["data"])?? || ((firstTabsInst["events"])?? && firstTabsInst["events"]?is_sequence && firstTabsInst["events"]?size gt 0)> data-frg-instance-id="${htmlAttr(firstTabsInst.instanceId!"")}" data-frg-module-type="${htmlAttr(firstTabsInst.moduleTypeCode!"")}"</#if>>
        <div class="tabs">
        <#assign tabIdx = 0>
        <#list slots["detailTabs"] as tabsInst>
            <#list (tabsInst.props["tabs"])![] as tab>
                <#assign tabCls = cssToken(tab["tabClass"]!"")>
            <div class="tab<#if (tabCls?length > 0)> ${tabCls}</#if><#if tabIdx == 0> on</#if>">${htmlText(tab["label"]!"")}</div>
                <#assign tabIdx = tabIdx + 1>
            </#list>
        </#list>
        </div>
        <div class="contents">
        <#assign frameIdx = 0>
        <#list slots["detailTabs"] as tabsInst>
            <#list (tabsInst.props["tabs"])![] as tab>
                <#assign tabCls = cssToken(tab["tabClass"]!"")>
            <#-- iframe src(tab.location)은 도메인 채움 TODO 배선점(§9 (B)) — 산출 시 src 미지정. -->
            <iframe title="${htmlText(tab["label"]!"")}" id="${htmlAttr(tab["frameId"]!"")}" class="associate-frame<#if (tabCls?length > 0)> ${tabCls}</#if><#if frameIdx == 0> on</#if>"></iframe>
                <#assign frameIdx = frameIdx + 1>
            </#list>
        </#list>
        </div>
    </section>
</#if>

</section>
