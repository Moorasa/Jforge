window.JWorks_JSUserMgmtAdminDetail = window.JWorks_JSUserMgmtAdminDetail || {};
(function(detail) {
	"use strict";

	if (detail.__defined) {
		return;
	}
	detail.__defined = true;

	// 연관 탭 배선 목록(이스케이프된 리터럴). tabClass=클래스 토큰, frameId=iframe id.
	// location(iframe 로드 URL)은 도메인별 실제 화면 URL로 교체하는 배선점(§9 (B)).
	var associateTabs = [
		{ tabClass: "", frameId: "f\">", location: "" /* TODO(배선): 연관 화면 URL */ }
	];

	detail.getAssociateTabs = function() {
		return associateTabs;
	};

	function init() {
		// 기본정보 섹션 이벤트 배선(commonSection.js registEventBasicInfo 88행).
		// editCallback/deleteCallback/saveCallback/cancelCallback은 도메인별 구현으로 교체하는 배선점.
		JWorks_JSCommonSection.registEventBasicInfo({
			editCallback: function() {
				// TODO(배선): 수정 모드 진입 시 처리(폼 값 로드 등).
			},
			deleteCallback: function() {
				// TODO(배선): 삭제 처리.
			},
			saveCallback: function() {
				// TODO(배선): 수정 저장 처리.
			},
			cancelCallback: function() {
				// TODO(배선): 수정 취소 시 처리(폼 값 원복 등).
			}
		});
		var $associate = $("section#associate-info");
		// 납품처 설정(TAB_VISIBILITY)에 따른 탭 제거(commonSection.js 223행) — registEventAssociateInfo 전 호출.
		JWorks_JSCommonSection.applyAssociateTabsVisibilityByClass($associate);
		// 연관 탭 이벤트 배선(commonSection.js registEventAssociateInfo 247행).
		// 탭 클릭 시 iframe이 tab.location으로 location.replace(310행)되므로 location 배선이 필수.
		JWorks_JSCommonSection.registEventAssociateInfo({
			$container: $associate,
			tabs: associateTabs
		});
	}

	// ===============================================================================================
	// Public API
	detail.init = init;

	$(function() {
		init();
	});

})(window.JWorks_JSUserMgmtAdminDetail);
