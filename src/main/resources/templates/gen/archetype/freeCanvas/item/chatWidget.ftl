<#-- CHAT_WIDGET — 캔버스 인라인 파셜(§17.4). -->
            <section class="dashboard-widget chat-widget-panel">
                <h2>${htmlText((props["title"])!"채팅 상담")}</h2>
                <div class="chat-messages"><p>${htmlText((props["welcomeMessage"])!"")}</p></div>
                <input type="text" placeholder="${htmlAttr((props["placeholder"])!"메시지를 입력하세요")}" />
            </section>
