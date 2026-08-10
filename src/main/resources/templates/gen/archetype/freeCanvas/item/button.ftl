<#-- CONTROL/BUTTON — 캔버스 인라인 파셜(§17.4). 스코프: inst/props/idx (shell.ftl 배정) -->
<#assign fcVariantRaw = (props["variant"])!"primary">
<#assign fcVariant = "primary">
<#if fcVariantRaw == "secondary"><#assign fcVariant = "secondary"><#elseif fcVariantRaw == "danger"><#assign fcVariant = "danger"></#if>
<#assign fcClass = cssToken((props["styleClass"])!"")>
            <button type="button" class="frg-fc-button frg-fc-button-${fcVariant}<#if fcClass?length gt 0> ${fcClass}</#if>">${htmlText((props["text"])!"버튼")}</button>
