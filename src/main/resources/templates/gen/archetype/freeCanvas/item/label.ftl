<#-- CONTROL/LABEL — 캔버스 인라인 파셜(§17.4) -->
<#assign fcLevelRaw = (props["level"])!"normal">
<#assign fcLevel = "normal">
<#if fcLevelRaw == "title"><#assign fcLevel = "title"><#elseif fcLevelRaw == "caption"><#assign fcLevel = "caption"></#if>
<#assign fcClass = cssToken((props["styleClass"])!"")>
            <span class="frg-fc-label frg-fc-label-${fcLevel}<#if fcClass?length gt 0> ${fcClass}</#if>">${htmlText((props["text"])!"라벨")}</span>
