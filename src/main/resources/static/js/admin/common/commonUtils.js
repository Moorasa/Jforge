/* ===============================================================================================

Name : MagicIAM_JSCommonUtils.js

Description :
	JWORKS 프론트엔드 모듈 전반에서 공통으로 사용하는 유틸리티 파일입니다.

Remarks :
	재배포를 금합니다.
	
=============================================================================================== */
window.MagicIAM_JSCommonUtils = window.MagicIAM_JSCommonUtils || {};
(function(commonUtils) {
	"use strict";

	if (commonUtils.__defined) {
		return;
	}
	commonUtils.__defined = true;


	/**
	 * iframe 내 테이블뷰의 선택 변경 상태를 감지하고, 부모 창으로 이벤트를 발생시키는 함수
	 * @param {jQuery} $container - 상태를 확인할 테이블의 컨테이너 jQuery 객체
	 */
	commonUtils.updateSelectionState = function($container) {
		// 라디오 버튼 확인
		const $radio = $container.find(".row-radio:checked");
		const $checkbox = $container.find(".row-checkbox:checked");
		
		let hasChecked = false;
		
		if ($radio.length > 0) {
			hasChecked = true; // 라디오는 단일 선택
		} else if ($checkbox.length > 0) {
			hasChecked = $checkbox.length > 0;
		}
		
		// 이벤트를 '부모 창(팝업)'으로 보냄
		window.parent.dispatchEvent(new CustomEvent('selectionChange', {
			detail: { hasChecked: hasChecked }
		}));
	};

	/**
	 * 버튼의 상태를 제어하는 범용 함수 (지정된 클래스 교체 방식)
	 * @param {jQuery} $button - 제어할 버튼의 jQuery 객체 (e.g., '#addResource')
	 * @param {boolean} isEnabled - 버튼을 활성화할지 여부 (true: 활성화, false: 비활성화)
	 */
	commonUtils.toggleButtonState = function($button, isEnabled) {
		if ($button.length === 0) {
			console.warn("toggleButtonState: 버튼을 찾을 수 없습니다");
			return;
		}
		
		$button.each(function() {
			const $this = $(this); // 현재 처리 중인 버튼 하나
			
			// 1. 행동 제어: 'disabled' 속성 설정 (기능적 제어)
			$button.prop('disabled', !isEnabled);
			
			// 2. 모양 제어: CSS 클래스 교체 (시각적 제어)
			const styleMap = [
				{
					active: 'basic-button-28-line-sub3-500',
					disabled: 'basic-button-28-line-sub3-disable'
				},
				{
					active: 'basic-button-28-line-sub4-500',
					disabled: 'basic-button-28-line-sub4-disable'
				},
				{
					active: 'basic-button-28-solid-sub3-500',
					disabled: 'basic-button-28-solid-sub3-disable'
				},
				{
					active: 'basic-button-28-solid-sub4-500',
					disabled: 'basic-button-28-solid-sub4-disable'
				},
				{
					active: 'basic-button-36-line-sub3-500',
					disabled: 'basic-button-36-line-sub3-disable'
				},
				{
					active: 'basic-button-36-line-sub4-500',
					disabled: 'basic-button-36-line-sub4-disable'
				},
				{
					active: 'basic-button-36-solid-sub3-500',
					disabled: 'basic-button-36-solid-sub3-disable'
				},
				{
					active: 'basic-button-36-solid-sub4-500',
					disabled: 'basic-button-36-solid-sub4-disable'
				},
				{
					active: 'basic-button-48-line-sub3-500',
					disabled: 'basic-button-48-line-sub3-disable'
				},
				{
					active: 'basic-button-48-line-sub4-500',
					disabled: 'basic-button-48-line-sub4-disable'
				},
				{
					active: 'basic-button-48-solid-sub3-500',
					disabled: 'basic-button-48-solid-sub3-disable'
				},
				{
					active: 'basic-button-48-solid-sub4-500',
					disabled: 'basic-button-48-solid-sub4-disable'
				},
				{
					active: 'basic-button-60-line-sub3-500',
					disabled: 'basic-button-60-line-sub3-disable'
				},
				{
					active: 'basic-button-60-line-sub4-500',
					disabled: 'basic-button-60-line-sub4-disable'
				},
				{
					active: 'basic-button-60-solid-sub3-500',
					disabled: 'basic-button-60-solid-sub3-disable'
				},
				{
					active: 'basic-button-60-solid-sub4-500',
					disabled: 'basic-button-60-solid-sub4-disable'
				}
			];
			
			$.each(styleMap, function(index, style) {
				if ($this.hasClass(style.active) || $this.hasClass(style.disabled)) {
					if (isEnabled) {
						// 활성화: disable 빼고 active 넣기
						$this.removeClass(style.disabled).addClass(style.active);
					} else {
						// 비활성화: active 빼고 disable 넣기
						$this.removeClass(style.active).addClass(style.disabled);
					}
					return false;
				}
			});
		});
	};

	/* ===============================================================================================
	Clearable Input 관련
	=============================================================================================== */
	commonUtils.registEventClearableInput = function(info) {
		info.$container
			.off("mouseover", ".input-wrapper.clearable")
			.on("mouseover", ".input-wrapper.clearable", function() {
			$(this).find(".clear-btn").css("visibility", "visible");
		});
		info.$container
			.off("mouseout", ".input-wrapper.clearable")
			.on("mouseout", ".input-wrapper.clearable", function() {
			$(this).find(".clear-btn").css("visibility", "hidden");
		});
		info.$container
			.off("click", ".input-wrapper.clearable .clear-btn")
			.on("click", ".input-wrapper.clearable .clear-btn", function() {
			const $input = $(this).closest('.input-wrapper.clearable').find("input");
			$input?.val("").trigger("input").focus(); // 버튼 비활성화/활성화 트리거
			const $textarea = $(this).closest('.input-wrapper.clearable').find("textarea");
			$textarea?.val("").trigger("input").focus(); // 버튼 비활성화/활성화 트리거
		});
	}


	/* ===============================================================================================
	Tab 관련
	=============================================================================================== */
	// 브라우저 tab 별로 다른 값을 저장하기 위해 tab의 id를 생성하여 return
	function getTabId() {
		let tabId = sessionStorage.getItem('tabId');
		if (tabId) {
			console.log('기존 탭 ID:', sessionStorage.getItem('tabId'));
		} else {
			tabId = 'tab_' + Math.random().toString(36).substring(2, 11);
			sessionStorage.setItem('tabId', tabId);
			console.log('새로운 탭 ID 생성:', tabId);
		}
		return tabId;
	}
	function loadTabResource(info) {
		// 선택된 tab에 해당하는 object 찾기
		const tab = info.tabs.find(tab => info.$selectedTab.hasClass(tab.tabClass));

		const iframeEl = document.getElementById(tab.frameId);
		if (iframeEl && iframeEl.contentWindow) {
			iframeEl.contentWindow.location.replace(tab.location);
		}
	}
	function switchTab(info) {
		// 선택된 tab에 해당하는 object 찾기
		const tab = info.tabs.find(tab => info.$selectedTab.hasClass(tab.tabClass));

		const $frames = info.$container.find("iframe");

		// 모든 frame의 on 삭제
		$frames.removeClass("on");
		// 해당 frame의 on 추가
		$frames.filter("." + tab.tabClass).addClass("on");
	}
	commonUtils.tabClick = function(info) {
		// 모든 tab의 on 삭제
		info.$container.find(".tab").removeClass("on");
		// 선택한 tab의 on 추가
		info.$selectedTab.addClass("on");
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
		if(info.$selectedTab.hasClass("load")) {
		}
		// 로딩되지 않은 경우
		else 
		{
			// 해당 tab에 load 추가
			info.$selectedTab.addClass("load");
***/
			loadTabResource(info);
/***
		}
***/
		
		switchTab(info);
	}


	/* ===============================================================================================
	cookie 관련
	=============================================================================================== */
	commonUtils.getCookie = function(name) {
		const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
		return match ? decodeURIComponent(match[1]) : null;
	}

	commonUtils.setCookie = function(name, value) {
		document.cookie = name + "=" + encodeURIComponent(value) + "; path=/";
	}

	commonUtils.getTabCookie = function(name) {
		const match = document.cookie.match(new RegExp('(?:^|; )' + getTabId() + '_' + name + '=([^;]*)'));
		return match ? decodeURIComponent(match[1]) : null;
	}

	commonUtils.setTabCookie = function(name, value) {
		document.cookie = getTabId() + '_' + name + "=" + encodeURIComponent(value) + "; path=/";
	}


	/* ===============================================================================================
	문자열 validation 관련
	=============================================================================================== */
	// 숫자 허용
	commonUtils.stringFilterNumber = function(value) {
	  return value.replace(/[^0-9]/g, '');
	}
	// 영문 허용
	commonUtils.stringFilterEnglish = function(value) {
	  return value.replace(/[^a-zA-Z]/g, '');
	}
	// 영문,숫자 허용
	commonUtils.stringFilterEnglishNumber = function(value) {
	  return value.replace(/[^a-zA-Z0-9]/g, '');
	}
	// 영문,숫자,_ 허용
	commonUtils.stringFilterEnglishNumberUnderscore = function(value) {
	  return value.replace(/[^a-zA-Z0-9_]/g, '');
	}
	// 영문,숫자,_,- 허용
	commonUtils.stringFilterEnglishNumberUnderscoreHyphen = function(value) {
	  return value.replace(/[^a-zA-Z0-9_-]/g, '');
	}
	// 영문,숫자,특수문자 허용
	commonUtils.stringFilterEnglishNumberSpecialcharacter = function(value) {
	  return value.replace(/[^a-zA-Z0-9!@#$%^&*(),.?":{}|<>`~[\]\\\/;'_+=\-]/g, '');
	}
	// 영문,한글 허용
	commonUtils.stringFilterEnglishKorean = function(value) {
	  return value.replace(/[^a-zA-Z가-힣]/g, '');
	}
	// 영문,한글,숫자 허용
	commonUtils.stringFilterEnglishKoreanNumber = function(value) {
	  return value.replace(/[^a-zA-Z가-힣0-9]/g, '');
	}
	// 영문,한글,숫자,_ 허용
	commonUtils.stringFilterEnglishKoreanNumberUnderscore = function(value) {
	  return value.replace(/[^a-zA-Z가-힣0-9_]/g, '');
	}
	// 영문,한글,숫자,_,- 허용
	commonUtils.stringFilterEnglishKoreanNumberUnderscoreHyphen = function(value) {
	  return value.replace(/[^a-zA-Z가-힣0-9_-]/g, '');
	}
	// 영문,한글,숫자,특수문자 허용
	commonUtils.stringFilterEnglishKoreanNumberSpecialcharacter = function(value) {
	  return value.replace(/[^a-zA-Z가-힣0-9!@#$%^&*(),.?":{}|<>`~[\]\\\/;'_+=\-]/g, '');
	}


	/* ===============================================================================================
	network 관련
	=============================================================================================== */
	commonUtils.sendPost = function(url, params, target) {
		let form = document.createElement("form");
		form.setAttribute("method", "post");
		form.setAttribute("action", url);
		if(target === undefined)
			;
		else
			form.setAttribute("target", target);

		document.charset = "utf-8";
		
		for(var key in params) {
			var hiddenInput = document.createElement("input");
			hiddenInput.setAttribute("type", "hidden");
			hiddenInput.setAttribute("name", key);
			hiddenInput.setAttribute("value", params[key]);
			form.appendChild(hiddenInput);
		}
		document.body.appendChild(form);
		form.submit();
	}
	
	/* ===============================================================================================
	속성 key 유효성 검사
	=============================================================================================== */
	commonUtils.getAttributeData = function(info) {
		if (!info || !info.$container || info.$container.length === 0) {
			console.error("CommonUtils: $container 옵션이 누락되었거나 요소를 찾을 수 없습니다.");
			return null;
		}

		const $container = info.$container;

		let attribute = {};
		let keys = [];
		let isValid = true;

		$container.find('.attribute-row').each(function() {
			const $row = $(this);
			const key = $row.find(".attr-key").val().trim();
			const value = $row.find(".attr-value").val();

			// [Guard Clause] 둘 다 비어있으면 건너뜀 (유효성 실패 아님)
			if (key === '' && value === '') {
				return true; // continue
			}

			// [Case 1] Key 없이 Value만 있는 경우
			if (key === '' && value !== '') {
				JWORKS_JSAlert.start("", "속성값이 입력된 항목에 속성명이 비어있습니다.", function() {
					$row.find(".attr-key").focus();
				});
				isValid = false;
				return false;
			}

			// [Case 2] Key 형식 검사
			const filteredKey = MagicIAM_JSCommonUtils.stringFilterEnglishKoreanNumberUnderscoreHyphen(key);
			if (key !== filteredKey) {
				JWORKS_JSAlert.start("", "속성명은 다음 문자만 허용됩니다 : 영문, 한글, 숫자, '_', '-'", function() {
					$row.find(".attr-key").focus();
				});
				isValid = false;
				return false;
			}

			// [Case 3] 중복 Key 검사
			if (keys.indexOf(key) > -1) {
				JWORKS_JSAlert.start("", "중복된 속성명이 있습니다.", function() {
					$row.find(".attr-key").focus();
				});
				isValid = false;
				return false;
			}

			keys.push(key);
			attribute[key] = value;
		});

		// 3. 결과 반환
		return isValid ? attribute : null;
	};
	
	/* ===============================================================================================
	화면 이동 시 목적지 타입
	=============================================================================================== */
//	commonUtils.jump = (jumpType, jumpParams) => {
	commonUtils.jump = function(jumpType, jumpParams) {
		// 1. 라우팅 테이블
		const jumpMap = {
			'C3300001': '/iam/v2/admin/view/user/user',
			'C3300002': '/iam/v2/admin/view/user/group',
			'C3300003': '/iam/v2/admin/view/user/dept',
			'C3300004': '/iam/v2/admin/view/role/role',
			'C3300005': '/iam/v2/admin/view/role/group',
			'C3300006': '/iam/v2/admin/view/resource/resource'
		};

		const targetUrl = jumpMap[jumpType];

		// 2. 유효성 검증
		if (!targetUrl) {
			console.error(`[MagicIAM Jump Error] 정의되지 않은 jumpType 입니다: ${jumpType}`);
			return;
		}

		// 3. 점프 파라미터가 존재할 경우 쿠키 세팅
		if (jumpParams) {
			commonUtils.setTabCookie('jumpData', JSON.stringify(jumpParams));
		}

		location.href = targetUrl;
	};

	/* ===============================================================================================
	임시저장/공통 변환 유틸
	=============================================================================================== */
	// 날짜 입력(yyyy-MM-dd)을 저장용 YYYYMMDD로 변환
	commonUtils.toYmd8 = function(isoDate) {
		if (isoDate == null || String(isoDate).trim() === "") {
			return null;
		}
		const s = String(isoDate).trim();
		if (/^\d{8}$/.test(s)) {
			return s;
		}
		const m = s.match(/^(\d{4})-(\d{2})-(\d{2})/);
		if (!m) {
			return null;
		}
		return m[1] + m[2] + m[3];
	};

	// attribute 객체를 저장 가능한 문자열(JSON)로 변환
	commonUtils.stringifyAttribute = function(obj) {
		if (obj == null) {
			return null;
		}
		if (typeof obj === "string") {
			return obj;
		}
		try {
			return JSON.stringify(obj);
		} catch (e) {
			return "{}";
		}
	};

	/*
	 * dual-layout iframe reload 후에도 좌측 목록 선택 상태를 복원하기 위한 탭 단위 임시 저장소.
	 * 쿠키처럼 서버 요청에 실리지 않고, 현재 브라우저 탭을 닫으면 함께 사라지는 UI 상태만 보관한다.
	 */
	commonUtils.setSessionState = function(key, value) {
		if (!key) {
			return;
		}
		sessionStorage.setItem(key, JSON.stringify(value || {}));
	};

	commonUtils.getSessionState = function(key) {
		if (!key) {
			return {};
		}
		try {
			return JSON.parse(sessionStorage.getItem(key) || "{}");
		} catch (e) {
			return {};
		}
	};

})(window.MagicIAM_JSCommonUtils);
