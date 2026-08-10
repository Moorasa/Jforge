<#-- CONTROL/IMAGE — 캔버스 인라인 파셜(§17.4). fit 은 화이트리스트 통과값만(클래스로만 반영). -->
<#assign fcFitRaw = (props["fit"])!"contain">
<#assign fcFit = "contain">
<#if fcFitRaw == "cover"><#assign fcFit = "cover"><#elseif fcFitRaw == "fill"><#assign fcFit = "fill"></#if>
<#assign fcClass = cssToken((props["styleClass"])!"")>
            <img class="frg-fc-image frg-fc-image-${fcFit}<#if fcClass?length gt 0> ${fcClass}</#if>" src="${htmlAttr((props["src"])!"")}" alt="${htmlAttr((props["alt"])!"")}" />
