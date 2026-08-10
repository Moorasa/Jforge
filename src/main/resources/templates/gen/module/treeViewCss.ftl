<#-- P5-3: MGMT_LIST_DETAIL+TREE_VIEW 뷰. -->
<#--
  아티팩트: ListTreeView (CSS) → {stem}ListTreeView.css (계약 §8.2)
  역할: 이 화면 TREE_VIEW의 최소 스코프 스타일. 공통 트리 스타일은 번들 commonListTreeView.css.
  🔒 자유문자열(labelField/rootLabel/treeStyleClass 등) 미삽입 — 고정 셀렉터만. 배너 0.
-->
#tree-view.on {
	display: block;
}

#tree-view .layout-body {
	display: flex;
	flex-direction: column;
}

#tree-view .layout-body .tree-list {
	list-style: none;
	margin: 0;
	padding: 0;
}

#tree-view .layout-body .tree-node-label {
	display: flex;
	align-items: center;
}

#tree-view .layout-body .tree-node-label.selected {
	font-weight: bold;
}

#tree-view .empty-case {
	width: 100%;
	text-align: center;
}
