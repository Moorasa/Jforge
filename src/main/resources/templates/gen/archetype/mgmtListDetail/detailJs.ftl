<#-- P5.5a/b: MGMT_LIST_DETAIL 상세영역 JS. JWorks commonSection.js 1:1 배선(계약 §9). -->
<#--
  아티팩트: Detail (JS) → {stem}Detail.js (계약 §9.2)
  역할: window.JWorks_JS{Domain}{Role}Detail 네임스페이스 + IIFE + __defined 골격. 번들 런타임
        window.JWorks_JSCommonSection(commonSection.js) API에 배선.
    - registEventBasicInfo(info)(88행) → info.{editCallback/deleteCallback/saveCallback/cancelCallback}
      (전부 도메인 채움 TODO 배선점). detailBasic 존재 시에만 호출.
    - applyAssociateTabsVisibilityByClass($associate)(223행) → registEventAssociateInfo 전 호출(문서 규약).
    - registEventAssociateInfo(info)(247행) → info.{$container, tabs:[{tabClass,frameId,location}]}.
      detailTabs 존재 시에만 호출. tab.location(iframe src)은 도메인 채움 TODO 배선점(§9 (B)).
  🔒 자유문자열 전량 GenEscaper 경유(계약 §9.3):
    - tab.tabClass → cssToken → jsString(JS 문자열; treeView rootIconClass 선례 동형)
    - tab.frameId  → jsString(JS 문자열; getElementById 배선)
  URL 직접수신 props 없음(§9.3): tab.location은 산출 시 ""(도메인 배선). 스크립트릿 0 / 배너 0.
  🔒 role/stem은 TemplateContextBuilder(§5.1/§1.1) 화이트리스트 재검증 구조값 → NS 식별자 안전.
-->
<#assign Domain = stem?cap_first>
<#assign Role = role?cap_first>
<#assign NS = "JWorks_JS" + Domain + Role + "Detail">
<#assign hasBasic = (slots["detailBasic"])?? && (slots["detailBasic"]?size > 0)>
<#assign hasTabs = (slots["detailTabs"])?? && (slots["detailTabs"]?size > 0)>
window.${NS} = window.${NS} || {};
(function(detail) {
	"use strict";

	if (detail.__defined) {
		return;
	}
	detail.__defined = true;

<#if hasTabs>
	<#-- 다중 detailTabs 인스턴스의 tabs[]를 하나의 시퀀스로 평탄화(?has_next 구분자용). -->
	<#assign allTabs = []>
	<#list slots["detailTabs"] as tabsInst>
		<#assign allTabs = allTabs + (tabsInst.props["tabs"])![]>
	</#list>
	// 연관 탭 배선 목록(이스케이프된 리터럴). tabClass=클래스 토큰, frameId=iframe id.
	// location(iframe 로드 URL)은 도메인별 실제 화면 URL로 교체하는 배선점(§9 (B)).
	var associateTabs = [
<#list allTabs as tab>
		{ tabClass: "${jsString(cssToken(tab["tabClass"]!""))}", frameId: "${jsString(tab["frameId"]!"")}", location: "" /* TODO(배선): 연관 화면 URL */ }<#if tab?has_next>,</#if>
</#list>
	];

	detail.getAssociateTabs = function() {
		return associateTabs;
	};
</#if>

	function init() {
<#if hasBasic>
		// 기본정보 섹션 이벤트 배선(commonSection.js registEventBasicInfo 88행).
		// editCallback/deleteCallback/saveCallback/cancelCallback은 도메인별 구현으로 교체하는 배선점.
		JWorks_JSCommonSection.registEventBasicInfo({
			editCallback: function() {
				// TODO(배선): 수정 모드 진입 시 처리(폼 값 로드 등).
			},
			deleteCallback: function() {
				// TODO(배선): 삭제 처리.
			},
			saveCallback: function() {
				// TODO(배선): 수정 저장 처리.
			},
			cancelCallback: function() {
				// TODO(배선): 수정 취소 시 처리(폼 값 원복 등).
			}
		});
</#if>
<#if hasTabs>
		var $associate = $("section#associate-info");
		// 납품처 설정(TAB_VISIBILITY)에 따른 탭 제거(commonSection.js 223행) — registEventAssociateInfo 전 호출.
		JWorks_JSCommonSection.applyAssociateTabsVisibilityByClass($associate);
		// 연관 탭 이벤트 배선(commonSection.js registEventAssociateInfo 247행).
		// 탭 클릭 시 iframe이 tab.location으로 location.replace(310행)되므로 location 배선이 필수.
		JWorks_JSCommonSection.registEventAssociateInfo({
			$container: $associate,
			tabs: associateTabs
		});
</#if>
	}

	// ===============================================================================================
	// Public API
	detail.init = init;

	$(function() {
		init();
	});

})(window.${NS});
