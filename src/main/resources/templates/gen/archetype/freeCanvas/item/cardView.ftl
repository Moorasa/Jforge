<#-- CARD_VIEW — 캔버스 인라인 파셜(§17.4). module/cardView.ftl 과 동형.
     §17.13 id 는 **캔버스에 하나뿐일 때만** 찍는다 — #card-view 규칙 73개가 전부 id 선택자라
     클래스만으로는 MagicIAM 스타일이 하나도 걸리지 않는다. 2개 이상이면 id 중복이므로 생략.
     🔒 자유문자열은 htmlText/htmlAttr, class 토큰은 cssToken 만. -->
<#assign fcCardClass = cssToken((props["cardStyleClass"])!"")>
<#assign fcSelectMode = (props["selectMode"])!"none">
            <section<#if (canvasSoleType["CARD_VIEW"])!false> id="card-view"</#if> class="card-view<#if fcCardClass?length gt 0> ${fcCardClass}</#if>"
                data-select-mode="${htmlAttr(fcSelectMode)}"
                data-title-field="${htmlAttr((props["titleField"])!"")}"
                data-subtitle-field="${htmlAttr((props["subtitleField"])!"")}"
                data-image-field="${htmlAttr((props["imageField"])!"")}">
                <div class="layout-column">
                    <div class="layout-header">
                        <div class="layout-left">
                            <section class="total"><span class="count">0</span></section>
                        <#if (props["categoryYn"])!false>
                            <section class="category"><select aria-label="카테고리"></select></section>
                        </#if>
                        </div>
                    </div>
                    <#-- 카드 본문은 런타임 renderCallback 이 .layout-body 에 채운다. -->
                    <div class="layout-body"></div>
                <#if (props["pagingYn"])!false>
                    <div class="layout-footer"><div class="pagination"></div></div>
                </#if>
                </div>
            </section>
