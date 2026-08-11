/* ===============================================================================================

Name : JWorks_JSCommonPopup.js

Description :
	Popup UI에서 공통으로 사용하는 유틸리티 파일
	Popup 의 popup이 발생할 수 있으므로
	context를 이용한 방식으로 구현하여 중복은 방지

Remarks :
	재배포를 금합니다.

=============================================================================================== */
window.JWorks_JSCommonPopup = window.JWorks_JSCommonPopup || {};
(function(commonPopup) {
	"use strict";

	if (commonPopup.__defined) {
		return;
	}
	commonPopup.__defined = true;


	// 팝업 context
	const contexts = [];

	/* ===============================================================================================
	postMessage 관련
	=============================================================================================== */
	commonPopup.postMessageEventListener = function() {
		window.addEventListener("message", function(event) {
			if (window.location.origin === event.origin) {
				// pass
			}
			else {
				console.log("error : not same origin");
				return;
			}

			const data = event.data;
			if (data.type === PostMessageType.RENDER_COMPLETE) {
				commonPopup.popupPositioning();
			}
			else {
				console.log("unreachable : known post message");
			}
		});
	}

	commonPopup.init = function(option) {
		const context = {
			$container: option.$container,
			header: option?.header,
			body: option?.body,
			footer: option?.footer,
			attribute: option.attribute || { use: false },
			target: option.target || { use: false },
			useRoleValidity: option.useRoleValidity || false,
			useAttributeOnlyValue: option.useAttributeOnlyValue || false
		}
		contexts.push(context);

		registEvent(context);

		if (option.type === PopupType.width.SMALL) {
			// 560px
			context.$container.find(".box").css("width", "29.1667rem");
		}
		else if (option.type === PopupType.width.MEDIUM) {
			if($("body").hasClass("dual-layout"))
				// 560px
				context.$container.find(".box").css("width", "29.1667rem");
			else
				// 870px
				context.$container.find(".box").css("width", "45.3125rem");
		}
		else if (option.type === PopupType.width.LARGE) {
			if($("body").hasClass("dual-layout"))
				// 800px
				context.$container.find(".box").css("width", "41.6667rem");
			else
				// 1120px
				context.$container.find(".box").css("width", "58.3333rem");
		}

		// 최상위 DOM 경우
		if (window.top === window) {
			console.log("html");
			return;
		}
		// 최상위 DOM 아닌 경우 : iframe (CSS 파일 로딩 후 포지셔닝 되도록 수정)
		else {
			// CSS link 태그 찾기
			const $cssLinks = context.$container.find("link[rel='stylesheet']");
			let pendingCount = $cssLinks.length;

			// CSS 파일이 없으면 즉시 실행, 있으면 로딩 대기
			if (pendingCount === 0) {
				commonPopup.popupPositioning();
			} else {
				// CSS 로딩(또는 에러)이 끝나면 카운트 차감 후 실행
				$cssLinks.one("load error", function() {
					pendingCount--;
					if (pendingCount <= 0) {
						commonPopup.popupPositioning();
					}
				});
			}
		}

		// 팝업 내부에 iframe이 있을 때 empty-case.popup을 위한 메시지 전달
		const $iframe = context.$container.find('iframe');
		if ($iframe.length > 0) {
			$iframe.on('load', function() {
				const childWindow = this.contentWindow;
				childWindow.postMessage({ type: 'SET_EMPTY_MODE', mode: 'popup' }, window.location.origin);
			});
		}

		// 속성 핸들러 위임
		if (option.attribute && option.attribute.use) {
			JWorks_JSCommonAttributeHandler.init(context);
		}
	}

	function registEvent(context) {
		context.$container.on("click", ".layout-header .close", function() {
			context.$container.remove();
		});
		if (context.footer.type == PopupType.button.OK) {
			context.$container.on("click", ".buttons button.ok", function() {
				context.footer.okCallback?.();
			});
		}
		else if (context.footer.type == PopupType.button.OK_CANCEL) {
			context.$container.on("click", ".buttons button.ok", function() {
				context.footer.okCallback?.();
			});
			context.$container.on("click", ".buttons button.cancel", function() {
				context.$container.remove();
				context.footer.cancelCallback?.();
			});
		}
		else if (context.footer.type == PopupType.button.OK_COPY) {
			context.$container.on("click", ".buttons button.ok", function() {
				context.$container.remove();
			});
			context.$container.on("click", ".buttons button.copy", function() {
				context.footer.copyCallback?.();
			});
		}

		JWorks_JSCommonUtils.registEventClearableInput({
			$container: context.$container
		});

		// 속성 관리 : 속성 추가 팝업
		if (context.useAttributeOnlyValue) {
			const $container = context.$container;
			const $attrBody = $container.find(".attribute-body");

			// 1. 행 추가 버튼 클릭
			$container.on("click", ".insert", function() {
				commonPopup.addAttributeOnlyValueRow($container);

				// 스크롤 효과
				const $scrollBody = $container.find(".layout-body");
				if ($scrollBody.length > 0) {
					$scrollBody.stop().animate({
						scrollTop: $scrollBody[0].scrollHeight
					}, 300);
				}
			});

			// 2. 행 삭제 버튼 클릭
			$container.on("click", ".delete", function() {
				const $checkedRows = $attrBody.find("input[type='checkbox']:checked");

				if ($checkedRows.length === 0) {
					JWORKS_JSAlert.start("", "삭제할 속성을 선택하세요.");
					return;
				}

				$checkedRows.each(function() {
					$(this).closest(".attribute-row").remove();
				});

				// 삭제 후 Empty Case 처리
				if ($attrBody.find(".attribute-row").length === 0) {
					$attrBody.find(".empty-case").show();
				}
			});
		}

		// 권한 유효 기간 : 기간 제한 없음
		if (context.useRoleValidity) {
			const $container = context.$container;
			$container.on("change", "#noLimit", function() {
				const isChecked = $(this).is(":checked");
				const $expireInput = $container.find(".expire-date");
				const $expireGroup = $expireInput.closest(".date-group");

				if (isChecked) {
					$expireInput.val("9999-12-31");
					$expireGroup.addClass("disabled");
				} else {
					$expireInput.val("");
					$expireGroup.removeClass("disabled");
				}
			});
		}

		// 오브젝트 지정: 태그 삭제 이벤트
		if (context.target.use) {
			context.$container.find(".target-body").on("click", ".remove", function() {
				const $targetBody = $(this).closest(".target-body");

				$(this).closest(".target-item").remove();

				if ($targetBody.find(".target-item").length === 0) {
					$targetBody.append(
						`<p class="target-placeholder font-body-04">${context.target.placeholderText}</p>`
					);
				}

				context.target.onChange?.();
			});
		}
	}

	commonPopup.addTargetItem = function($container, selectedList, onChange) {
		const $targetBody = $container.find(".target-body");

		// 기존 선택 태그는 유지하고 안내 문구만 제거한다.
		$targetBody.find(".target-placeholder").remove();

		(selectedList || []).forEach(function(item) {
			if (!item) {
				return;
			}

			let tagHtml = `
	            <span class="target-item font-body-03" data-no="${item.no}">
	                ${item.name}
	                <button type="button" class="remove" title="삭제">
	                    <img src="/images/admin/icon-remove-button.png" alt="삭제">
	                </button>
	            </span>
	        `;

			// 이미 선택된 항목은 중복 추가하지 않고 최신 값으로 교체한다.
			const $existTargetItem = $targetBody.find(`.target-item[data-no="${item.no}"]`);
			if ($existTargetItem.length > 0) {
				$existTargetItem.replaceWith(tagHtml);
			} else {
				$targetBody.append(tagHtml);
			}
		});

		onChange?.();
	}

	commonPopup.addAttributeOnlyValueRow = function($container, value = "", isActive = false) {
		if (!$container) return;

		const $attrBody = $container.find(".attribute-body");
		$attrBody.find(".empty-case").hide();

		const uniqueId = new Date().getTime() + Math.random().toString(36).substring(2);
		const newRowHtml = `
	        <div class="attribute-row">
	            <input type="checkbox" id="attr-chk-${uniqueId}">
	            <label for="attr-chk-${uniqueId}" class="blind">속성 선택</label>

	            <div class="input-wrapper clearable">
	                <input type="text" id="attr-value-${uniqueId}" class="attr-value" placeholder="속성값을 입력하세요." value="${value}">
					<div class="input-inner-buttons">
	                    <img class="clear-btn" src="/images/admin/icon-clear-button.png" alt="입력 내용 지우기">
	                    <button type="button" class="default-toggle-btn ${isActive}">기본값</button>
	                </div>
	            </div>

	        </div>
	    `;

		$attrBody.append(newRowHtml);
	};

	commonPopup.getValidatedData = function($container) {
		return JWorks_JSCommonAttributeHandler.getValidatedData($container);
	}

	// 팝업의 위치는
	// 기본적으로 flex를 이용하여 중앙에 지정한다.
	// 화면의 스크롤이 발생한 경우
	// 팝업의 위치를 조정할 필요가 있어 block 으로 위치 지정
	commonPopup.popupPositioning = function() {

		if (contexts.length > 0)
			;
		else
			return;

		// 마지막 등록된 팝업의 context를 확인
		const context = contexts[contexts.length - 1];
		const $container = context.$container;
		// 브라우저 높이
		const viewHeight = window.top.innerHeight;
		// 브라우저 기준 해당 frame의 Y 좌표
		let frameY = window.frameElement.getBoundingClientRect().y;
		if (frameY > 0)
			frameY = 0;
		else
			frameY = -1 * frameY;
		// frame 높이
		const frameHeight = window.frameElement.getBoundingClientRect().height;
		const $box = $container.find(".box");
		// box 높이
		const boxHeight = $box.height();

		console.log(`[Popup Debug] Box Height: ${boxHeight}, Frame Height: ${frameHeight}`);

		// box의 브라우저 기준 상하 여백
		let boxYMargin = Math.min(viewHeight, frameHeight) - boxHeight;
		if (boxYMargin < 0)
			boxYMargin = 0;

		$container.removeClass("block").addClass("block");
		const boxTop = frameY + (boxYMargin / 2);


		console.log(`[Popup Debug] Calculated Top: ${boxTop}`);


		if (boxTop + boxHeight > frameHeight)
			$box.css("bottom", `${boxYMargin / 2}px`);
		else
			$box.css("top", `${frameY + (boxYMargin / 2)}px`);

	}


})(window.JWorks_JSCommonPopup);
