/* ===============================================================================================

Name : jworks-tooltip-1.0.0.js

Description :
	화면 대기 tooltip 처리

Dependency :
	jQuery 필요 - 1.11.1 이상

License :
	Original BSD License 준용 
	
Remarks :
	css를 js에 모두 포함하는 방법 고려

=============================================================================================== */
"undefined" == typeof JWORKS_JSTooltip && (JWORKS_JSTooltip = {}),(function(tooltip) {


// 업무(자원) 노드 타입 정의
tooltip.ALIGN_TYPE = {
	CENTER: "center",
	CORNER: "corner"
};

// 툴팁 관리 큐
tooltip.queue = [];

// end 하지 않고 start를 중복하지 않도록 확인
let isStarted = false;

// tooltip 옵션
tooltip.option = {
	titleBarBgColor: "#313942",
	titleBarColor: "#FFFFFF",
	buttonBgColor: "#161C23",
	buttonColor: "#FFFFFF",
	closeText: "닫기",
	buttonText: "확인",
	tooltipCallback: null,
}

tooltip.bodyOverflow = "auto";

// tooltip 옵션 설정
// 옵션을 설정하지 않은 경우 기본값으로 수행
tooltip.setOption = function(option) {
	if(option.hasOwnProperty("titleBarBgColor"))
		tooltip.option.titleBarBgColor = option.titleBarBgColor;
	if(option.hasOwnProperty("titleBarColor"))
		tooltip.option.titleBarColor = option.titleBarColor;
	if(option.hasOwnProperty("buttonBgColor"))
		tooltip.option.buttonBgColor = option.buttonBgColor;
	if(option.hasOwnProperty("buttonColor"))
		tooltip.option.buttonColor = option.buttonColor;
	if(option.hasOwnProperty("closeText"))
		tooltip.option.closeText = option.closeText;
	if(option.hasOwnProperty("buttonText"))
		tooltip.option.buttonText = option.buttonText;
	if(option.hasOwnProperty("tooltipCallback"))
		tooltip.option.tooltipCallback = option.tooltipCallback;
	else
		tooltip.option.tooltipCallback = null;
}

tooltip.attach = function(tooltipOption) {
	// 배열에 해당 container가 이미 존재하는 경우 : 기존 삭제
	for (let i = tooltip.queue.length - 1; i >= 0; i--) {
		if(tooltip.queue[i].$container.is(tooltipOption.$container)) {
			console.log("delete");
			tooltip.queue.splice(i, 1);
		}
	}
	// 배열에 해당 툴팁 등록
	tooltip.queue.push(tooltipOption);
	
	tooltipOption.$container.off("mouseenter").on("mouseenter", function() {
		makeTooltip($(this), tooltipOption);
	});
	tooltipOption.$container.off("mouseleave").on("mouseleave", function() {
		removeTooltip();
	});
}

function makeTooltip($container, tooltipOption) {
	const appendStr = `
		<div id="tooltip">
		<div class="encase">
			<div class="tooltip-body">
				${tooltipOption.close ? `
				<div class="close">
					<img alt="닫기" src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAB2SURBVHgBnZMBDsAQDEXVxWcn/9NFFxnql0RI9T3ShgC4Uh0icqfAMC4rW2exQAAujX0DKgAj0ZyWW/4HW8kSZiRb2JPQ8EwShicSF86eY7Gnbv+ejUCLB7iLcRKvYFsJiGovJQi0apDgoM+9RAys35kWmETXB9OVGQdhtC6/AAAAAElFTkSuQmCC" />
				</div>
				` : ""}
				${tooltipOption.title ? `
				<p class="title font-body-03">${tooltipOption.title}</p>
				` : ""}
				<p class="text font-body-04">${tooltipOption.text}</p>
				<img class="tail top-left" alt="" title="" src="/images/admin/icon-tooltip-tail-top-left.png" />
				<img class="tail top-right" alt="" title="" src="/images/admin/icon-tooltip-tail-top-right.png" />
				<img class="tail bottom-left" alt="" title="" src="/images/admin/icon-tooltip-tail-bottom-left.png" />
				<img class="tail bottom-right" alt="" title="" src="/images/admin/icon-tooltip-tail-bottom-right.png" />
			</div>
		</div>
		</div>
	`;
	const $obj = $(appendStr);
	$("body").append($obj);

	const viewportWidth = $(window).width();
	const viewportHeight = $(window).height();
	const containerViewportTop = $container.offset().top - $(window).scrollTop();
	const containerViewportLeft = $container.offset().left - $(window).scrollLeft();
	const containerWidth = $container.width();
	const containerHeight = $container.height();
	const $tooltip = $("body > #tooltip");
	const tooltipWidth = $tooltip.width();
	const tooltipHeight = $tooltip.height();
	const tooltipMargin = 20;

	// 상 공간 있음
	if(containerViewportTop > tooltipHeight + tooltipMargin) {
		$tooltip.css("top", `${containerViewportTop - tooltipHeight - tooltipMargin}px`);
		// 우 공간 있음
		if(viewportWidth > containerViewportLeft + tooltipWidth + tooltipMargin) {
			// 우상 
			$tooltip.css("left", `${containerViewportLeft}px`);
			$tooltip.find(".tooltip-body").css("border-bottom-left-radius", `0`);
			$tooltip.find(".tail.bottom-left").show();
		}
		// 우 공간 없음 : 좌
		else {
			// 좌상 
			$tooltip.css("left", `${containerViewportLeft - tooltipWidth}px`);
			$tooltip.find(".tooltip-body").css("border-bottom-right-radius", `0`);
			$tooltip.find(".tail.bottom-right").show();
		}
	}
	// 상 공간 없음 : 하
	else {
		$tooltip.css("top", `${containerViewportTop + containerHeight + tooltipMargin}px`);
		// 우 공간 있음
		if(viewportWidth > containerViewportLeft + tooltipWidth + tooltipMargin) {
			// 우하
			$tooltip.css("left", `${containerViewportLeft}px`);
			$tooltip.find(".tooltip-body").css("border-top-left-radius", `0`);
			$tooltip.find(".tail.top-left").show();
		}
		// 우 공간 없음 : 좌
		else {
			// 좌하
			$tooltip.css("left", `${containerViewportLeft - tooltipWidth}px`);
			$tooltip.find(".tooltip-body").css("border-top-right-radius", `0`);
			$tooltip.find(".tail.top-right").show();
		}
	}
	if(tooltipOption.align === tooltip.ALIGN_TYPE.CENTER) {
	}
	else if(tooltipOption.align === tooltip.ALIGN_TYPE.CORNER) {
	}
	else {
	}

	$tooltip.show();
}

function removeTooltip() {
	$("body > #tooltip").remove();
}

tooltip.start = function(title, text, callback) {
	if(isStarted) {
		console.log("LoadingSpinner is already started!!! Check your code!");
		return;
	} 
	else {
		isStarted = true;
	}

	// callback 함수 저장
	if(callback)
		tooltip.option.tooltipCallback = callback;
	else
		tooltip.option.tooltipCallback = null;

	// body overflow를 저장
	tooltip.bodyOverflow = $("body").css("overflow");
	$("body").css("overflow", "hidden");

	let appendStr = `
		<div id="tooltip">
		<div class="encase">
			<section class="msg-box">
				${title ? `
				<div class="title-bar">
					<span class="title">${title}</span>
					<img alt="${tooltip.option.closeText}" src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAB2SURBVHgBnZMBDsAQDEXVxWcn/9NFFxnql0RI9T3ShgC4Uh0icqfAMC4rW2exQAAujX0DKgAj0ZyWW/4HW8kSZiRb2JPQ8EwShicSF86eY7Gnbv+ejUCLB7iLcRKvYFsJiGovJQi0apDgoM+9RAys35kWmETXB9OVGQdhtC6/AAAAAElFTkSuQmCC" />
				</div>
				` : ""}
				<p class="text">${text}</p>
				<div class="button" tabindex="0">${tooltip.option.buttonText}</div>
			</section>
		</div>
		</div>"
	`;
	let obj = $(appendStr);
	obj.find(".title-bar").css("background", tooltip.option.titleBarBgColor);
	obj.find(".title-bar").css("color", tooltip.option.titleBarColor);
	obj.find(".title-bar img").on("click", function() {
		JWORKS_JSTooltip.end();
	});
	obj.find(".button").css("background", tooltip.option.buttonBgColor);
	obj.find(".button").css("color", tooltip.option.buttonColor);
	obj.find(".button").on("click", function() {
		JWORKS_JSTooltip.end();
	});

	$("body").append(obj);
	const $button = $("body > #tooltip .button");
	$button.on("keydown keyup", function(event) {
		event.preventDefault();
		// enter, space
		if(event.keyCode == 13 || event.keyCode == 32) {
			$(this).trigger("click");
		}
	});
	$button.focus();
	
}

tooltip.end = function() {
	isStarted = false;

	// body overflow를 복구
	$("body").css("overflow", tooltip.bodyOverflow);

	$("body > #tooltip").remove();

	if(JWORKS_JSTooltip.option.tooltipCallback)
		JWORKS_JSTooltip.option.tooltipCallback();
}

 
})(JWORKS_JSTooltip);



