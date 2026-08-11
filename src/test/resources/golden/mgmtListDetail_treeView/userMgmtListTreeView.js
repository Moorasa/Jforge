window.JWorks_JSUserMgmtAdminTreeView = window.JWorks_JSUserMgmtAdminTreeView || {};
(function(view) {
	"use strict";

	if (view.__defined) {
		return;
	}
	view.__defined = true;

	// TREE_VIEW 배선 설정(이스케이프된 리터럴). parser/renderCallback이 계층 바인딩 시 참조.
	var config = {
		labelField: "orgName",
		idField: "orgNo",
		parentField: "parentNo",
		iconField: "orgType"
	};

	// 루트 노드 매핑(commonListTreeView.js 485~486행 root.name/root.iconClass).
	// rootIconClass는 cssToken 화이트리스트 통과 토큰(위반 시 빈 문자열).
	var rootMapping = {
		name: "전체 조직",
		iconClass: "icon-org-root"
	};

	// selectMode → 런타임 selectionType. 'checkbox'면 features.checkbox 활성.
	var selectionType = "checkbox";
	var orderingEnabled = true;

	view.getConfig = function() {
		return config;
	};

	function init() {
		var $container = $("#tree-view");
		if (!$container.length) {
			$container = $("section#tree-view");
		}

		// 번들 런타임(commonListTreeView.js) 배선. init 시그니처: init(options)(33행).
		// 필수 $container(34행), 선택 apiInfo(40행)/features(41행)/callbacks(42행)/
		// dataMapping(43행)/search(44행)/orderingState(50행). apiInfo는 도메인별로 채우는 배선점.
		JWorks_JSCommonListTreeView.init({
			$container: $container,
			apiInfo: {
				// TODO(배선): 실제 트리 조회 API 및 콜백으로 교체.
				url: "",
				param: {},
				emptyMainText: "데이터가 없습니다.",
				emptySubText: "",
				parser: function(data) {
					// TODO(배선): 서버 응답 → 표준 노드 배열 변환(config.idField/parentField/labelField 참조).
					return [];
				},
				renderCallback: function(res) {
					// TODO(배선): 트리 렌더 후처리(commonListTreeView.js 461행).
				}
			},
			features: {
				checkbox: { enable: (selectionType === "checkbox") },
				ordering: { enable: orderingEnabled }
			},
			callbacks: {
				onNodeClick: function(nodeData, e) {
					// TODO(배선): 노드 클릭 처리(상세 이동 등, commonListTreeView.js 108행).
				},
				onCheckChange: function(e, nodeData, $checkbox) {
					// TODO(배선): 체크 변경 처리(commonListTreeView.js 129행).
				}
			},
			dataMapping: {
				root: rootMapping
			},
			search: {
				// resetBehavior: 빈 검색어 복원 방식('expandAll' 등, commonListTreeView.js 202행).
			},
			orderingState: { ordering: {}, orderingUpdate: {}, orderingOrigin: {} }
		});
	}

	// ===============================================================================================
	// Public API
	view.init = init;

	$(function() {
		init();
	});

})(window.JWorks_JSUserMgmtAdminTreeView);
