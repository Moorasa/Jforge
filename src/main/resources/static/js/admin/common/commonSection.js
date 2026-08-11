/* ===============================================================================================

Name : JWorks_JSCommonSection.js

Description :
	JWORKS 프론트엔드 모듈 전반에서 공통으로 사용하는 유틸리티 파일입니다.

Remarks :
	재배포를 금합니다.
	
=============================================================================================== */
window.JWorks_JSCommonSection = window.JWorks_JSCommonSection || {};
(function(commonSection){
	"use strict";

	if (commonSection.__defined) {
		return;
	}
	commonSection.__defined = true;


	/* ===============================================================================================
	postMessage 관련
	=============================================================================================== */
	commonSection.postMessageEventListener = function(info) {
		window.addEventListener("message", function(event) {
			if (window.location.origin === event.origin) {
				// pass
			}
			else {
				console.log("error : not same origin");
				return;
			}
	
			const data = event.data;
			if (data.type === PostMessageType.SET_HEIGHT && typeof data.height === "number" && data.height > 0) {
				if($("body").hasClass("dual-layout"))
					;
				else
					if (info && info.$containerToSetHeight && info.$containerToSetHeight.length) {
						info.$containerToSetHeight.css({ "min-height": data.height + "px", "height": data.height + "px" });
					}
			}
			// duallayout에서 view type 전환 시 left, right frame 간 비율을 자동 조정하기 위한 type 추가
			else if (data.type === PostMessageType.DUAL_LAYOUT_VIEW_TYPE_CHANGE) {
				if($("body").hasClass("dual-layout")) {
					commonSection.setDualLayoutRatioByViewType(data.viewType);
				} else {
					;
				}
			}
			else if (data.type === PostMessageType.CREATE_SNACKBAR) {
				JWORKS_JSSnackBar.create(data.message);
			}
			else if (data.type === PostMessageType.CLOSE_MY_POPUP) {
				$("#header .header-my-menu").trigger("click");
			}
			else if (data.type === PostMessageType.SESSION_RENEW) {
				JWorks_JSSessionTimer.resetTimer();
			}
			else if (data.type === PostMessageType.MY_PROFILE_SET_HEIGHT) {
				$('.myPopup-overlay .tab-content #myTabFrame').css("height", data.height + "px");
			}
			else if (data.type === PostMessageType.SYNC_VIEW_STATE) {
				// TODO: frame간 상태 동기화가 필요한 경우 모두 사용 가능하도록 개선
				// right > top > left leload 상태 동기화를 위해 공통 key로 targetFrameId를 관리
				// top은 이번 변경이 삭제인지 수정인지 구분할 1회성 action만 덧붙임
				const state = JWorks_JSCommonUtils.getSessionState("dualLayoutFrameState");
				state.targetFrameId = data.targetFrameId;
				state.action = data.action || "";
				JWorks_JSCommonUtils.setSessionState("dualLayoutFrameState", state);

				const targetFrame = data.targetFrameId ? window.document.getElementById(data.targetFrameId) : null;
				if (targetFrame && targetFrame.contentWindow && data.targetUrl) {
					targetFrame.contentWindow.location.replace(data.targetUrl);
				}
			}
			else {
				console.log("unreachable : known post message");
			}
		});
	}


	/* ===============================================================================================
	basic-info section
	=============================================================================================== */
	commonSection.registEventBasicInfo = function(info) {
		
		// 화면 스크롤에 따른 기본 정보 접기 (260223: HUG 요청으로 주석 처리)
		/*
		$(window).on('scroll', function() {
			
			// ROOT 노드가 아닐때만 동작하도록 조건 추가
			if ($("#basic-info").hasClass("view-mode") && !$("#basic-info").hasClass("static-root")) {
				if ($(window).scrollTop() > 100) {
					$("#basic-info .button-detail-collapse").trigger("click");
				} else {
					$("#basic-info .button-detail-expand").trigger("click");
				}
			}

			else if($("#basic-info").hasClass("edit-mode")) {
				// no action
			}
			else {
				console.log("unreachable");
			}
		});
		*/

		// 상세 보기 접기 클릭
		$("#basic-info .button-detail-collapse").on("click", function() {
			$("#basic-info").addClass("collapse");
		});
		// 상세 보기 펼치기 클릭
		$("#basic-info .button-detail-expand").on("click", function() {
			$("#basic-info").removeClass("collapse");
		});
		
		// default 설정 : 상세 보기 접기
		if(Constants.DEFAULT_BASIC_INFO_COLLAPSE === "Y")
			$("#basic-info").removeClass("collapse").addClass("collapse");
		else
			$("#basic-info").removeClass("collapse");
		
		// 수정하기 버튼 클릭
		$("#basic-info .buttons .update").on("click", function() {
			$("#basic-info").removeClass("view-mode").addClass("edit-mode");
			if (info.editCallback && typeof info.editCallback === "function") {
				info.editCallback();
			}
		});
		// 삭제하기 버튼 클릭
		$("#basic-info .buttons .delete").on("click", info.deleteCallback);

		// 수정 취소
		$("#basic-info .buttons-edit .cancel").on("click", function() {
			$("#basic-info").removeClass("edit-mode").addClass("view-mode");
			info.cancelCallback();
		});
		
		// 수정 저장
		$("#basic-info .buttons-edit .save").on("click", info.saveCallback);
	
		// 클리어 버튼
		JWorks_JSCommonUtils.registEventClearableInput({
			$container: $("section#basic-info .detail-info-edit .layout-column") 
		});
	
	}
	
	/* ===============================================================================================
	basic-info section > attribute
	=============================================================================================== */
	commonSection.handleBasicInfoAttribute = {

		// 1. 보기 모드 렌더링
		renderView: function(info) {
			const $attrContainer = info.$container || $(".attribute-chip-container");
			const attributeData = info.data;

			$attrContainer.empty();

			if (attributeData && attributeData !== "{}") {
				const attributes = JSON.parse(attributeData);

				if (Object.keys(attributes).length === 0) {
					$attrContainer.html('<span>-</span>');
					return;
				}

				for (const key in attributes) {
					const value = attributes[key];

					let fontClass = "font-body-01";
					if($("body").hasClass("dual-layout"))
						fontClass = "font-body-03";
					// Chip HTML 생성
					const chipHtml = `
	                        <div class="chip" title="${key} : ${value}">
	                            <span class="key ${fontClass}">${key}</span>
	                            <span class="value ${fontClass}">${value ? value : '-'}</span>
	                        </div>
	                    `;
					$attrContainer.append(chipHtml);
				}
			} else {
				$attrContainer.html('<span>-</span>');
			}
		}
	};

	/* ===============================================================================================
	associate-info section
	=============================================================================================== */
	/** TB_IAM_SETTING 탭 노출 코드 — ClientSettings 와 동일 (C34: C3400 + 숫자 2자리 이상) */
	const DETAIL_TAB_VISIBILITY_CLASS_RE = /^C3400\d{2,}$/;

	function resolveDetailTabSettingKeyFromClass($el) {
		const cls = ($el.attr("class") || "").trim();
		if (!cls) {
			return "";
		}
		const tokens = cls.split(/\s+/);
		for (let i = 0; i < tokens.length; i++) {
			if (DETAIL_TAB_VISIBILITY_CLASS_RE.test(tokens[i])) {
				return tokens[i];
			}
		}
		return "";
	}

	/**
	 * 납품처 설정(TAB_VISIBILITY)에 따라 associate 탭·iframe 을 제거한다.
	 * <p>확장 규칙(타 화면 추후 적용): 각 {@code .tabs .tab} 과 짝이 되는 {@code .contents iframe} 의 {@code class} 에
	 * 동일한 TB_IAM_SETTING KEY_CODE 토큰(예: {@code C3400119})을 넣는다. 기존 tabClass 등과 공존 가능.
	 * 페이지 {@code ready} 에서 {@code registEventAssociateInfo} 보다 먼저
	 * {@code JWorks_JSCommonSection.applyAssociateTabsVisibilityByClass($("section#associate-info"))} 호출.
	 * <p>현재 JSP 반영·검증 범위: 권한 위임 상세만.
	 * @param $container jQuery (기본: section#associate-info)
	 */
	commonSection.applyAssociateTabsVisibilityByClass = function($container) {
		const $c = $container && $container.length ? $container : $("section#associate-info");
		if (!$c.length || typeof Constants === "undefined" || !Constants.TAB_VISIBILITY) {
			return;
		}
		$c.find(".tabs .tab").each(function() {
			const $tab = $(this);
			const key = resolveDetailTabSettingKeyFromClass($tab);
			if (!key) {
				return;
			}
			if (Constants.TAB_VISIBILITY[key] !== false) {
				return;
			}
			$c.find(".contents iframe").filter(function() {
				return $(this).hasClass(key);
			}).remove();
			$tab.remove();
		});
		if ($c.find(".tabs .tab").length === 0) {
			$c.remove();
		}
	};

	commonSection.registEventAssociateInfo = function(info) {
		if (!info || !info.$container || !info.$container.length) {
			return;
		}

		// 탭이 존재하는 경우
		if(info.tabs != null) {
			
			// 탭이 존재하는 경우 좌상 모서리 라운드 제거
			info.$container.removeClass("with-tab").addClass("with-tab");
			
			// 탭이 존재하는 경우 클릭 이벤트 처리 등록
			info.$container.on("click", ".tabs .tab", function() {
			
				// 클릭한 tab 정보 저장
				const tabClass = $(this).attr('class')
					.split(' ')
					.map(c => c.trim()) // 공백 제거
					.filter(c => c !== 'tab' && c !== 'on') // 공통 클래스 제외
					.join(' ');
				JWorks_JSCommonUtils.setTabCookie("associateInfo-selectedTab", tabClass);
				
				// 기본 정보 영역이 있는 경우 접기 + 보기 모드 전환 (수정 모드에서 탭 이동 시 버튼이 남아있는 문제 수정)
				$("section#basic-info").length > 0 && $("section#basic-info").removeClass("edit-mode").addClass("view-mode");
//				$("section#basic-info").length > 0 && $("section#basic-info").removeClass("edit-mode").addClass("view-mode collapse");
				
				// 모든 tab의 on 삭제
				info.$container.find(".tab").removeClass("on");
				// 선택한 tab의 on 추가
				$(this).addClass("on");
/***
TODO 
로딩과 상관없이 탭을 클릭시 로드하도록 변경
사유 : back button을 이용하는 등의 페이지 실패가 발생했을때 사용자 편의성이 떨어짐 
좀더 나은 프로세스 개선 필요
=> 로드하지 않도록 재 변경
frame 내부의 페이지 이동시 location.replace()를 이용해서 history를 쌓지 않도록 개선
=> 다시 로드하도록 재 변경
frame 내부에서 변경이 일어났을때 다른 탭에도 반영이 되어야 하는 경우가 있으므로 무조건 갱신이 필요
				// 로딩된 경우
				if($(this).hasClass("load")) {
				}
				// 로딩되지 않은 경우
				else 
				{
					// 해당 tab에 load 추가
					$(this).addClass("load");
***/
					loadTabResource(info, $(this));
/***
				}
***/
			
				switchTab(info, $(this));
			});
		}
	}
	function loadTabResource(info, $selectedTab) {
		// 선택된 tab에 해당하는 object 찾기
		const tab = info.tabs.find(tab => $selectedTab.hasClass(tab.tabClass));

		const iframeEl = document.getElementById(tab.frameId);
		if (iframeEl && iframeEl.contentWindow) {
			iframeEl.contentWindow.location.replace(tab.location);
		}
	}
	function switchTab(info, $selectedTab) {
		// 선택된 tab에 해당하는 object 찾기
		const tab = info.tabs.find(tab => $selectedTab.hasClass(tab.tabClass));

		const $frames = info.$container.find("iframe");

		// 모든 frame의 on 삭제
		$frames.removeClass("on");
		// 해당 frame의 on 추가
		$frames.filter("." + tab.tabClass).addClass("on");
	}


	/* ===============================================================================================
	dual-layout-area section
	=============================================================================================== */
	// 2단 레이아웃: view type 변경 시 각 레이아웃 width 자동 조정 기능 이벤트
	commonSection.setDualLayoutRatioByViewType = function(viewType) {
		const $left = $('#dual-layout-area .layout-left');
		const $right = $('#dual-layout-area .layout-right');
		if (!$left.length || !$right.length) {
			return;
		}

		// TODO: 해당 설정도 고객사별로 다를 수 있으니 DB > js-constants.jsp를 통해 관리
		let leftRatio = Constants.DUAL_LAYOUT_TABLE_VIDE_DEFAULT_LEFT_FLEX;
		let rightRatio = Constants.DUAL_LAYOUT_TABLE_VIDE_DEFAULT_RIGHT_FLEX;

		if (viewType == ViewType.TREE) {
			leftRatio = Constants.DUAL_LAYOUT_TREE_VIDE_DEFAULT_LEFT_FLEX;
			rightRatio = Constants.DUAL_LAYOUT_TREE_VIDE_DEFAULT_RIGHT_FLEX;
		}
		else if (viewType == ViewType.CARD) {
			leftRatio = Constants.DUAL_LAYOUT_CARD_VIDE_DEFAULT_LEFT_FLEX;
			rightRatio = Constants.DUAL_LAYOUT_CARD_VIDE_DEFAULT_RIGHT_FLEX;
		}
		else if (viewType == ViewType.TABLE) {
			leftRatio = Constants.DUAL_LAYOUT_TABLE_VIDE_DEFAULT_LEFT_FLEX;
			rightRatio = Constants.DUAL_LAYOUT_TABLE_VIDE_DEFAULT_RIGHT_FLEX;
		}
		else if (viewType == ViewType.FORM) {
			leftRatio = Constants.DUAL_LAYOUT_TABLE_VIDE_DEFAULT_LEFT_FLEX;
			rightRatio = Constants.DUAL_LAYOUT_TABLE_VIDE_DEFAULT_RIGHT_FLEX;
		}

		// resizer를 고려해 left/right의 width 초기화와 left flex: 1 재설정
		$left.css({ "width": "", "flex": `${leftRatio}` });
		$right.css({ "width": "", "flex": `${rightRatio}` });
	};

	// 2단 레이아웃 : 각 레이아웃 width 조절 기능 이벤트 등록
	$(function() {
		const $resizer = $('#dual-layout-area .layout-middle.resizer');
		const $resizerBar = $('#dual-layout-area .layout-middle.resizer .resizer-bar');
		const $collapseLeft = $('#dual-layout-area .layout-middle.resizer .collapse-left');
		const $collapseRight = $('#dual-layout-area .layout-middle.resizer .collapse-right');
		const $expand = $('#dual-layout-area .layout-middle.resizer .expand');
		const $left = $('#dual-layout-area .layout-left');
		const $right = $('#dual-layout-area .layout-right');
		const $container = $resizer.parent();
		// 2026-06-30 수정: containerW를 로드 시 1회 캐싱하던 것을 제거.
		// 로드 이후 창 크기/좌측 메뉴 폭이 바뀌면 캐싱 값이 실제 영역 폭과 달라져
		// 드래그 시 좌우 폭 합이 어긋나고, justify-content:space-between 때문에
		// 가운데가 벌어지는 문제가 있었음. → mousemove에서 실시간 측정하도록 변경.
		const resizerW = $resizer.outerWidth();
	
		let isDragging = false;
		let startX = 0;
		let startWidth = 0;
	
		// 좌측 접기
		$collapseLeft.on('click', function(e) {
			$left.hide();
			$collapseLeft.hide();
			$collapseRight.hide();
			$expand.show();
			$resizerBar.hide();
			$right.css("flex", "1");
		});

		// 우측 접기
		$collapseRight.on('click', function(e) {
			$right.hide();
			$collapseLeft.hide();
			$collapseRight.hide();
			$expand.show();
			$resizerBar.hide();
			$left.css("flex", "1");
		});

		// 펼치기
		$expand.on('click', function(e) {
			const window = $(document).find("#dual-layout-area .layout-left > iframe").get(0).contentWindow;
			const currViewType = window.JWorks_JSCommonList.getCurrViewType();
			if($left[0].style.width) {
				$left.css("flex", "0 0 auto");
			}
			else {
				if(currViewType === ViewType.TABLE)
					$left.css("flex", `${Constants.DUAL_LAYOUT_TABLE_VIDE_DEFAULT_LEFT_FLEX}`);
				if(currViewType === ViewType.CARD)
					$left.css("flex", `${Constants.DUAL_LAYOUT_CARD_VIDE_DEFAULT_LEFT_FLEX}`);
				if(currViewType === ViewType.TREE)
					$left.css("flex", `${Constants.DUAL_LAYOUT_TREE_VIDE_DEFAULT_LEFT_FLEX}`);
				else
					$left.css("flex", `${Constants.DUAL_LAYOUT_TABLE_VIDE_DEFAULT_LEFT_FLEX}`);
			}
			if($right[0].style.width) {
				$right.css("flex", "0 0 auto");
			}
			else {
				if(currViewType === ViewType.TABLE)
					$right.css("flex", `${Constants.DUAL_LAYOUT_TABLE_VIDE_DEFAULT_RIGHT_FLEX}`);
				if(currViewType === ViewType.CARD)
					$right.css("flex", `${Constants.DUAL_LAYOUT_CARD_VIDE_DEFAULT_RIGHT_FLEX}`);
				if(currViewType === ViewType.TREE)
					$right.css("flex", `${Constants.DUAL_LAYOUT_TREE_VIDE_DEFAULT_RIGHT_FLEX}`);
				else
					$right.css("flex", `${Constants.DUAL_LAYOUT_TABLE_VIDE_DEFAULT_RIGHT_FLEX}`);
			}
			$left.show();
			$right.show();
			$collapseLeft.show();
			$collapseRight.show();
			$expand.hide();
			$resizerBar.show();
		});

		// 드래그 시작
		$resizerBar.on('mousedown', function(e) {
			isDragging = true;
			startX = e.clientX;
			startWidth = $left.width();
	
			// 드래그 중 텍스트 선택 방지
			$('body').css('user-select', 'none');
	
			$left.css("width", "" + $left.outerWidth() + "px");
			$left.css("flex", "none");
			$left.find("iframe").css("display", "none");
			$right.css("width", "" + $right.outerWidth() + "px");
			$right.css("flex", "none");
			$right.find("iframe").css("display", "none");
	
		});
	
		// 드래그 중
		$(document).on('mousemove', function(e) {
			if (!isDragging) return;

			// 2026-06-30 수정: 로드 시 캐싱한 containerW 대신 드래그 시점의 실제 폭을 측정.
			// (캐싱 값과 실제 영역 폭이 다르면 좌우 합이 어긋나 가운데가 벌어지던 문제 수정)
			const currContainerW = $container.width();
			const delta = e.clientX - startX;
			const minWidth = currContainerW * 0.25;
			const maxWidth = currContainerW * 0.75;
			const newLeftWidth = Math.min(Math.max(startWidth + delta, minWidth), maxWidth);
			const newRightWidth = currContainerW - resizerW - newLeftWidth;

			$left.width(newLeftWidth);
			$right.width(newRightWidth);
		});
	
		// 드래그 종료
		$(document).on('mouseup', function() {
			isDragging = false;
			$left.find("iframe").css("display", "block");
			$right.find("iframe").css("display", "block");
			$('body').css('user-select', '');
		});

		// 2026-06-30 추가: 창 크기 변경 시 좌우 패널을 기본 flex 비율로 환원.
		// 드래그로 좌우 폭이 px(flex:none)로 고정된 상태에서 창/좌측 메뉴 폭이 바뀌면
		// 폭 합이 영역보다 작아져 가운데가 벌어지므로, resize 시 비율 기반으로 되돌림.
		let resizeTimer;
		$(window).on('resize', function() {
			clearTimeout(resizeTimer);
			resizeTimer = setTimeout(function() {
				if (isDragging) return;
				// 한쪽 접힘(expand 버튼 노출) 상태는 사용자가 의도한 것이므로 건드리지 않음
				if ($expand.is(':visible')) return;

				const leftIframe = $('#dual-layout-area .layout-left > iframe').get(0);
				const win = leftIframe ? leftIframe.contentWindow : null;
				const viewType = (win && win.JWorks_JSCommonList)
					? win.JWorks_JSCommonList.getCurrViewType()
					: null;
				commonSection.setDualLayoutRatioByViewType(viewType);
			}, 150);
		});
	});


})(window.JWorks_JSCommonSection);


