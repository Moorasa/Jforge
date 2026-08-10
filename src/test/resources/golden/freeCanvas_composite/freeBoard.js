window.MagicIAM_JSFreeBoardCanvas = window.MagicIAM_JSFreeBoardCanvas || {};
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
        // [1] TABLE_VIEW
        // TODO: 목록 조회 API 를 연결한다(§14 서버 바인딩 또는 {stem}Design.js).
        // [2] FORM_VIEW
        // [3] BUTTON
        screen.item(3).find("button").on("click", function() {
            // TODO: 동작을 연결한다.
        });
        // [4] BUTTON
        screen.item(4).find("button").on("click", function() {
            // TODO: 동작을 연결한다.
        });
        // [5] BUTTON
        screen.item(5).find("button").on("click", function() {
            // TODO: 동작을 연결한다.
        });
    };

    $(screen.init);
})(window.MagicIAM_JSFreeBoardCanvas);
