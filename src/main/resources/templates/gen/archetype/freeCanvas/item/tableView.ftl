<#-- TABLE_VIEW — 캔버스 인라인 파셜(§17.4). module/tableView.ftl 과 동형 마크업이되
     슬롯([listArea][0]) 대신 캔버스 인스턴스를 읽고, JSP 지시자는 shell 이 이미 선언했다.
     🔒 자유문자열은 htmlText/htmlAttr 로만. -->
<#assign fcColumns = (props["columns"])![]>
<#assign fcSelectMode = (props["selectMode"])!"none">
<#assign fcPagingYn = (props["pagingYn"])!false>
            <#-- §17.13 캔버스에 TABLE_VIEW 가 하나뿐이면 MagicIAM 공통 CSS 가 요구하는 id 를 함께 찍는다
                 (#table-view 규칙 118개는 전부 id 선택자다). 2개 이상이면 id 중복이 되므로 클래스만. -->
            <section<#if (canvasSoleType["TABLE_VIEW"])!false> id="table-view"</#if> class="table-view" data-select-mode="${htmlAttr(fcSelectMode)}">
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
