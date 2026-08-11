window.JWorks_JSUserMgmtAdminTableView = window.JWorks_JSUserMgmtAdminTableView || {};
(function(view) {
	"use strict";

	if (view.__defined) {
		return;
	}
	view.__defined = true;

	// TABLE_VIEW props.columns 정의(이스케이프된 리터럴). 헤더/엑셀/컬럼가시성 참조용.
	var columns = [
		{ name: "c\"d", displayName: "\x3Cimg onerror=1>", displayYn: true, sortYn: true }
	];

	// selectMode → 런타임 selectionType. 'none'은 선택 컬럼 없음.
	var selectionType = "checkbox";

	view.getColumns = function() {
		return columns;
	};

	function init() {
		var $container = $("#table-view");
		if (!$container.length) {
			$container = $("section#table-view");
		}

		// 번들 런타임(commonListTableView.js) 배선. apiInfo는 도메인별로 채우는 배선점.
		JWorks_JSCommonListTableView.init({
			$container: $container,
			selectionType: selectionType,
			tableType: "",
			apiInfo: {
				// TODO(배선): 실제 목록 조회 API 및 콜백으로 교체.
				url: "",
				param: {},
				defaultSort: { item: (columns[0] ? columns[0].name : ""), order: "asc" },
				emptyMainText: "데이터가 없습니다.",
				emptySubText: "",
				excelDownloadUrl: "",
				csvUploadUrl: "",
				renderCallback: function(res) {
					// TODO(배선): tbody 행 렌더는 도메인별 구현.
				}
			},
			callbacks: {}
		});
	}

	// ===============================================================================================
	// Public API
	view.init = init;

	$(function() {
		init();
	});

})(window.JWorks_JSUserMgmtAdminTableView);
