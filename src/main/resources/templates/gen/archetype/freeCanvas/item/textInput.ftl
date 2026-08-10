<#-- CONTROL/TEXT_INPUT — 캔버스 인라인 파셜(§17.4). input type 은 화이트리스트 통과값만. -->
<#assign fcTypeRaw = (props["inputType"])!"text">
<#assign fcType = "text">
<#if fcTypeRaw == "number" || fcTypeRaw == "date" || fcTypeRaw == "email" || fcTypeRaw == "tel" || fcTypeRaw == "password">
<#assign fcType = fcTypeRaw>
</#if>
<#assign fcName = htmlAttr((props["name"])!"")>
<#assign fcLabel = htmlText((props["label"])!"")>
<#assign fcClass = cssToken((props["styleClass"])!"")>
            <div class="frg-fc-field<#if fcClass?length gt 0> ${fcClass}</#if>">
            <#if fcLabel?length gt 0>
                <label for="fc-${idx?c}-${fcName}">${fcLabel}</label>
            </#if>
                <input type="${fcType}" id="fc-${idx?c}-${fcName}" name="${fcName}"<#if ((props["placeholder"])!"")?length gt 0> placeholder="${htmlAttr(props["placeholder"])}"</#if> />
            </div>
