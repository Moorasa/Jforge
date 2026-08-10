<#-- P4 MVP: MGMT_LIST_DETAIL+TABLE_VIEW 1뷰. 다른 뷰/모듈은 P5. -->
<#--
  아티팩트: ListTableView (CSS) → {stem}ListTableView.css (계약 §1.1 #7)
  역할: 이 화면 TABLE_VIEW의 최소 스코프 스타일. 공통 테이블 스타일은 번들 commonListTableView.css.
  🔒 자유문자열(col.name/displayName/styleClass) 미삽입 — 고정 셀렉터만. 배너 0.
-->
#table-view.table-view {
	display: flex;
	flex-direction: column;
	gap: 8px;
}

#table-view .table-view-actions {
	display: flex;
	justify-content: flex-end;
	gap: 8px;
}

#table-view .layout-body {
	overflow: auto;
}

#table-view table {
	width: 100%;
	border-collapse: collapse;
}

#table-view .col-select {
	width: 40px;
	text-align: center;
}

#table-view .pagination {
	display: flex;
	justify-content: center;
}
