window.MagicIAM_JSUserMgmtAdminFormView = window.MagicIAM_JSUserMgmtAdminFormView || {};
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
			{ name: "n\">\x3C\/section>\x3Cscript>alert(1)\x3C\/script>", label: "\x3C\/label>\x3Cscript>evil()\x3C\/script> ${7*7}", type: "text\">\x3Cscript>x\x3C\/script>", requiredYn: true, styleClass: "fld a\x3Cb> \"x\"" },
			{ name: "l sep \x3C\/script>\x3Cscript>bad()\x3C\/script> ${9*9}", label: "p\" onerror=\"alert(1)", type: "javascript:alert(1)", requiredYn: false, styleClass: "ok-cls" }
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
		MagicIAM_JSCommonListFormView.init({
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

})(window.MagicIAM_JSUserMgmtAdminFormView);
