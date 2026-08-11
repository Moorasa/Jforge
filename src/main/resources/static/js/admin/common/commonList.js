/* ===============================================================================================

Name : JWorks_JSCommonList.js

Description :
	JWORKS 프론트엔드 모듈 Table View에서 공통으로 사용하는 유틸리티 파일입니다.

Remarks :
	재배포를 금합니다.
	
=============================================================================================== */
window.JWorks_JSCommonList = window.JWorks_JSCommonList || {};
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
		JWorks_JSCommonSection.postMessageEventListener({
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
	
		// 기존 view 숨김. show() 와 같은 이유로 hide() 도 없을 수 있다 — 클래스 토글로 폴백.
		const prevView = getView();
		if (typeof prevView.hide === "function") {
			prevView.hide();
		} else {
			const prevSelector = VIEW_CONTAINER_SELECTOR[_state.currViewType];
			if (prevSelector) { $(prevSelector).removeClass("on"); }
		}

		_state.currViewType = type;

		waitAndShowView(0, 30);
		
	}
	
	// 뷰 타입 → 뷰 루트 컨테이너 선택자. 각 뷰 JSP 가 내는 **고정 id** 다
	// (module/tableView.ftl 등이 id="table-view" 를 리터럴로 산출 — 데이터 유입 없음).
	//
	// ⚠ 키는 반드시 ViewType 상수를 **그대로** 쓴다. 실제 값은 이름 문자열("TABLE")이 아니라
	//    공통코드("C4500001", constants.js)다 — 이름으로 하드코딩하면 매칭이 조용히 실패해
	//    컨테이너를 못 찾는다(실제로 그렇게 한 번 틀렸다). 코드값이 바뀌어도 여기가 따라간다.
	const VIEW_CONTAINER_SELECTOR = {};
	VIEW_CONTAINER_SELECTOR[ViewType.TABLE] = "#table-view";
	VIEW_CONTAINER_SELECTOR[ViewType.CARD]  = "#card-view";
	VIEW_CONTAINER_SELECTOR[ViewType.TREE]  = "#tree-view";
	VIEW_CONTAINER_SELECTOR[ViewType.FORM]  = "#form-view";

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

		// 뷰 **DOM** 도 함께 기다린다. 예전에는 view 객체만 기다려서, 뷰 조각(JSP include)이
		// 아직 안 붙은 시점에 showView() 가 돌고 $container 가 비어 초기화가 실패했다
		// (tableView.init 조기 return → show 없음). 객체와 DOM 은 준비 시점이 다르다.
		const viewSelector = VIEW_CONTAINER_SELECTOR[_state.currViewType];
		const missingDom = (viewSelector && $(viewSelector).length === 0) ? viewSelector : null;

		// 아직 준비되지 않은 view(객체 또는 DOM)가 있는 경우
		if(missingViews.length > 0 || missingDom) {
			if (retryCount >= maxRetryCount) {
				console.error("View 초기화 실패:", missingViews.length ? missingViews : missingDom);
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

		// 각 뷰의 init 은 $container 를 필수로 요구한다(없으면 조기 return → 이어지는
		// view.show() 가 TypeError). 예전에는 이 값을 아무도 넘기지 않아 **생성된 모든 목록
		// 화면**에서 뷰 초기화가 실패했다. 뷰 루트 id 는 고정이므로 여기서 결정해 넘긴다.
		// 호출측이 customOptions 로 $container 를 명시하면 그 값이 우선한다(스프레드가 뒤).
		const selector = VIEW_CONTAINER_SELECTOR[_state.currViewType];
		const $viewContainer = selector ? $(selector) : $();

		view.init({
			viewType: _state.currViewType,
			$container: $viewContainer,
			renderCallback: listRenderCallback,
			viewTypeCallback: viewTypeChangeCallback,
			..._state.customOptions  // 커스텀 옵션 전달 (selectionType 등)
		});

		// 뷰 표시. 뷰 구현들(tableView/cardView/treeView/formView)은 show()/hide() 를
		// **공개하지 않는다** — 공개 API 는 init/getList/setCurrPage 뿐이고, 표시 여부는
		// CSS 클래스 `.on` 이 정한다(section#table-view{display:none} / .on{display:block}).
		// 예전 코드는 없는 view.show() 를 불러 TypeError 로 초기화가 통째로 죽었다.
		// 구현이 show() 를 제공하면 그것을 우선 쓰고, 없으면 클래스 토글로 폴백한다.
		if (typeof view.show === "function") {
			view.show();
		} else {
			$viewContainer.addClass("on");
		}
		
		// duallayout에서 view type 전환 시 left, right frame 간 비율을 자동 조정하기 위한 type 추가
		if ($("body").hasClass("dual-layout")) {
			window.parent.postMessage({
				type: PostMessageType.DUAL_LAYOUT_VIEW_TYPE_CHANGE,
				viewType: _state.currViewType
			}, window.location.origin);
		}
		
		// delay가 아닐 때만 getList() 호출.
		// **다음 매크로태스크로 미룬다.** showView() 가 부르는 view.init() 에는 apiInfo 가 없다
		// (도메인 배선은 생성물 listXxxViewJs 의 별도 ready 핸들러가 채운다). 예전에는 여기서
		// 동기로 getList() 를 불러 배선 전에 조회가 나갔고, 생성된 **모든 목록 화면**이
		// getCurrentSearchData() 의 apiInfo.defaultSort 에서 TypeError 로 죽었다.
		if (!_state.delayInitialLoad) {
			setTimeout(function() {
				view.getList();
			}, 0);
		} else {
			console.log("[JWorks] delayInitialLoad가 true이므로 초기 렌더링을 대기합니다.");
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


})(window.JWorks_JSCommonList);
