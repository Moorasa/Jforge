<#-- TOOLBAR — 캔버스 인라인 파셜(§17.4). action 은 data-action 속성으로만(§3). -->
<#assign fcButtons = (props["buttons"])![]>
            <div class="list-toolbar">
            <#list fcButtons as btn>
                <button type="button" class="${cssToken((btn["styleClass"])!"btn-default")}" data-action="${htmlAttr(btn["action"]!"")}">${htmlText(btn["label"]!"")}</button>
            </#list>
            </div>
