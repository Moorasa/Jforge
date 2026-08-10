/* ===============================================================================================

Name : jworks-alert-1.0.0.js

Description :
	화면 대기 alert 처리

Dependency :
	jQuery 필요 - 1.11.1 이상

License :
	Original BSD License 준용 
	
Remarks :
	css를 js에 모두 포함하는 방법 고려

=============================================================================================== */
"undefined" == typeof JWORKS_JSAlert && (JWORKS_JSAlert = {}),(function(alert) {


// end 하지 않고 start를 중복하지 않도록 확인
let isStarted = false;

// alert 옵션
alert.option = {
	titleBarBgColor: "#313942",
	titleBarColor: "#FFFFFF",
	buttonBgColor: "#161C23",
	buttonColor: "#FFFFFF",
	closeText: "닫기",
	buttonText: "확인",
	alertCallback: null,
}

alert.bodyOverflow = "auto";

// alert 옵션 설정
// 옵션을 설정하지 않은 경우 기본값으로 수행
alert.setOption = function(option) {
	if(option.hasOwnProperty("titleBarBgColor"))
		alert.option.titleBarBgColor = option.titleBarBgColor;
	if(option.hasOwnProperty("titleBarColor"))
		alert.option.titleBarColor = option.titleBarColor;
	if(option.hasOwnProperty("buttonBgColor"))
		alert.option.buttonBgColor = option.buttonBgColor;
	if(option.hasOwnProperty("buttonColor"))
		alert.option.buttonColor = option.buttonColor;
	if(option.hasOwnProperty("closeText"))
		alert.option.closeText = option.closeText;
	if(option.hasOwnProperty("buttonText"))
		alert.option.buttonText = option.buttonText;
	if(option.hasOwnProperty("alertCallback"))
		alert.option.alertCallback = option.alertCallback;
	else
		alert.option.alertCallback = null;
}

alert.start = function(title, text, callback) {
	if(isStarted) {
		console.log("LoadingSpinner is already started!!! Check your code!");
		return;
	} 
	else {
		isStarted = true;
	}

	// callback 함수 저장
	if(callback)
		alert.option.alertCallback = callback;
	else
		alert.option.alertCallback = null;

	// body overflow를 저장
	alert.bodyOverflow = $("body").css("overflow");
	$("body").css("overflow", "hidden");

	let appendStr = `
		<div id="alert">
		<div class="encase">
			<section class="msg-box">
				${title ? `
				<div class="title-bar">
					<span class="title">${title}</span>
					<img alt="${alert.option.closeText}" src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAB2SURBVHgBnZMBDsAQDEXVxWcn/9NFFxnql0RI9T3ShgC4Uh0icqfAMC4rW2exQAAujX0DKgAj0ZyWW/4HW8kSZiRb2JPQ8EwShicSF86eY7Gnbv+ejUCLB7iLcRKvYFsJiGovJQi0apDgoM+9RAys35kWmETXB9OVGQdhtC6/AAAAAElFTkSuQmCC" />
				</div>
				` : ""}
				<p class="text">${text}</p>
				<div class="button" tabindex="0">${alert.option.buttonText}</div>
			</section>
		</div>
		</div>"
	`;
	let obj = $(appendStr);
	obj.find(".title-bar").css("background", alert.option.titleBarBgColor);
	obj.find(".title-bar").css("color", alert.option.titleBarColor);
	obj.find(".title-bar img").on("click", function() {
		JWORKS_JSAlert.end();
	});
	obj.find(".button").css("background", alert.option.buttonBgColor);
	obj.find(".button").css("color", alert.option.buttonColor);
	obj.find(".button").on("click", function() {
		JWORKS_JSAlert.end();
	});

	$("body").append(obj);
	const $button = $("body > #alert .button");
	$button.on("keydown keyup", function(event) {
		event.preventDefault();
		// enter, space
		if(event.keyCode == 13 || event.keyCode == 32) {
			$(this).trigger("click");
		}
	});
	$button.focus();
	
	// 최상위 DOM 경우 
	if(window.top === window) {
		console.log("html");
		return;
	}
	// 최상위 DOM 아닌 경우 : iframe 
	else {
		console.log("iframe");
		alert.popupPositioning();
	}

}

alert.end = function() {
	isStarted = false;

	// body overflow를 복구
	$("body").css("overflow", alert.bodyOverflow);

	$("body > #alert").remove();

	if(JWORKS_JSAlert.option.alertCallback)
		JWORKS_JSAlert.option.alertCallback();
}

// 팝업의 위치는 
// 기본적으로 flex를 이용하여 중앙에 지정한다.
// 화면의 스크롤이 발생한 경우
// 팝업의 위치를 조정할 필요가 있어 block 으로 위치 지정
alert.popupPositioning = function() {
	
	const $container = $("body > #alert");
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

 
})(JWORKS_JSAlert);



