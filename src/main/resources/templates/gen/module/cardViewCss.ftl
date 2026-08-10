<#-- P5-2: MGMT_LIST_DETAIL+CARD_VIEW 뷰. -->
<#--
  아티팩트: ListCardView (CSS) → {stem}ListCardView.css (계약 §8.2)
  역할: 이 화면 CARD_VIEW의 최소 스코프 스타일. 공통 카드 스타일은 번들 commonListCardView.css.
  🔒 자유문자열(col.name/displayName/titleField/cardStyleClass) 미삽입 — 고정 셀렉터만. 배너 0.
-->
#card-view.on {
	display: block;
}

#card-view .layout-body {
	display: flex;
	flex-direction: row;
	flex-wrap: wrap;
	gap: 0.625rem;
}

#card-view .layout-body .card {
	display: flex;
	flex-direction: column;
}

#card-view .empty-case {
	width: 100%;
	text-align: center;
}

#card-view .layout-footer .pagination {
	display: flex;
	justify-content: center;
}
