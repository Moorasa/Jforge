/* ===============================================================================================

Name : MagicIAM_JSCommonList.js

Description :
	JWORKS 프론트엔드 모듈 Table View에서 공통으로 사용하는 유틸리티 파일입니다.

Remarks :
	재배포를 금합니다.
	
=============================================================================================== */
window.MagicIAM_JSCommonList = window.MagicIAM_JSCommonList || {};
(function(commonList) {
	"use strict";

	if (commonList.__defined) {
		return;
	}
	commonList.__defined = true;


	const _state = {
		// 지원하는 뷰 타입 
		supportView: null,
	
		// 사용하는 뷰 타입 : 기본은 테이블 뷰
		currViewType: ViewType.TABLE,

		// 콜백 함수
		callback: null,

		// 커스텀 옵션 저장 (selectionType 등)
		customOptions: {},
		
		// 지연 로딩 여부 (동적 필터 등)
		delayInitialLoad: false
	}

	function init(option) {
		_state.supportView = option.supportView;
		
		_state.currViewType = option.currViewType;
		
		_state.callback = option.callback;
		
		_state.delayInitialLoad = option.delayInitialLoad === true;
		
		// 커스텀 옵션 저장 (supportView, currViewType 제외한 나머지)
		_state.customOptions = {};
		for (let key in option) {
			if (key !== 'supportView' && key !== 'currViewType' && key !== 'callback' && key !== 'delayInitialLoad') {
				_state.customOptions[key] = option[key];
			}
		}

		$("html").css("font-size", $(window.top.document).find('html').css("font-size"));
	
		registEvent();
	
		waitAndShowView(0, 30);
	
	}

	function getSupportView(option) {
		return _state.supportView;
	}
		
	function getCurrViewType(option) {
		return _state.currViewType;
	}
		
	function registEvent() {
		// postMessage 수신 처리
		MagicIAM_JSCommonSection.postMessageEventListener({
			$containerToSetHeight: $("section#associate-info iframe")
		});
	}
	
	function listRenderCallback() {

		// table view, card view의 경우 부모창에게 높이를 전달
		if(_state.currViewType == ViewType.TABLE
			|| _state.currViewType == ViewType.CARD
		) {
			// 부모창에게 높이를 전달
			window.parent.postMessage({
				type: PostMessageType.SET_HEIGHT,
				height: getView().getHeight()
			}, "*");
		}
	
		// 부모창에게 render 종료 알림
		window.parent.postMessage({
			type: PostMessageType.RENDER_COMPLETE,
			height: getView().getHeight()
		}, "*");
	
	}
	
	function viewTypeChangeCallback(type) {
	
		// 기존 view는 숨김
		getView().hide();

		_state.currViewType = type;

		waitAndShowView(0, 30);
		
	}
	
	function waitAndShowView(retryCount, maxRetryCount) {

		retryCount = retryCount || 0;
		maxRetryCount = maxRetryCount || 30;

		// 모든 view 객체가 생성되었는지 확인
		const missingViews = _state
				.supportView
				.filter(function(view) {
					return typeof window[view.viewName] === "undefined";
				})
				.map(function(view) {
					return view.viewName;
				});

		// 아직 생성되지 않은 view가 있는 경우
		if(missingViews.length > 0) {
			if (retryCount >= maxRetryCount) {
				console.error("View 초기화 실패:", missingViews);
				if (typeof _state.callback === "function") {
					_state.callback(false);
				}
				return;
			}

			setTimeout(function() {
				waitAndShowView(retryCount + 1, maxRetryCount);
			}, 100);
		}
		else {
			showView();
	
			if (typeof _state.callback === "function") {
				_state.callback(true);
			}
		}
	}
	
	function showView() {
	
		const view = getView();
		
		view.init({
			viewType: _state.currViewType, 
			renderCallback: listRenderCallback, 
			viewTypeCallback: viewTypeChangeCallback,
			..._state.customOptions  // 커스텀 옵션 전달 (selectionType 등)
		});

		view.show();
		
		// duallayout에서 view type 전환 시 left, right frame 간 비율을 자동 조정하기 위한 type 추가
		if ($("body").hasClass("dual-layout")) {
			window.parent.postMessage({
				type: PostMessageType.DUAL_LAYOUT_VIEW_TYPE_CHANGE,
				viewType: _state.currViewType
			}, window.location.origin);
		}
		
		// delay가 아닐 때만 getList() 호출
		if (!_state.delayInitialLoad) {
			view.getList();
		} else {
			console.log("[MagicIAM] delayInitialLoad가 true이므로 초기 렌더링을 대기합니다.");
		}
		
	}
	
	// 해당 목록의 지원하는 view 중 curr view를 찾아 return
	function getView() {
		return window[_state.supportView.find(function(item) {
			return item.type === _state.currViewType;
		}).viewName];
	}


	// ===============================================================================================
	// Public API
	// -----------------------------------------------------------------------------------------------
	// 아래 영역만 namespace를 통해 외부에 공개한다.
	// 구현 함수와 private 상수는 이 주석 위의 IIFE 내부 scope에 둔다.
	commonList.init = init;
	commonList.getSupportView = getSupportView;
	commonList.getCurrViewType = getCurrViewType;


})(window.MagicIAM_JSCommonList);
