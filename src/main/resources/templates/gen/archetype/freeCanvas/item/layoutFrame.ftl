<#-- LAYOUT_FRAME — 캔버스 인라인 파셜(§17.4/§17.9, §19 프레임 연결).
     "다른 화면을 불러올 자리"다. §19 부터 `frameSrc` props 가 게이트를 통과하면 src 를 산출하고,
     없거나 형태가 어긋나면 **종전대로 src 없는 빈 iframe**이다(기존 정의 산출 불변).
     id 는 frameId props 가 있을 때만 붙인다(도메인 JS 가 잡을 손잡이). -->
<#import "/common/frameSrc.ftl" as frameLib>
<#assign fcFrameId = htmlAttr((props["frameId"])!"")>
<#assign fcFrameClass = cssToken((props["paneClass"])!"")>
<#assign fcFrameSrc = frameLib.safeFrameSrc(props)>
            <iframe class="frg-fc-frame<#if fcFrameClass?length gt 0> ${fcFrameClass}</#if>"<#if fcFrameId?length gt 0> id="${fcFrameId}"</#if><#if fcFrameSrc?length gt 0> src="${r"${ctx}"}${htmlAttr(fcFrameSrc)}"</#if> title="${htmlAttr((props["title"])!"")}"></iframe>
