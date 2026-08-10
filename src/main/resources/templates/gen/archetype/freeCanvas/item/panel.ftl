<#-- LAYOUT/PANEL — 캔버스 중첩 컨테이너(§17.8).
     자식은 이 div 의 형제가 아니라 **바깥 .frg-fc-item 안**에 놓인다(shell.ftl 이 재귀 삽입).
     제목은 absolute 캡션이라 자식 좌표 원점(패널 안쪽 좌상단)을 밀지 않는다. -->
<#assign fcPanelClass = cssToken((props["styleClass"])!"")>
<#assign fcPanelTitle = htmlText((props["title"])!"")>
            <div class="frg-fc-panel<#if (props["borderYn"])!true> frg-fc-panel-bordered</#if><#if (props["fillYn"])!false> frg-fc-panel-filled</#if><#if fcPanelClass?length gt 0> ${fcPanelClass}</#if>">
            <#if fcPanelTitle?length gt 0>
                <span class="frg-fc-panel-title">${fcPanelTitle}</span>
            </#if>
            </div>
