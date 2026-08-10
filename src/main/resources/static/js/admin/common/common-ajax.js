/* ===============================================================================================

Name : MagicIAM_JSCommonAjax.js

Description :
	JWORKS 프론트엔드 모듈 전반에서 공통으로 사용하는 유틸리티 파일입니다.

Remarks :
	재배포를 금합니다.
	
=============================================================================================== */
window.MagicIAM_JSCommonAjax = window.MagicIAM_JSCommonAjax || {};
(function(commonAjax) {
	"use strict";

	if (commonAjax.__defined) {
		return;
	}
	commonAjax.__defined = true;


	const consoleLogDisplay = false;
	
	$(document).ajaxStart(function() {
		if(consoleLogDisplay) console.log("ajaxStart");
		// 로딩 인디케이터 show 처리 등

		window.top.postMessage({
			type: PostMessageType.SESSION_RENEW
		}, "*");
	});
	
	$.ajaxSetup({
		beforeSend: function(xhr, settings) {
			JWORKS_JSLoadingSpinner.start();	
			// 모든 ajax 요청에 실행됨
			if(consoleLogDisplay) console.log('beforeSend: 요청 전 실행');
			// request-response 확인 token 생성 등 처리
			//xhr.setRequestHeader('X-Custom-Token', 'abc123');
		},
		error: function(xhr, status, error) {
			if(consoleLogDisplay) console.error('공통 에러 처리:', error);
			if (xhr.status === 401) {
				alert("세션이 만료되었습니다.");
				top.location.href = "/iam/v2/admin/view/login/login";
			}
		}
	});

	$(document).ajaxSend(function(event, jqXHR, settings) {
		if(consoleLogDisplay) console.log('ajaxSend: 모든 AJAX 요청 전에 실행');
		// request-response 확인 token 생성 등 처리
		//jqXHR.setRequestHeader('X-Global-Header', '123456');
	});

	$(document).ajaxStop(function() {
		JWORKS_JSLoadingSpinner.end();
		if(consoleLogDisplay) console.log("ajaxStop");
		// 로딩 인디케이터 hide 처리 등
	});

})(window.MagicIAM_JSCommonAjax);
