<#-- P4 MVP: MGMT_LIST_DETAIL+TABLE_VIEW 1뷰. 다른 뷰/모듈은 P5. -->
<#--
  아티팩트: ListTableView (JS) → {stem}ListTableView.js (계약 §1.1 #6)
  역할: 번들 런타임 window.JWorks_JSCommonListTableView(commonListTableView.js) API에 배선.
        - init({ $container, apiInfo, selectionType, tableType }) 호출(시그니처는 commonListTableView.js 확인).
        - columns 정의 배열(name/displayName/displayYn/sortYn)을 뷰 네임스페이스에 노출(헤더/엑셀 참조용).
  🔒 자유문자열 전량 GenEscaper 경유:
    - col.name        → jsString
    - col.displayName → jsString
  selectMode → selectionType('checkbox'|'radio'|'none'). pagingYn/excelYn/csvYn는 apiInfo 배선점.
  IIFE + __defined 골격. 스크립트릿 0 / 배너 0.

  ⚠ 배선점(TODO, 파이프라인/개발자 채움): apiInfo.url / defaultSort / param / renderCallback /
     emptyMainText / emptySubText / excelDownloadUrl / csvUploadUrl 은 도메인별 실제 값으로 교체.
-->
<#assign tvInst = slots["listArea"][0]>
<#assign props = tvInst.props>
<#assign columns = (props["columns"])![]>
<#assign selectMode = (props["selectMode"])!"none">
<#assign Domain = stem?cap_first>
<#assign Role = role?cap_first>
<#assign NS = "JWorks_JS" + Domain + Role + "TableView">
window.${NS} = window.${NS} || {};
(function(view) {
	"use strict";

	if (view.__defined) {
		return;
	}
	view.__defined = true;

	// TABLE_VIEW props.columns 정의(이스케이프된 리터럴). 헤더/엑셀/컬럼가시성 참조용.
	var columns = [
<#list columns as col>
		{ name: "${jsString(col["name"]!"")}", displayName: "${jsString(col["displayName"]!"")}", displayYn: <#if (col["displayYn"]!true)>true<#else>false</#if>, sortYn: <#if (col["sortYn"]!false)>true<#else>false</#if> }<#if col?has_next>,</#if>
</#list>
	];

	// selectMode → 런타임 selectionType. 'none'은 선택 컬럼 없음.
	var selectionType = "${jsString(selectMode)}";

	view.getColumns = function() {
		return columns;
	};

	function init() {
		var $container = $("#table-view");
		if (!$container.length) {
			$container = $("section#table-view");
		}

		// 번들 런타임(commonListTableView.js) 배선. apiInfo는 도메인별로 채우는 배선점.
		JWorks_JSCommonListTableView.init({
			$container: $container,
			selectionType: selectionType,
			tableType: "",
			apiInfo: {
				// TODO(배선): 실제 목록 조회 API 및 콜백으로 교체.
				url: "",
				param: {},
				defaultSort: { item: (columns[0] ? columns[0].name : ""), order: "asc" },
				emptyMainText: "데이터가 없습니다.",
				emptySubText: "",
				excelDownloadUrl: "",
				csvUploadUrl: "",
				renderCallback: function(res) {
					// TODO(배선): tbody 행 렌더는 도메인별 구현.
				}
			},
			callbacks: {}
		});
	}

	// ===============================================================================================
	// Public API
	view.init = init;

	$(function() {
		init();
	});

})(window.${NS});
