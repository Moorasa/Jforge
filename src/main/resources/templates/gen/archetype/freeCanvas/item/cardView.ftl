<#-- CARD_VIEW — 캔버스 인라인 파셜(§17.4). module/cardView.ftl 과 동형이되 id 를 쓰지 않는다
     (같은 모듈을 N개 놓을 수 있으므로 문서 내 id 중복을 만들지 않는다).
     🔒 자유문자열은 htmlText/htmlAttr, class 토큰은 cssToken 만. -->
<#assign fcCardClass = cssToken((props["cardStyleClass"])!"")>
<#assign fcSelectMode = (props["selectMode"])!"none">
            <section class="card-view<#if fcCardClass?length gt 0> ${fcCardClass}</#if>"
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
