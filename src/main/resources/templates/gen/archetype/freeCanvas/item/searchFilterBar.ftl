<#-- SEARCH_FILTER_BAR — 캔버스 인라인 파셜(§17.4). -->
<#assign fcFilters = (props["filters"])![]>
            <section class="search">
            <#if (fcFilters?size gt 0)>
                <div class="filter">
                <#list fcFilters as f>
                    <select name="${htmlAttr(f["name"]!"")}" aria-label="${htmlAttr(f["label"]!"")}"></select>
                </#list>
                </div>
            </#if>
            <#if (props["keywordYn"])!false>
                <div class="input"><input type="text" name="keyword" /></div>
            </#if>
            <#if (props["dateRangeYn"])!false>
                <div class="filter-datepicker">
                    <input type="text" class="datepicker-start" />
                    <input type="text" class="datepicker-end" />
                </div>
            </#if>
            </section>
