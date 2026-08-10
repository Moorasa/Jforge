<#-- P5.5a/b: MGMT_LIST_DETAIL 상세영역 CSS. 고정 셀렉터만(자유문자열 0). -->
<#--
  아티팩트: Detail (CSS) → {stem}Detail.css (계약 §9.2)
  역할: 상세영역(.detail-area) 레이아웃 보강. #basic-info/#associate-info 세부 스타일은 번들
        commonSection.css(header.jsp 매니페스트 20행)가 제공 — 여기서는 컨테이너/모드 토글만.
  🔒 props 자유문자열 삽입 0(고정 셀렉터·고정 값만). 스크립트릿 0 / 배너 0.
-->
.detail-area {
	display: flex;
	flex-direction: column;
	gap: 16px;
	margin-top: 16px;
}

.detail-area .detail-toolbar {
	display: flex;
	justify-content: flex-end;
	gap: 8px;
}

<#-- 보기/수정 모드 토글: commonSection.js가 #basic-info의 view-mode/edit-mode 클래스를 전환한다. -->
.detail-area #basic-info .detail-info-edit {
	display: none;
}

.detail-area #basic-info.edit-mode .detail-info-view {
	display: none;
}

.detail-area #basic-info.edit-mode .detail-info-edit {
	display: block;
}

.detail-area #basic-info .buttons-edit {
	display: none;
}

.detail-area #basic-info.edit-mode .buttons {
	display: none;
}

.detail-area #basic-info.edit-mode .buttons-edit {
	display: flex;
}

<#-- 접기: commonSection.js가 collapse 클래스를 토글(button-detail-collapse/expand). -->
.detail-area #basic-info.collapse .detail-info-view,
.detail-area #basic-info.collapse .detail-info-edit {
	display: none;
}

<#-- 연관 탭: 활성 탭(.on)/활성 iframe(.on)만 노출. commonSection.js가 .on을 전환. -->
.detail-area #associate-info .tabs {
	display: flex;
	gap: 4px;
}

.detail-area #associate-info .contents iframe {
	display: none;
	width: 100%;
	border: 0;
}

.detail-area #associate-info .contents iframe.on {
	display: block;
}
