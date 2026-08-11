/* ===============================================================================================

Name : JWorks_JSCommonListFormView.js

Description :
	JWORKS 프론트엔드 모듈 Form View에서 공통으로 사용하는 유틸리티 파일입니다.

Remarks :
	재배포를 금합니다.
	
=============================================================================================== */
window.JWorks_JSCommonListFormView = window.JWorks_JSCommonListFormView || {};
(function(formView) {
	"use strict";

	if (formView.__defined) {
		return;
	}
	formView.__defined = true;


	let $container = null;

	let apiInfo = null;
	/**
	 * 테이블의 공통 이벤트 핸들러를 초기화
	 * @param {object} options - 설정 객체
	*/
	formView.init = function(options) {

		if (!options || !options.$container || !options.$container.length) {
			console.error("formView.init: 필수 옵션인 $container가 유효하지 않습니다.");
			return;
		}

		$container = options.$container;
		// init 은 두 번 불린다(commonList.showView + 생성물 listFormViewJs). 넘어오지 않은
		// apiInfo 로 덮어쓰지 않는다 — 상세는 commonListTableView.init 주석 참조.
		if (options.apiInfo) {
			apiInfo = options.apiInfo;
		}
		let selectionType = options.selectionType || 'checkbox';

		if (selectionType === 'checkbox') {
			// 체크박스 기능 초기화
			$container.on("change", "#select-all, .row-checkbox", function() {
				if ($(this).is("#select-all")) {
					$container.find(".row-checkbox").prop("checked", $(this).prop("checked"));
				}
				const checkedCount = $container.find(".row-checkbox:checked").length;
				const totalCount = $container.find(".row-checkbox").length;
				$container.find("#select-all").prop("checked", totalCount > 0 && totalCount === checkedCount);

				window.parent.dispatchEvent(new CustomEvent('selectionChange', {
					detail: { hasChecked: checkedCount > 0 }
				}));
			});
		}

		// 미배선 상태는 초기값 null 로도 나타난다(typeof null 은 "object"). 참/거짓으로 판단.
		if (!apiInfo) {
		}
		else {
			registEvent();
		}

		// 부모(Popup)로부터 메시지 수신 대기
		window.addEventListener("message", function(event) {
			if (window.location.origin === event.origin) {
				// pass
			}
			else {
				console.log("error : not same origin");
				return;
			}

			if (event.data.type === 'SET_EMPTY_MODE' && event.data.mode === 'popup') {
				isPopupMode = true;

				if ($container) {
					const $emptyCase = $container.find(".empty-case");
					if ($emptyCase.length > 0) {
						$emptyCase.addClass("popup");
					}
				}
			}
		});
		
	}

	function registEvent() {
	}

	formView.getList = function() {
		// 배선 전이면 조회할 대상이 없다(상세는 commonListTableView.getList 주석 참조).
		if (!apiInfo || !apiInfo.url) {
			return;
		}

		const data = {};

		// API 호출
		$.ajax({
			url: apiInfo.url,
			type: "POST",
			cache: false,
			contentType: "application/json",
			data: JSON.stringify(data),
			dataType: "JSON",
			success: function(result, status, xhr) {
				getListPostAction(result);
			},
			error: function(xhr, status, error) {
				console.log(xhr);
				console.log(status);
				console.log(xhr.responseText);
			},
			timeout: Constants.DEFAULT_AJAX_TIMEOUT
		});

	}

	function getListPostAction(res) {
		if (res.code == 200) {
			console.log(res);
		}
		else {
			alert(res.msg);
			return;
		}

		render(res.data);

		apiInfo.renderCallback(res);
	}

	function render(data) {
	}

})(window.JWorks_JSCommonListFormView);