window.JWorks_JSSessionTimer = window.JWorks_JSSessionTimer || {};
(function(sessionTimer) {
	"use strict";

	if (sessionTimer.__defined) {
		return;
	}
	sessionTimer.__defined = true;


	// 세션 timeout 시간 - 단위 : 초
	// 2026-04-27 세션정책변경: 기본 세션 타임아웃 600초(10분) → 3600초(1시간)
	// let sessionTimeout = 600;
	let sessionTimeout = 3600;
	// 타이머 ID
	let timerId;
	// 경과 시간
	let elapsedSeconds = sessionTimeout;
	// 타이머 동작 유무
	let isRunning = false;
	// 타이머를 display할 container
	let $container = null;


	function formatTime(seconds) {
		const hrs = String(Math.floor(seconds / 3600)).padStart(2, '0');
		const mins = String(Math.floor((seconds % 3600) / 60)).padStart(2, '0');
		const secs = String(seconds % 60).padStart(2, '0');
		// 시간은 생략
		//return `${hrs}:${mins}:${secs}`;
		return `${mins}:${secs}`;
	}

	function updateDisplay() {
		$container.text(formatTime(elapsedSeconds));
	}

	sessionTimer.setOption = function(option) {
		if (option.hasOwnProperty("sessionTimeout")) {
			sessionTimeout = option.sessionTimeout;
			elapsedSeconds = option.sessionTimeout;
		}
		if (option.hasOwnProperty("container"))
			$container = option.container;
	}

	sessionTimer.startTimer = function() {
		if (isRunning) return;
		isRunning = true;
		timerId = setInterval(() => {
			if (elapsedSeconds > 0)
				elapsedSeconds--;
			else {
				elapsedSeconds = 0;
				clearInterval(timerId);
				JWORKS_JSAlert.start("Session Timeout"
					, `세션 타임아웃 시간이 경과하여 로그아웃 되었습니다.<br/>다시 로그인 해 주세요.`
					, function() {
						window.top.location.href = "/iam/v2/admin/view/login/iamServerLogout"
					}
				);
			}
			updateDisplay();
		}, 1000);
	}

	sessionTimer.resetTimer = function() {
		console.log("resetTimer");
		elapsedSeconds = sessionTimeout;
	}

	// 타이머 정지 기능은 보류
	/***
	sessionTimer.pauseTimer = function() {
		isRunning = false;
		clearInterval(timerId);
	}
	***/

	// 타이머 리셋 기능은 보류
	/***
	function resetSessionTimer() {
		pauseTimer();
		elapsedSeconds = sessionTimeout;
		updateDisplay();
	}
	***/


})(window.JWorks_JSSessionTimer);
