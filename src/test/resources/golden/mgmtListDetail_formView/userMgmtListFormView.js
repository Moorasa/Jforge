window.JWorks_JSUserMgmtAdminFormView = window.JWorks_JSUserMgmtAdminFormView || {};
(function(view) {
	"use strict";

	if (view.__defined) {
		return;
	}
	view.__defined = true;

	// FORM_VIEW 배선 설정(이스케이프된 리터럴). renderCallback/도메인 바인딩 시 참조.
	// type은 산출 JSP에서 허용목록 리터럴 매핑으로 검증되며, 여기 config는 jsString 리터럴이다.
	var config = {
		fields: [
			{ name: "userId", label: "사용자 ID", type: "text", requiredYn: true, styleClass: "fld-id" },
			{ name: "email", label: "이메일", type: "email", requiredYn: false, styleClass: "" },
			{ name: "memo", label: "메모", type: "textarea", requiredYn: false, styleClass: "" },
			{ name: "grade", label: "등급", type: "select", requiredYn: false, styleClass: "" }
		]
	};

	// selectionType → commonListFormView.js 38행 selectionType('checkbox'|'none').
	var selectionType = "checkbox";

	view.getConfig = function() {
		return config;
	};

	function init() {
		var $container = $("#form-view");
		if (!$container.length) {
			$container = $("section#form-view");
		}

		// 번들 런타임(commonListFormView.js) 배선. init 시그니처: init(options)(29행).
		// 필수 $container(31행), 선택 apiInfo(37행)/selectionType(38행). apiInfo는 도메인별 배선점.
		JWorks_JSCommonListFormView.init({
			$container: $container,
			apiInfo: {
				// TODO(배선): 실제 폼 데이터 조회 API 및 콜백으로 교체.
				url: "",
				param: {},
				renderCallback: function(res) {
					// TODO(배선): 폼 값 바인딩은 도메인별 구현(config.fields 참조).
				}
			},
			selectionType: selectionType
		});
	}

	// ===============================================================================================
	// Public API
	view.init = init;

	$(function() {
		init();
	});

})(window.JWorks_JSUserMgmtAdminFormView);
