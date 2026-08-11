<#-- P4 MVP: MGMT_LIST_DETAIL+TABLE_VIEW 1뷰. 다른 뷰/모듈은 P5. -->
<#--
  아티팩트: List (JS) → {stem}List.js (계약 §1.1 #3)
  역할: window.JWorks_JS{Domain}{Role} 네임스페이스 + IIFE + __defined 골격.
        commonList(JWorks_JSCommonList) 뷰전환 컨트롤러를 init.
  🔒 이 파일은 구조값(stem/role)만 사용. props 자유문자열 삽입 없음(뷰 배선은 ListTableView.js 담당).
  스크립트릿 0 / 배너 0.
  🔒 role/stem은 TemplateContextBuilder(§5.1/§1.1)에서 화이트리스트(^[a-z][a-zA-Z0-9]*$) 재검증된
     구조값이다 → cap_first로 조립한 NS도 JS 식별자로 안전(window.${r"${NS}"} 식별자 문맥 안전).
     단, JS 문자열 리터럴 내부(console.error)에 들어가는 NS는 계약 §3.3 원문삽입금지에 따라 jsString() 경유.
-->
<#assign Domain = stem?cap_first>
<#assign Role = role?cap_first>
<#assign NS = "JWorks_JS" + Domain + Role>
window.${NS} = window.${NS} || {};
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
					console.error("[${jsString(NS)}] 뷰 초기화 실패");
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

})(window.${NS});
