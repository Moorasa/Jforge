<#-- P5-5c: DUAL_LAYOUT 아키타입 CSS. 고정 셀렉터만(자유문자열 0). -->
<#--
  아티팩트: dualCss → {stem}.css (계약 §10.1)
  역할: 좌우 2단 레이아웃 컨테이너(.dual-layout-area)의 flex 골격 + iframe 채움. 리사이저/접힘 세부는
        번들 commonSection.css/init.css가 보강하며, 여기서는 flex 배치와 iframe 크기만 고정한다.
  🔒 props 자유문자열 삽입 0(고정 셀렉터·고정 값만). 스크립트릿 0 / 배너 0.
-->
html, body.dual-layout {
	height: 100%;
	margin: 0;
}

.dual-layout-area {
	display: flex;
	flex-direction: row;
	align-items: stretch;
	width: 100%;
	height: 100%;
}

.dual-layout-area .layout-left {
	flex: 1;
	min-width: 0;
	overflow: hidden;
}

.dual-layout-area .layout-right {
	flex: 1;
	min-width: 0;
	overflow: hidden;
}

<#-- 좌우 패인 iframe: 패인 전체 채움(commonSection.js가 드래그 시 display 토글). -->
.dual-layout-area .layout-left > iframe,
.dual-layout-area .layout-right > iframe {
	width: 100%;
	height: 100%;
	border: 0;
	display: block;
}

<#-- 가운데 리사이저: commonSection.js가 .resizer-bar 드래그·.collapse-left/right·.expand 이벤트 바인딩. -->
.dual-layout-area .layout-middle.resizer {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	flex: 0 0 auto;
	width: 12px;
	cursor: col-resize;
	user-select: none;
}

.dual-layout-area .layout-middle.resizer .resizer-bar {
	width: 4px;
	height: 100%;
	background: #d0d0d0;
}

.dual-layout-area .layout-middle.resizer .expand {
	display: none;
}
