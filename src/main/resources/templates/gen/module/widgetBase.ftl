<#assign props={}>
<#assign widgetInst={}>
<#list slots["widgetArea"]![] as inst><#if inst.moduleTypeCode == widgetType><#assign props=inst.props><#assign widgetInst=inst><#break></#if></#list>
<#assign title=htmlText(props["title"]!defaultTitle)>
<#-- 🔒 숫자 → 문자열은 ?c(로케일 무관)로만. ?string 은 메서드 인자로 넘어갈 때 값이 아니라
     FreeMarker 포매터 객체가 찍힌다(예: data-value="...NumberFormatter@43208e9a"). -->
<#assign valueRaw = props["value"]!(widgetType == "BAR_CHART")?then(65, 72)>
<#assign valueText = "">
<#if valueRaw?is_number><#assign valueText = valueRaw?c><#elseif valueRaw?is_string><#assign valueText = valueRaw></#if>
<#if widgetType == "BAR_CHART">
<section id="${stem}-${widgetSelector}" class="dashboard-widget bar-chart" data-value="${htmlAttr(valueText)}"<#if (widgetInst["data"])?? || ((widgetInst["events"])?? && widgetInst["events"]?is_sequence && widgetInst["events"]?size gt 0)> data-frg-instance-id="${htmlAttr(widgetInst.instanceId!"")}" data-frg-module-type="${htmlAttr(widgetInst.moduleTypeCode!"")}"</#if>><h2>${title}</h2><div class="bar-track"><span class="bar-value"></span></div><strong>${htmlText(valueText)}${htmlText(props["unit"]!"%")}</strong></section>
<#elseif widgetType == "SEMICIRCLE_CHART">
<section id="${stem}-${widgetSelector}" class="dashboard-widget semicircle-chart" data-value="${htmlAttr(valueText)}"<#if (widgetInst["data"])?? || ((widgetInst["events"])?? && widgetInst["events"]?is_sequence && widgetInst["events"]?size gt 0)> data-frg-instance-id="${htmlAttr(widgetInst.instanceId!"")}" data-frg-module-type="${htmlAttr(widgetInst.moduleTypeCode!"")}"</#if>><h2>${title}</h2><div class="semicircle-gauge"></div><strong>${htmlText(valueText)}${htmlText(props["unit"]!"%")}</strong></section>
<#elseif widgetType == "EMPTY_STATE">
<section id="${stem}-${widgetSelector}" class="dashboard-widget empty-state"<#if (widgetInst["data"])?? || ((widgetInst["events"])?? && widgetInst["events"]?is_sequence && widgetInst["events"]?size gt 0)> data-frg-instance-id="${htmlAttr(widgetInst.instanceId!"")}" data-frg-module-type="${htmlAttr(widgetInst.moduleTypeCode!"")}"</#if>><h2>${title}</h2><p>${htmlText(props["description"]!"")}</p><button type="button">${htmlText(props["actionText"]!"새로 만들기")}</button></section>
<#else>
<section id="${stem}-${widgetSelector}" class="dashboard-widget chat-widget-panel"<#if (widgetInst["data"])?? || ((widgetInst["events"])?? && widgetInst["events"]?is_sequence && widgetInst["events"]?size gt 0)> data-frg-instance-id="${htmlAttr(widgetInst.instanceId!"")}" data-frg-module-type="${htmlAttr(widgetInst.moduleTypeCode!"")}"</#if>><h2>${title}</h2><div class="chat-messages"><p>${htmlText(props["welcomeMessage"]!"")}</p></div><input type="text" placeholder="${htmlAttr(props["placeholder"]!"메시지를 입력하세요")}" /></section>
</#if>
