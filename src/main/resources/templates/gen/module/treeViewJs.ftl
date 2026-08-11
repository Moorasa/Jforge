<#-- P5-3: MGMT_LIST_DETAIL+TREE_VIEW 뷰. JWorks commonListTreeView.js 1:1 배선. -->
<#--
  아티팩트: ListTreeView (JS) → {stem}ListTreeView.js (계약 §8.2)
  역할: 번들 런타임 window.JWorks_JSCommonListTreeView(commonListTreeView.js) API에 배선.
        - treeView.init = function(options)(33행) → init({ $container, apiInfo, features,
          callbacks, dataMapping, search, orderingState }) 호출.
        - 필수: options.$container(34행 유효성 검사). 선택: apiInfo(40행: parser 494행/renderCallback 461행/
          emptyMainText·emptySubText 270~271행), features(41행: checkbox/ordering/getNodeData),
          callbacks(42행: onNodeClick 108행/onCheckChange 129행), dataMapping(43행: root.name 486행/
          root.iconClass 485행), search(44행: resetBehavior 202행), orderingState(50행: ordering 활성 시 필수).
        - config(labelField/idField/parentField/iconField)를 뷰 네임스페이스에 노출(parser/renderCallback 배선용).
  🔒 자유문자열 전량 GenEscaper 경유(계약 §8.4 Tree 문맥표):
    - labelField/idField/parentField/iconField → jsString(JS 문자열 config)
    - rootLabel                                → jsString(dataMapping.root.name 문자열)
    - rootIconClass                            → cssToken(dataMapping.root.iconClass 클래스 토큰)
  iconField는 URL이 아니라 데이터 필드명(런타임이 경로 조립), rootIconClass는 cssToken 검증
  클래스 토큰(root.iconClass) — URL 직접수신 props 없음(§8.4).
  selectMode → selectionType('single'|'checkbox'). IIFE + __defined 골격. 스크립트릿 0 / 배너 0.

  ⚠ 배선점(TODO, 개발자 채움): apiInfo.url / param / parser / renderCallback / emptyMainText /
     emptySubText / features.getNodeData 는 도메인별 실제 값으로 교체. parser(res)는 서버 응답을
     표준 노드(nodeNo/name/originalData/children/iconClass)로 변환(commonListTreeView.js 494·516행),
     renderCallback(res)는 후처리 콜백(461행). config를 참조해 labelField 등 바인딩.
-->
<#assign tvInst = slots["listArea"][0]>
<#assign props = tvInst.props>
<#assign selectMode = (props["selectMode"])!"single">
<#assign labelField = (props["labelField"])!"">
<#assign idField = (props["idField"])!"">
<#assign parentField = (props["parentField"])!"">
<#assign iconField = (props["iconField"])!"">
<#assign rootLabel = (props["rootLabel"])!"">
<#assign rootIconClass = cssToken((props["rootIconClass"])!"")>
<#assign searchYn = (props["searchYn"])!false>
<#assign orderingYn = (props["orderingYn"])!false>
<#assign Domain = stem?cap_first>
<#assign Role = role?cap_first>
<#assign NS = "JWorks_JS" + Domain + Role + "TreeView">
window.${NS} = window.${NS} || {};
(function(view) {
	"use strict";

	if (view.__defined) {
		return;
	}
	view.__defined = true;

	// TREE_VIEW 배선 설정(이스케이프된 리터럴). parser/renderCallback이 계층 바인딩 시 참조.
	var config = {
		labelField: "${jsString(labelField)}",
		idField: "${jsString(idField)}",
		parentField: "${jsString(parentField)}",
		iconField: "${jsString(iconField)}"
	};

	// 루트 노드 매핑(commonListTreeView.js 485~486행 root.name/root.iconClass).
	// rootIconClass는 cssToken 화이트리스트 통과 토큰(위반 시 빈 문자열).
	var rootMapping = {
		name: "${jsString(rootLabel)}",
		iconClass: "${jsString(rootIconClass)}"
	};

	// selectMode → 런타임 selectionType. 'checkbox'면 features.checkbox 활성.
	var selectionType = "${jsString(selectMode)}";
	var orderingEnabled = <#if orderingYn>true<#else>false</#if>;

	view.getConfig = function() {
		return config;
	};

	function init() {
		var $container = $("#tree-view");
		if (!$container.length) {
			$container = $("section#tree-view");
		}

		// 번들 런타임(commonListTreeView.js) 배선. init 시그니처: init(options)(33행).
		// 필수 $container(34행), 선택 apiInfo(40행)/features(41행)/callbacks(42행)/
		// dataMapping(43행)/search(44행)/orderingState(50행). apiInfo는 도메인별로 채우는 배선점.
		JWorks_JSCommonListTreeView.init({
			$container: $container,
			apiInfo: {
				// TODO(배선): 실제 트리 조회 API 및 콜백으로 교체.
				url: "",
				param: {},
				emptyMainText: "데이터가 없습니다.",
				emptySubText: "",
				parser: function(data) {
					// TODO(배선): 서버 응답 → 표준 노드 배열 변환(config.idField/parentField/labelField 참조).
					return [];
				},
				renderCallback: function(res) {
					// TODO(배선): 트리 렌더 후처리(commonListTreeView.js 461행).
				}
			},
			features: {
				checkbox: { enable: (selectionType === "checkbox") },
				ordering: { enable: orderingEnabled }
			},
			callbacks: {
				onNodeClick: function(nodeData, e) {
					// TODO(배선): 노드 클릭 처리(상세 이동 등, commonListTreeView.js 108행).
				},
				onCheckChange: function(e, nodeData, $checkbox) {
					// TODO(배선): 체크 변경 처리(commonListTreeView.js 129행).
				}
			},
			dataMapping: {
				root: rootMapping
			},
			search: {
				// resetBehavior: 빈 검색어 복원 방식('expandAll' 등, commonListTreeView.js 202행).
			}<#if orderingYn>,
			orderingState: { ordering: {}, orderingUpdate: {}, orderingOrigin: {} }</#if>
		});
	}

	// ===============================================================================================
	// Public API
	view.init = init;

	$(function() {
		init();
	});

})(window.${NS});
