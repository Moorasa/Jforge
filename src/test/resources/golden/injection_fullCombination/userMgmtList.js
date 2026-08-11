window.JWorks_JSUserMgmtAdmin = window.JWorks_JSUserMgmtAdmin || {};
(function(list) {
	"use strict";

	if (list.__defined) {
		return;
	}
	list.__defined = true;

	// 이 화면이 지원하는 뷰 세트. MVP: TABLE 1뷰(P5에서 Tree/Card/Form 확장).
	// 각 뷰의 구현 네임스페이스는 번들 런타임(commonListTableView.js 등)이 제공.
	var supportView = [
		{ type: ViewType.TABLE, viewName: "JWorks_JSCommonListTableView" }
	];

	function init() {
		// 뷰 전환 컨트롤러(commonList) 초기화: 지원 뷰/현재 뷰/셀렉션 옵션 배선.
		JWorks_JSCommonList.init({
			supportView: supportView,
			currViewType: ViewType.TABLE,
			callback: function(ok) {
				if (!ok) {
					console.error("[JWorks_JSUserMgmtAdmin] 뷰 초기화 실패");
				}
			}
		});
	}

	// ===============================================================================================
	// Public API
	list.init = init;

	$(function() {
		init();
	});

})(window.JWorks_JSUserMgmtAdmin);
