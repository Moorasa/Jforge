<#-- SEMICIRCLE_CHART — 캔버스 인라인 파셜(§17.4).
     🔒 숫자는 ?c 로만 문자열화(?string 은 메서드 인자로 넘기면 포매터 객체가 찍힌다). -->
<#assign fcValueRaw = (props["value"])!72>
<#assign fcValue = "">
<#if fcValueRaw?is_number><#assign fcValue = fcValueRaw?c><#elseif fcValueRaw?is_string><#assign fcValue = fcValueRaw></#if>
            <section class="dashboard-widget semicircle-chart" data-value="${htmlAttr(fcValue)}">
                <h2>${htmlText((props["title"])!"달성률")}</h2>
                <div class="semicircle-gauge"></div>
                <strong>${htmlText(fcValue)}${htmlText((props["unit"])!"%")}</strong>
            </section>
