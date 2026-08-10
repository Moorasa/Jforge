/* ===============================================================================================

Name : jworks-snackbar-1.0.0.js

Description :
	JWORKS Web UI Component 스낵바

Dependency :
	jQuery 필요 - 1.11.1 이상

License :
	Original BSD License 준용 

Remarks :
	css를 js에 모두 포함하는 방법 고려
	
=============================================================================================== */
"undefined" == typeof JWORKS_JSSnackBar && ((JWORKS_JSSnackBar = {}),(function(snackBar) {


// 스낵바 옵션
let option = {
	iconSrc: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABgAAAAYCAYAAADgdz34AAAACXBIWXMAAAsTAAALEwEAmpwYAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAFUSURBVHgBpVYLkcQgDI2EOrhKqBQc3Do4HFAHJ4F1UAmVUgmVkIPZl23KsXzaN8NMB8hL8gKhRAUw8xCGC2MNY+MDO+ZsGCP1IhqBoBW+2VHY+IMIJdJobDRB+J4w90yysjVypwyWKFFDQGPiyJUiF1jqRLCZP9ojCpFlpotA0UWuUS94KRbdBKSNWElFLxjpAlA7h+9BqTHotC5Fz8fBeKi551tuPs67oU7kyDFv3jKpdCbqwCdyrInsG4n41IESudrz4k0d8Os+TBXyvUSeOtjw/YUFuTDfBfKpQj6BY88WOeeklRx7T0UWMp9skvl4jH9byWHrxVYuBoNgyDjZIWMr+f+Lq2Ra6CY413b43Oy6O6nicUqNMV20KjV3gbze7vnc0z03ND/UcFF2c83AKrnEkdFFhqQGa/p5fVBjuukzWMPCN/4uYkZrktWGuShp8d3+A0qAcyiBA5XBAAAAAElFTkSuQmCC",
	timeout: 2000,
	opacity: "0.8",
	color: "#FFFFFF",
	bgcolor: "#3E454D",
}

// 스낵바 관리 큐
let queue = [];

// 스낵바 옵션 설정
// 옵션을 설정하지 않은 경우 기본값으로 수행
snackBar.setOption = function(newOption) {
	if(newOption.hasOwnProperty("timeout"))
		option.timeout = newOption.timeout;
	if(newOption.hasOwnProperty("iconSrc"))
		option.iconSrc = newOption.iconSrc;
	if(newOption.hasOwnProperty("opacity"))
		option.opacity = newOption.opacity;
	if(newOption.hasOwnProperty("color"))
		option.color = newOption.color;
	if(newOption.hasOwnProperty("bgcolor"))
		option.bgcolor = newOption.bgcolor;
	if(newOption.hasOwnProperty("fontFamily"))
		option.fontFamily = newOption.fontFamily;
}

// 호출할 때마다 하나의 스낵바를 생성한다.
snackBar.create = function(text) {

	if(queue.length == 0) {
		let appendStr = `
			<div id="snack-bar">
			<div class="encase">
			<div class="item">
			<div class="encase">
			<img src="${option.iconSrc}" />
			<span>${text}</span>
			</div>
			</div>
			</div>
			</div>
		`;
		let obj = $(appendStr);
		obj.find(".item").css("background", option.bgcolor);
		obj.find(".item > .encase").css("opacity", option.opacity);
		obj.find(".item span").css("font-family", option.fontFamily);
		obj.find(".item span").css("color", option.color);
		$("body").append(obj);
	}
	else {
		let appendStr = `
			<div class="item">
			<div class="encase">
			<img src="${option.iconSrc}" />
			<span>${text}</span>
			</div>
			</div>
		`;
		let obj = $(appendStr);
		obj.css("background", option.bgcolor);
		obj.find("> .encase").css("opacity", option.opacity);
		obj.find("span").css("font-family", option.fontFamily);
		obj.find("span").css("color", option.color);
		$("#snack-bar > .encase").append(obj);
	}
	$("#snack-bar .item:last-child").fadeIn();
	queue.push(text);

	// 일정 시간 후 스낵바를 하나씩 제거한다.
	setTimeout(function() {
			// timer를 연속으로 사용하는 경우 fadeout에서 timer가 남는 오류가 발생
			//$("#snack-bar .item:first-child").fadeOut("slow", "swing", function() {
				JWORKS_JSSnackBar.remove();
			//});
	}, option.timeout);

}

// 스낵바 제거
snackBar.remove = function() {
	$("#snack-bar .item:first-child").remove();
	queue.shift();
	// 스낵바의 항목이 없는 경우 스낵바 자체를 제거
	// : popup 등 다른 body append 객체가 생기면 스낵바가 복수로 생성되는 것을 방지  
	if(queue.length == 0)
		$("#snack-bar").remove();
}

 
})(JWORKS_JSSnackBar));



