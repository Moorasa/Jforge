<#-- BAR_CHART — 캔버스 인라인 파셜(§17.4). id 를 쓰지 않는다(같은 위젯 N개 배치 시 중복 방지).
     🔒 숫자는 ?c(로케일 무관 표기)로만 문자열화한다. ?string 은 메서드 인자로 넘길 때
     포매터 객체가 그대로 찍히므로 쓰지 않는다. -->
<#assign fcValueRaw = (props["value"])!65>
<#assign fcValue = "">
<#if fcValueRaw?is_number><#assign fcValue = fcValueRaw?c><#elseif fcValueRaw?is_string><#assign fcValue = fcValueRaw></#if>
            <section class="dashboard-widget bar-chart" data-value="${htmlAttr(fcValue)}">
                <h2>${htmlText((props["title"])!"진행률")}</h2>
                <div class="bar-track"><span class="bar-value"></span></div>
                <strong>${htmlText(fcValue)}${htmlText((props["unit"])!"%")}</strong>
            </section>
