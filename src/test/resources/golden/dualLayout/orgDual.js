window.JWorks_JSOrgDualAdmin = window.JWorks_JSOrgDualAdmin || {};
(function(page) {
	"use strict";

	if (page.__defined) {
		return;
	}
	page.__defined = true;

	function init() {
		// 좌우 프레임 간 상태/뷰타입 동기화 메시지 수신 등록(commonSection.js 25행).
		// body.dual-layout이므로 SET_HEIGHT는 무시되고 DUAL_LAYOUT_VIEW_TYPE_CHANGE로 비율 조정된다.
		JWorks_JSCommonSection.postMessageEventListener({});

		// TODO(배선): 좌/우 iframe의 src를 도메인 화면 URL로 로드.
		//   var $left = $("#dual-layout-area .layout-left > iframe");
		//   $left.length && $left.get(0).contentWindow.location.replace("<좌측 화면 URL>");
		//   var $right = $("#dual-layout-area .layout-right > iframe");
		//   $right.length && $right.get(0).contentWindow.location.replace("<우측 화면 URL>");
	}

	// ===============================================================================================
	// Public API
	page.init = init;

	$(function() {
		init();
	});

})(window.JWorks_JSOrgDualAdmin);
