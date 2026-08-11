<#-- P5-2: MGMT_LIST_DETAIL+CARD_VIEW 뷰. JWorks commonListCardView.js 1:1 배선. -->
<#--
  아티팩트: ListCardView (JS) → {stem}ListCardView.js (계약 §8.2)
  역할: 번들 런타임 window.JWorks_JSCommonListCardView(commonListCardView.js) API에 배선.
        - cardView.init = function(options)(43행) → init({ $container, apiInfo,
          searchCallback, sortCallback, categoryChangeCallback }) 호출.
        - 필수: options.$container(44행 유효성 검사). 선택: apiInfo(51행),
          searchCallback(54행)/sortCallback(78행)/categoryChangeCallback(91행).
        - config(titleField/subtitleField/imageField/columns)를 뷰 네임스페이스에 노출(renderCallback 배선용).
  🔒 자유문자열 전량 GenEscaper 경유(계약 §8.4 Card 문맥표):
    - titleField/subtitleField/imageField → jsString(JS 문자열 config)
    - col.name / col.displayName          → jsString
  imageField는 URL이 아니라 데이터 필드명(런타임이 경로 조립) — URL 직접수신 props 없음(§8.4).
  selectMode → selectionType('checkbox'|'radio'|'none'). IIFE + __defined 골격. 스크립트릿 0 / 배너 0.

  ⚠ 배선점(TODO, 개발자 채움): apiInfo.url / defaultSort / param / renderCallback /
     emptyMainText / emptySubText 는 도메인별 실제 값으로 교체. renderCallback이 .layout-body에
     개별 카드 마크업을 채운다(commonListCardView.js 243·262행). config를 참조해 titleField 등 바인딩.
-->
<#assign cvInst = slots["listArea"][0]>
<#assign props = cvInst.props>
<#assign columns = (props["columns"])![]>
<#assign selectMode = (props["selectMode"])!"none">
<#assign titleField = (props["titleField"])!"">
<#assign subtitleField = (props["subtitleField"])!"">
<#assign imageField = (props["imageField"])!"">
<#assign Domain = stem?cap_first>
<#assign Role = role?cap_first>
<#assign NS = "JWorks_JS" + Domain + Role + "CardView">
window.${NS} = window.${NS} || {};
(function(view) {
	"use strict";

	if (view.__defined) {
		return;
	}
	view.__defined = true;

	// CARD_VIEW 배선 설정(이스케이프된 리터럴). renderCallback이 카드 바인딩 시 참조.
	var config = {
		titleField: "${jsString(titleField)}",
		subtitleField: "${jsString(subtitleField)}",
		imageField: "${jsString(imageField)}"
	};

	// 카드 본문 항목 정의(이스케이프된 리터럴). 라벨/가시성 참조용.
	var columns = [
<#list columns as col>
		{ name: "${jsString(col["name"]!"")}", displayName: "${jsString(col["displayName"]!"")}", displayYn: <#if (col["displayYn"]!true)>true<#else>false</#if>, sortYn: <#if (col["sortYn"]!false)>true<#else>false</#if> }<#if col?has_next>,</#if>
</#list>
	];

	// selectMode → 런타임 selectionType. 'none'은 선택 없음.
	var selectionType = "${jsString(selectMode)}";

	view.getConfig = function() {
		return config;
	};

	view.getColumns = function() {
		return columns;
	};

	function init() {
		var $container = $("#card-view");
		if (!$container.length) {
			$container = $("section#card-view");
		}

		// 번들 런타임(commonListCardView.js) 배선. init 시그니처: init(options)(43행).
		// 필수 $container(44행), 선택 apiInfo(51행)/searchCallback(54행)/sortCallback(78행)/
		// categoryChangeCallback(91행). apiInfo는 도메인별로 채우는 배선점.
		JWorks_JSCommonListCardView.init({
			$container: $container,
			apiInfo: {
				// TODO(배선): 실제 목록 조회 API 및 콜백으로 교체.
				url: "",
				param: {},
				defaultSort: { item: (columns[0] ? columns[0].name : (config.titleField || "")), order: "asc" },
				emptyMainText: "데이터가 없습니다.",
				emptySubText: "",
				renderCallback: function(res) {
					// TODO(배선): .layout-body 카드 렌더는 도메인별 구현(config.titleField 등 참조).
				}
			},
			searchCallback: function() {
				// TODO(배선): 검색 실행(cardView.getList 등).
			},
			sortCallback: function(sort) {
				// TODO(배선): 정렬 적용({ item, order }).
			},
			categoryChangeCallback: function() {
				// TODO(배선): 카테고리 변경 처리.
			}
		});
	}

	// ===============================================================================================
	// Public API
	view.init = init;

	$(function() {
		init();
	});

})(window.${NS});
