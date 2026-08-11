<#-- DETAIL_BASIC — 캔버스 인라인 파셜(§17.4). detail.ftl 의 basic-info 골격과 동형이되
     id 를 쓰지 않는다(캔버스에는 같은 모듈이 여럿 있을 수 있다).
     🔒 input type 은 허용목록 리터럴 매핑만(원문 직접삽입 0). -->
<#assign fcBiFields = (props["fields"])![]>
<#assign fcBiClass = cssToken((props["basicStyleClass"])!"")>
            <#-- §17.13 하나뿐일 때만 id(#basic-info 규칙 92개). -->
            <section<#if (canvasSoleType["DETAIL_BASIC"])!false> id="basic-info"</#if> class="basic-info view-mode<#if fcBiClass?length gt 0> ${fcBiClass}</#if>">
                <div class="detail-info-view">
                    <div class="layout-column">
                    <#list fcBiFields as f>
                        <div class="detail-field" data-name="${htmlAttr(f["name"]!"")}">
                            <span class="label">${htmlText(f["label"]!"")}</span>
                            <span class="value">-</span>
                        </div>
                    </#list>
                    </div>
                <#if (props["attributeYn"])!false>
                    <div class="attribute-area">
                        <span class="label">속성</span>
                        <div class="attribute-chip-container"><span>-</span></div>
                    </div>
                </#if>
                </div>
            <#if (props["editableYn"])!true>
                <div class="buttons">
                    <button type="button" class="update">수정</button>
                    <button type="button" class="delete">삭제</button>
                </div>
            </#if>
            </section>
