<#-- ASSOCIATE_TABS — 캔버스 인라인 파셜(§17.4). detail.ftl 의 associate-info 동형(id 미사용).
     탭 iframe 의 src(tab.location)은 도메인 채움 배선점이라 산출하지 않는다(§9 (B) 동일 규칙). -->
<#assign fcTabs = (props["tabs"])![]>
            <#-- §17.13 하나뿐일 때만 id(#associate-info 규칙 18개). -->
            <section<#if (canvasSoleType["ASSOCIATE_TABS"])!false> id="associate-info"</#if> class="associate-info with-tab">
                <div class="tabs">
                <#list fcTabs as tab>
                    <#assign fcTabClass = cssToken(tab["tabClass"]!"")>
                    <div class="tab<#if fcTabClass?length gt 0> ${fcTabClass}</#if><#if tab?index == 0> on</#if>">${htmlText(tab["label"]!"")}</div>
                </#list>
                </div>
                <div class="contents">
                <#list fcTabs as tab>
                    <#assign fcTabClass = cssToken(tab["tabClass"]!"")>
                    <iframe title="${htmlAttr(tab["label"]!"")}" class="associate-frame<#if fcTabClass?length gt 0> ${fcTabClass}</#if><#if tab?index == 0> on</#if>"></iframe>
                </#list>
                </div>
            </section>
