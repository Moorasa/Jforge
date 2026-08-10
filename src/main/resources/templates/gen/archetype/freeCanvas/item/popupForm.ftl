<#-- POPUP_FORM — 캔버스 인라인 파셜(§17.4/§17.9).
     ★ 의미 변경 주의: POPUP 아키타입에서는 화면 전체를 덮는 오버레이(.overlay-popup)지만,
       캔버스에서는 **고정 좌표·크기를 가진 상자**다. 따라서 오버레이 배경을 씌우지 않고
       박스 본문만 인라인으로 찍는다(캔버스의 다른 부품을 가리지 않게).
     🔒 input type 은 허용목록 리터럴만. 자유문자열은 htmlText/htmlAttr. -->
<#assign fcPopupFields = (props["fields"])![]>
            <div class="frg-fc-popup">
                <div class="frg-fc-popup-head">
                    <span>${htmlText((props["popupTitle"])!"정보 입력")}</span>
                    <button type="button" class="close" aria-label="닫기">×</button>
                </div>
                <div class="frg-fc-popup-body">
                <#if ((props["bodyTitle"])!"")?length gt 0>
                    <p class="title">${htmlText(props["bodyTitle"])}</p>
                </#if>
                    <form class="popup-form" onsubmit="return false;">
                    <#list fcPopupFields as field>
                        <#assign fcPfName = htmlAttr(field["name"]!"")>
                        <#assign fcPfRaw = field["type"]!"text">
                        <#assign fcPfType = "text">
                        <#if fcPfRaw == "number" || fcPfRaw == "date" || fcPfRaw == "email" || fcPfRaw == "tel" || fcPfRaw == "password">
                        <#assign fcPfType = fcPfRaw>
                        </#if>
                        <div class="popup-field" data-name="${fcPfName}">
                            <label>${htmlText(field["label"]!"")}<#if field["requiredYn"]!false><span class="required-mark"> *</span></#if></label>
                        <#if fcPfRaw == "textarea">
                            <textarea name="${fcPfName}"<#if field["requiredYn"]!false> required</#if>></textarea>
                        <#elseif fcPfRaw == "select">
                            <select name="${fcPfName}"<#if field["requiredYn"]!false> required</#if>></select>
                        <#else>
                            <input type="${fcPfType}" name="${fcPfName}"<#if field["requiredYn"]!false> required</#if> />
                        </#if>
                        </div>
                    </#list>
                    </form>
                </div>
                <div class="frg-fc-popup-foot">
                <#if (props["cancelYn"])!true>
                    <button type="button" class="cancel">취소</button>
                </#if>
                    <button type="button" class="ok">${htmlText((props["confirmText"])!"확인")}</button>
                </div>
            </div>
