<#--
  FREE_CANVAS per-screen CSS (계약 §17.3/§17.5).

  🔒 인젝션 원천 차단 — 이 파일이 CSS 에 넣는 것은 두 가지뿐이다:
     (1) 템플릿에 리터럴로 적힌 셀렉터/속성명
     (2) `?is_number` + 유효범위를 통과한 값의 `?c` 포맷
     인스턴스 식별자·클래스명·props 문자열은 **어떤 경우에도 CSS 에 삽입되지 않는다**.
     좌표 클래스의 숫자는 FreeMarker 루프 카운터이며 DEFINITION 데이터가 아니다.

  X/Y/W/H 4키를 전부 통과해야 그 인스턴스 규칙을 산출한다(§17.2). 하나라도 결여·비숫자·범위 밖이면
  해당 인스턴스는 0바이트 — 마크업은 그대로 남아 문서 흐름대로 배치된다(안전측 폴백).
-->
<#assign nodes = (canvasTree)![]>
<#assign canvasNode = (canvas)!{}>
<#assign sheetW = 1280>
<#assign sheetH = 800>
<#if (canvasNode["widthPx"])?? && canvasNode["widthPx"]?is_number && (canvasNode["widthPx"] >= 320) && (canvasNode["widthPx"] <= 4000)>
<#assign sheetW = canvasNode["widthPx"]>
</#if>
<#if (canvasNode["heightPx"])?? && canvasNode["heightPx"]?is_number && (canvasNode["heightPx"] >= 240) && (canvasNode["heightPx"] <= 8000)>
<#assign sheetH = canvasNode["heightPx"]>
</#if>
#${stem}-canvas .frg-fc-sheet {
	position: relative;
	width: ${sheetW?c}px;
	height: ${sheetH?c}px;
	margin: 0 auto;
}

<#-- §17.5 래퍼가 슬롯 부모를 흉내낸다 — 공통 런타임 CSS(flex 자식 가정)가 그대로 성립하도록. -->
#${stem}-canvas .frg-fc-item {
	position: absolute;
	display: flex;
	flex-direction: column;
	min-height: 0;
	overflow: hidden;
	box-sizing: border-box;
}

<#--
  §17.10 컨테이너는 **항상** 자기 레이어 공간을 연다.
  근거: .frg-fc-item 은 position:absolute 라, 부모에 z-index 가 붙은 경우에만 스태킹 컨텍스트가
  생긴다. 그 결과 부모의 layoutZ 유무에 따라 같은 구조가 다르게 그려졌다 — 부모에 z 가 있으면
  자식 z 가 부모 안에 갇히고, 없으면 자식 z 가 루트와 직접 경쟁해 부모의 형제를 덮었다.
  isolation:isolate 는 z-index 를 건드리지 않고 스태킹 컨텍스트만 만든다. 이 한 줄로
  **layoutZ 의 의미가 "형제 사이의 순서"로 확정**된다(부모 밖으로 새지 않는다).
-->
#${stem}-canvas .frg-fc-container {
	isolation: isolate;
}

#${stem}-canvas .frg-fc-item > * {
	flex: 1 1 auto;
	min-height: 0;
}

<#-- §17.8 중첩 컨테이너(PANEL). 자식 .frg-fc-item 은 absolute 라 이 상자가 곧 좌표 원점이 된다
     (부모가 absolute 이므로 컨테이닝 블록이 성립 — 좌표 변환 코드가 필요 없다). -->
#${stem}-canvas .frg-fc-panel {
	position: absolute;
	inset: 0;
	box-sizing: border-box;
	border-radius: 4px;
}

<#--
  §17.12 컨테이너의 내용 상자. 자식의 기준 상자가 되며 **테두리가 없다** — 패널 div 안에 자식을
  넣으면 기준 상자가 패널의 패딩 박스가 되어 좌표가 테두리 두께만큼(그리고 borderYn 에 따라
  달라지며) 밀린다. 이 상자는 래퍼와 같은 사각형이라 좌표가 그대로다.
-->
#${stem}-canvas .frg-fc-panel-body {
	position: absolute;
	inset: 0;
	border: 0;
}

#${stem}-canvas .frg-fc-panel-bordered {
	border: 1px solid #d5dce5;
}

#${stem}-canvas .frg-fc-panel-filled {
	background: #f7f9fc;
}

#${stem}-canvas .frg-fc-panel-title {
	position: absolute;
	top: -9px;
	left: 10px;
	padding: 0 5px;
	background: #fff;
	color: #6b7484;
	font-size: 12px;
}

