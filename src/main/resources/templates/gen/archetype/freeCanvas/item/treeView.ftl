<#-- TREE_VIEW — 캔버스 인라인 파셜(§17.4). module/treeView.ftl 동형(id 미사용). -->
<#assign fcTreeClass = cssToken((props["treeStyleClass"])!"")>
            <section class="tree-view<#if fcTreeClass?length gt 0> ${fcTreeClass}</#if>"
                data-select-mode="${htmlAttr((props["selectMode"])!"single")}"
                data-label-field="${htmlAttr((props["labelField"])!"")}"
                data-id-field="${htmlAttr((props["idField"])!"")}"
                data-parent-field="${htmlAttr((props["parentField"])!"")}"
                data-icon-field="${htmlAttr((props["iconField"])!"")}"
                data-ordering="<#if (props["orderingYn"])!false>true<#else>false</#if>">
                <div class="layout-column">
                    <div class="layout-header">
                        <div class="layout-left">
                            <section class="total"><span class="count">0</span></section>
                        <#if (props["searchYn"])!false>
                            <section class="search">
                                <div class="input" data-placeholder="검색어를 입력하세요"></div>
                                <div class="search-icon"></div>
                            </section>
                        </#if>
                        </div>
                    </div>
                    <#-- 노드는 런타임(commonListTreeView.js)이 .layout-body 에 채운다. -->
                    <div class="layout-body">
                    <#if ((props["rootLabel"])!"")?length gt 0>
                        <div class="tree-root">${htmlText(props["rootLabel"])}</div>
                    </#if>
                    </div>
                </div>
            </section>
