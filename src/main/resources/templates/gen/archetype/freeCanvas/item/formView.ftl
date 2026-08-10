<#-- FORM_VIEW — 캔버스 인라인 파셜(§17.4). input type 은 화이트리스트 통과값만. -->
<#assign fcFields = (props["fields"])![]>
            <section class="form-view">
                <div class="layout-body">
                    <form class="frg-fc-form" onsubmit="return false;">
                    <#list fcFields as field>
                        <#assign fcFieldName = htmlAttr(field["name"]!"")>
                        <#assign fcRawType = field["type"]!"text">
                        <#assign fcFieldType = "text">
                        <#if fcRawType == "number" || fcRawType == "date" || fcRawType == "email" || fcRawType == "tel" || fcRawType == "password">
                        <#assign fcFieldType = fcRawType>
                        </#if>
                        <div class="form-field" data-name="${fcFieldName}">
                            <label for="fc-${idx?c}-${fcFieldName}">${htmlText(field["label"]!"")}</label>
                        <#if fcRawType == "textarea">
                            <textarea id="fc-${idx?c}-${fcFieldName}" name="${fcFieldName}"></textarea>
                        <#elseif fcRawType == "select">
                            <select id="fc-${idx?c}-${fcFieldName}" name="${fcFieldName}"></select>
                        <#else>
                            <input type="${fcFieldType}" id="fc-${idx?c}-${fcFieldName}" name="${fcFieldName}" />
                        </#if>
                        </div>
                    </#list>
                    </form>
                </div>
            </section>
