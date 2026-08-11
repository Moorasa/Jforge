window.JWorks_JSFreeBoardCanvas = window.JWorks_JSFreeBoardCanvas || {};
(function(screen) {
    "use strict";
    if (screen.__defined) { return; }
    screen.__defined = true;

    var $root = $("#freeBoard-canvas");

    /** 캔버스 항목 1건을 위치 클래스(.frg-fc-N)로 집는다 — 좌표는 CSS 가 갖는다(§17.3). */
    screen.item = function(index) {
        return $root.find(".frg-fc-" + index);
    };

    screen.init = function() {
        // [1] PANEL
        // [2] BUTTON
        screen.item(2).find("button").on("click", function() {
            // TODO: 동작을 연결한다.
        });
        // [3] LABEL
    };

    $(screen.init);
})(window.JWorks_JSFreeBoardCanvas);