<#-- 원자 컨트롤 기본 표현(§17.1 CONTROL). 전부 리터럴 규칙 — 데이터 유입 0. -->
#${stem}-canvas .frg-fc-button {
	cursor: pointer;
	border-radius: 4px;
	border: 1px solid #c9d2de;
	background: #f4f6f9;
	color: #2c3444;
}

#${stem}-canvas .frg-fc-button-primary {
	border-color: #2f6fdc;
	background: #3d7ef0;
	color: #fff;
}

#${stem}-canvas .frg-fc-button-danger {
	border-color: #c8372d;
	background: #dc4437;
	color: #fff;
}

#${stem}-canvas .frg-fc-label {
	display: flex;
	align-items: center;
}

#${stem}-canvas .frg-fc-label-title {
	font-size: 18px;
	font-weight: 700;
}

#${stem}-canvas .frg-fc-label-caption {
	font-size: 12px;
	color: #6b7484;
}

#${stem}-canvas .frg-fc-field {
	display: flex;
	flex-direction: column;
	gap: 4px;
}

#${stem}-canvas .frg-fc-field input {
	width: 100%;
	box-sizing: border-box;
}

#${stem}-canvas .frg-fc-image {
	width: 100%;
	height: 100%;
}

#${stem}-canvas .frg-fc-image-contain { object-fit: contain; }

#${stem}-canvas .frg-fc-image-cover { object-fit: cover; }

#${stem}-canvas .frg-fc-image-fill { object-fit: fill; }

<#-- §17.9 캔버스판 POPUP_FORM — 오버레이가 아니라 고정 크기 상자다(자리를 차지하고 다른 부품을
     가리지 않는다). 공통 popup CSS 는 오버레이 전제라 여기서 상자 형태만 따로 준다. -->
#${stem}-canvas .frg-fc-popup {
	display: flex;
	flex-direction: column;
	height: 100%;
	box-sizing: border-box;
	border: 1px solid #d5dce5;
	border-radius: 6px;
	background: #fff;
	overflow: hidden;
}

#${stem}-canvas .frg-fc-popup-head {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 10px 14px;
	border-bottom: 1px solid #e5eaf1;
	font-weight: 700;
}

#${stem}-canvas .frg-fc-popup-head .close {
	border: 0;
	background: transparent;
	cursor: pointer;
	font-size: 16px;
	line-height: 1;
}

#${stem}-canvas .frg-fc-popup-body {
	flex: 1 1 auto;
	min-height: 0;
	overflow: auto;
	padding: 12px 14px;
}

#${stem}-canvas .frg-fc-popup-foot {
	display: flex;
	justify-content: flex-end;
	gap: 8px;
	padding: 10px 14px;
	border-top: 1px solid #e5eaf1;
}

<#-- §17.9 캔버스판 LAYOUT_FRAME — 다른 화면을 불러올 자리. src 는 도메인이 채운다. -->
#${stem}-canvas .frg-fc-frame {
	width: 100%;
	height: 100%;
	border: 1px solid #d5dce5;
	border-radius: 4px;
	background: #fff;
}
<#-- §17.8 중첩: shell.ftl 과 같은 canvasTree 를 같은 순서로 걷는다(seq 는 서버가 부여한 정수). -->
<#macro fcRules items>
<#list items as inst>
<#assign props = (inst.props)!{}>
<#if (props["layoutXPx"])?? && props["layoutXPx"]?is_number && (props["layoutXPx"] >= 0) && (props["layoutXPx"] <= 4000)
  && (props["layoutYPx"])?? && props["layoutYPx"]?is_number && (props["layoutYPx"] >= 0) && (props["layoutYPx"] <= 8000)
  && (props["layoutWPx"])?? && props["layoutWPx"]?is_number && (props["layoutWPx"] >= 20) && (props["layoutWPx"] <= 4000)
  && (props["layoutHPx"])?? && props["layoutHPx"]?is_number && (props["layoutHPx"] >= 20) && (props["layoutHPx"] <= 4000)>

#${stem}-canvas .frg-fc-${inst.seq?c} {
	left: ${props["layoutXPx"]?c}px;
	top: ${props["layoutYPx"]?c}px;
	width: ${props["layoutWPx"]?c}px;
	height: ${props["layoutHPx"]?c}px;
<#if (props["layoutZ"])?? && props["layoutZ"]?is_number && (props["layoutZ"] >= 0) && (props["layoutZ"] <= 999)>
	z-index: ${props["layoutZ"]?c};
</#if>
}
</#if>
<#if (inst.children)?? && (inst.children?size gt 0)>
<@fcRules items=inst.children/>
</#if>
</#list>
</#macro>
<@fcRules items=nodes/>
