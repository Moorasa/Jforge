window.MagicIAM_JSUserMgmtAdminCardView = window.MagicIAM_JSUserMgmtAdminCardView || {};
(function(view) {
	"use strict";

	if (view.__defined) {
		return;
	}
	view.__defined = true;

	// CARD_VIEW 배선 설정(이스케이프된 리터럴). renderCallback이 카드 바인딩 시 참조.
	var config = {
		titleField: "userName",
		subtitleField: "userId",
		imageField: "avatar"
	};

	// 카드 본문 항목 정의(이스케이프된 리터럴). 라벨/가시성 참조용.
	var columns = [
		{ name: "deptName", displayName: "부서", displayYn: true, sortYn: false }
	];

	// selectMode → 런타임 selectionType. 'none'은 선택 없음.
	var selectionType = "checkbox";

	view.getConfig = function() {
		return config;
	};

	view.getColumns = function() {
		return columns;
	};

	function init() {
		var $container = $("#card-view");
		if (!$container.length) {
			$container = $("section#card-view");
		}

		// 번들 런타임(commonListCardView.js) 배선. init 시그니처: init(options)(43행).
		// 필수 $container(44행), 선택 apiInfo(51행)/searchCallback(54행)/sortCallback(78행)/
		// categoryChangeCallback(91행). apiInfo는 도메인별로 채우는 배선점.
		MagicIAM_JSCommonListCardView.init({
			$container: $container,
			apiInfo: {
				// TODO(배선): 실제 목록 조회 API 및 콜백으로 교체.
				url: "",
				param: {},
				defaultSort: { item: (columns[0] ? columns[0].name : (config.titleField || "")), order: "asc" },
				emptyMainText: "데이터가 없습니다.",
				emptySubText: "",
				renderCallback: function(res) {
					// TODO(배선): .layout-body 카드 렌더는 도메인별 구현(config.titleField 등 참조).
				}
			},
			searchCallback: function() {
				// TODO(배선): 검색 실행(cardView.getList 등).
			},
			sortCallback: function(sort) {
				// TODO(배선): 정렬 적용({ item, order }).
			},
			categoryChangeCallback: function() {
				// TODO(배선): 카테고리 변경 처리.
			}
		});
	}

	// ===============================================================================================
	// Public API
	view.init = init;

	$(function() {
		init();
	});

})(window.MagicIAM_JSUserMgmtAdminCardView);
