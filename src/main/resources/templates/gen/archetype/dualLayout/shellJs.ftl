<#-- P5-5c: DUAL_LAYOUT 아키타입 JS. JWorks commonSection.js dual 런타임 배선(계약 §10). -->
<#--
  아티팩트: dualJs → {stem}.js (계약 §10.1)
  역할: window.JWorks_JS{Domain}{Role} 네임스페이스 + IIFE + __defined 골격.
    - JWorks_JSCommonSection.postMessageEventListener(info)(25행) 등록 → 자식 프레임의
      DUAL_LAYOUT_VIEW_TYPE_CHANGE(45행)/SYNC_VIEW_STATE(64행) 메시지로 좌우 비율·상태 동기화.
    - 리사이저 드래그/접기/펼치기 이벤트는 commonSection.js 자체 $(function)(364행)가 자동 바인딩(여기 불필요).
  🔒 이 파일은 구조값(stem/role)만 사용. props 자유문자열 삽입 없음(패인 배선은 shell iframe id + 도메인 URL).
  스크립트릿 0 / 배너 0. role/stem은 §5.1/§1.1 화이트리스트 재검증 구조값 → NS 식별자 안전.

  ⚠ 배선점(TODO, 개발자 채움): 좌/우 iframe(#dual-left-frame-* / #dual-right-frame-* 또는 지정 frameId)의
     src를 도메인 화면 URL로 로드한다(예: leftFrame.contentWindow.location.replace(url)). DUAL의 좌측
     프레임은 뷰전환(commonList)을 가진 목록 화면, 우측은 상세/연관 화면을 로드하는 것이 일반 패턴이다.
-->
<#assign Domain = stem?cap_first>
<#assign Role = role?cap_first>
<#assign NS = "JWorks_JS" + Domain + Role>
window.${NS} = window.${NS} || {};
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

})(window.${NS});
