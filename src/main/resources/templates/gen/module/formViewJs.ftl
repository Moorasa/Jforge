<#-- P5-4: MGMT_LIST_DETAIL+FORM_VIEW 뷰. MagicIAM commonListFormView.js 1:1 배선. -->
<#--
  아티팩트: ListFormView (JS) → {stem}ListFormView.js (계약 §8.2)
  역할: 번들 런타임 window.MagicIAM_JSCommonListFormView(commonListFormView.js) API에 배선.
        - formView.init = function(options)(29행) → init({ $container, apiInfo, selectionType }) 호출.
        - 필수: options.$container(31행 유효성 검사). 선택: apiInfo(37행: url·renderCallback),
          selectionType(38행, 기본 'checkbox' — 'checkbox'면 #select-all/.row-checkbox 이벤트 활성 42행).
        - config(fields[])를 뷰 네임스페이스에 노출(renderCallback/도메인 바인딩 참조용).
  🔒 자유문자열 전량 GenEscaper 경유(계약 §8.4 Form 문맥표):
    - field.name / field.label / field.type / field.styleClass → jsString(JS 문자열 config)
    - selectionType                                            → jsString(selectionType 문자열)
  URL 직접수신 props 없음(§8.4). IIFE + __defined 골격. 스크립트릿 0 / 배너 0.

  ⚠ 배선점(TODO, 개발자 채움): apiInfo.url / param / renderCallback 는 도메인별 실제 값으로 교체.
     commonListFormView.js render(data)는 비어 있어(127행) 폼 본문은 산출 JSP 정적 골격이 담당하며,
     renderCallback(res)(124행)에서 도메인별 값 바인딩(config.fields 참조).
-->
<#assign fvInst = slots["listArea"][0]>
<#assign props = fvInst.props>
<#assign selectionType = (props["selectionType"])!"checkbox">
<#assign fields = (props["fields"])![]>
<#assign Domain = stem?cap_first>
<#assign Role = role?cap_first>
<#assign NS = "MagicIAM_JS" + Domain + Role + "FormView">
window.${NS} = window.${NS} || {};
(function(view) {
	"use strict";

	if (view.__defined) {
		return;
	}
	view.__defined = true;

	// FORM_VIEW 배선 설정(이스케이프된 리터럴). renderCallback/도메인 바인딩 시 참조.
	// type은 산출 JSP에서 허용목록 리터럴 매핑으로 검증되며, 여기 config는 jsString 리터럴이다.
	var config = {
		fields: [
<#list fields as field>
			{ name: "${jsString(field["name"]!"")}", label: "${jsString(field["label"]!"")}", type: "${jsString(field["type"]!"text")}", requiredYn: <#if (field["requiredYn"]!false)>true<#else>false</#if>, styleClass: "${jsString(field["styleClass"]!"")}" }<#if field?has_next>,</#if>
</#list>
		]
	};

	// selectionType → commonListFormView.js 38행 selectionType('checkbox'|'none').
	var selectionType = "${jsString(selectionType)}";

	view.getConfig = function() {
		return config;
	};

	function init() {
		var $container = $("#form-view");
		if (!$container.length) {
			$container = $("section#form-view");
		}

		// 번들 런타임(commonListFormView.js) 배선. init 시그니처: init(options)(29행).
		// 필수 $container(31행), 선택 apiInfo(37행)/selectionType(38행). apiInfo는 도메인별 배선점.
		MagicIAM_JSCommonListFormView.init({
			$container: $container,
			apiInfo: {
				// TODO(배선): 실제 폼 데이터 조회 API 및 콜백으로 교체.
				url: "",
				param: {},
				renderCallback: function(res) {
					// TODO(배선): 폼 값 바인딩은 도메인별 구현(config.fields 참조).
				}
			},
			selectionType: selectionType
		});
	}

	// ===============================================================================================
	// Public API
	view.init = init;

	$(function() {
		init();
	});

})(window.${NS});
