/* ===============================================================================================

Name : jworks-confirm-1.0.0.js

Description :
	화면 대기 confirm 처리

Dependency :
	jQuery 필요 - 1.11.1 이상

License :
	Original BSD License 준용 
	
Remarks :
	css를 js에 모두 포함하는 방법 고려

=============================================================================================== */
"undefined" == typeof JWORKS_JSConfirm && (JWORKS_JSConfirm = {}),(function(confirm) {


// end 하지 않고 start를 중복하지 않도록 확인
let isStarted = false;

// confirm 옵션
confirm.option = {
	titleBarBgColor: "#313942",
	titleBarColor: "#FFFFFF",
	cancelButtonBgColor: "#FFFFFF",
	cancelButtonColor: "#134F7D",
	okButtonBgColor: "#134F7D",
	okButtonColor: "#FFFFFF",
	closeText: "닫기",
	cancelButtonText: "취소",
	okButtonText: "확인",
	confirmCallback: null, /*필수 항목*/
}

confirm.bodyOverflow = "auto";

// confirm 옵션 설정
// 옵션을 설정하지 않은 경우 기본값으로 수행
confirm.setOption = function(option) {
	if(option.hasOwnProperty("titleBarBgColor"))
		confirm.option.titleBarBgColor = option.titleBarBgColor;
	if(option.hasOwnProperty("titleBarColor"))
		confirm.option.titleBarColor = option.titleBarColor;
	if(option.hasOwnProperty("cancelButtonBgColor"))
		confirm.option.cancelButtonBgColor = option.cancelButtonBgColor;
	if(option.hasOwnProperty("cancelButtonColor"))
		confirm.option.cancelButtonColor = option.cancelButtonColor;
	if(option.hasOwnProperty("okButtonBgColor"))
		confirm.option.okButtonBgColor = option.okButtonBgColor;
	if(option.hasOwnProperty("okButtonColor"))
		confirm.option.okButtonColor = option.okButtonColor;
	if(option.hasOwnProperty("closeText"))
		confirm.option.closeText = option.closeText;
	if(option.hasOwnProperty("cancelButtonText"))
		confirm.option.cancelButtonText = option.cancelButtonText;
	if(option.hasOwnProperty("okButtonText"))
		confirm.option.okButtonText = option.okButtonText;
	if(option.hasOwnProperty("confirmCallback"))
		confirm.option.confirmCallback = option.confirmCallback;
}

confirm.start = function(title, text, callback) {
	if(isStarted) {
		console.log("LoadingSpinner is already started!!! Check your code!");
		return;
	} 
	else {
		isStarted = true;
	}

	// callback 함수 저장
	if(callback)
		confirm.option.confirmCallback = callback;

	// body overflow를 저장
	confirm.bodyOverflow = $("body").css("overflow");
	$("body").css("overflow", "hidden");

	let appendStr = `
		<div id="confirm">
		<div class="encase">
			<section class="msg-box">
				${title ? `
				<div class="title-bar">
					<span class="title">${title}</span>
					<img alt="${confirm.option.closeText}" src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAB2SURBVHgBnZMBDsAQDEXVxWcn/9NFFxnql0RI9T3ShgC4Uh0icqfAMC4rW2exQAAujX0DKgAj0ZyWW/4HW8kSZiRb2JPQ8EwShicSF86eY7Gnbv+ejUCLB7iLcRKvYFsJiGovJQi0apDgoM+9RAys35kWmETXB9OVGQdhtC6/AAAAAElFTkSuQmCC" />
				</div>
				` : ""}
				<p class="text">${text}</p>
				<div class="buttons">
					<div tabindex="0" class="button cancel">${confirm.option.cancelButtonText}</div>
					<div tabindex="0" class="button ok">${confirm.option.okButtonText}</div>
				</div>
			</section>
		</div>
		</div>
	`;
	let obj = $(appendStr);
	obj.on("keyup", function(event) {
		// escape
		if(event.keyCode == 27) {
			$("body > #confirm .button.cancel").trigger("click");
		}
	});
	obj.find(".title-bar").css("background", confirm.option.titleBarBgColor);
	obj.find(".title-bar").css("color", confirm.option.titleBarColor);
	obj.find(".title-bar img").on("click", function() {
		$("body > #confirm .button.cancel").trigger("click");
	});
	obj.find(".button.cancel").css("background", confirm.option.cancelButtonBgColor);
	obj.find(".button.cancel").css("color", confirm.option.cancelButtonColor);
	obj.find(".button.cancel").on("click", function() {
		JWORKS_JSConfirm.end(false);
	});
	obj.find(".button.ok").css("background", confirm.option.okButtonBgColor);
	obj.find(".button.ok").css("color", confirm.option.okButtonColor);
	obj.find(".button.ok").on("click", function() {
		JWORKS_JSConfirm.end(true);
	});

	$("body").append(obj);
	const $buttonOk = $("body > #confirm .button.ok");
	$buttonOk.on("keyup", function(event) {
		// enter, space
		if(event.keyCode == 13 || event.keyCode == 32) {
			$(this).trigger("click");
		}
	});
	$("body > #confirm .button.ok").focus();
	
	// 최상위 DOM 경우 
	if(window.top === window) {
		console.log("html");
		return;
	}
	// 최상위 DOM 아닌 경우 : iframe 
	else {
		console.log("iframe");
		confirm.popupPositioning();
	}

}

confirm.end = function(callback) { // confirm 변수 섀도잉으로 parameter 이름 변경
	isStarted = false;

	// body overflow를 복구
	$("body").css("overflow", confirm.bodyOverflow);

	$("body > #confirm").remove();
	
	JWORKS_JSConfirm.option.confirmCallback(callback);
}

confirm.popupPositioning = function() {
	
	const $container = $("body > #confirm");
	// 브라우저 높이
	const viewHeight = window.top.innerHeight;
	// 브라우저 기준 해당 frame의 Y 좌표
	let frameY = window.frameElement.getBoundingClientRect().y;
	if(frameY > 0) 
		frameY = 0;
	else 
		frameY = -1 * frameY;
	// frame 높이
	const frameHeight = window.frameElement.getBoundingClientRect().height;
	const $box = $container.find(".msg-box");
	// box 높이
	const boxHeight = $box.height();
	// box의 브라우저 기준 상하 여백
	let boxYMargin = Math.min(viewHeight, frameHeight) - boxHeight;
	if(boxYMargin < 0)
		boxYMargin = 0;

	$container.removeClass("block").addClass("block");
	const boxTop = frameY + (boxYMargin / 2);
	if(boxTop + boxHeight > frameHeight)
		$box.css("bottom", `${boxYMargin / 2}px`);
	else
		$box.css("top", `${frameY + (boxYMargin / 2)}px`);
	
}
 
})(JWORKS_JSConfirm);



