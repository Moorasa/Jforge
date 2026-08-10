<#-- TABLE_VIEW — 캔버스 인라인 파셜(§17.4). module/tableView.ftl 과 동형 마크업이되
     슬롯([listArea][0]) 대신 캔버스 인스턴스를 읽고, JSP 지시자는 shell 이 이미 선언했다.
     🔒 자유문자열은 htmlText/htmlAttr 로만. -->
<#assign fcColumns = (props["columns"])![]>
<#assign fcSelectMode = (props["selectMode"])!"none">
<#assign fcPagingYn = (props["pagingYn"])!false>
            <section class="table-view" data-select-mode="${htmlAttr(fcSelectMode)}">
                <div class="layout-body">
                    <table>
                        <colgroup>
                        <#if fcSelectMode == "checkbox" || fcSelectMode == "radio">
                            <col class="col-select" />
                        </#if>
                        <#list fcColumns as col><#if (col["displayYn"]!true)>
                            <col />
                        </#if></#list>
                        </colgroup>
                        <thead>
                            <tr>
                            <#if fcSelectMode == "checkbox">
                                <td class="col-select"><input type="checkbox" /></td>
                            <#elseif fcSelectMode == "radio">
                                <td class="col-select"></td>
                            </#if>
                            <#list fcColumns as col><#if (col["displayYn"]!true)>
                                <td data-name="${htmlAttr(col["name"]!"")}"><div><span>${htmlText(col["displayName"]!"")}</span></div></td>
                            </#if></#list>
                            </tr>
                        </thead>
                        <tbody></tbody>
                    </table>
                </div>
            <#if fcPagingYn>
                <div class="pagination"></div>
            </#if>
            </section>
