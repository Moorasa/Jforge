<#-- P4 MVP: MGMT_LIST_DETAIL+TABLE_VIEW 1뷰. 다른 뷰/모듈은 P5. -->
<#--
  아티팩트: List (CSS) → {stem}List.css (계약 §1.1 #4)
  역할: List 컨테이너 레이아웃 최소 골격. 뷰 상세 스타일은 번들 commonList*.css 및 {stem}ListTableView.css.
  🔒 자유문자열(styleClass 등) 미삽입 — 고정 셀렉터만. 배너 0.
-->
#${stem}-list.common-list {
	display: flex;
	flex-direction: column;
	gap: 12px;
}

#${stem}-list .search {
	display: flex;
	align-items: center;
	gap: 8px;
}

#${stem}-list .list-toolbar {
	display: flex;
	justify-content: flex-end;
	gap: 8px;
}

#${stem}-list .list-area {
	flex: 1 1 auto;
	min-height: 0;
}
<#-- §13 인스턴스 레이아웃(P8): props.layoutWidthPct(10~100)/layoutHeightPx(40~2000) — 숫자·유효범위 통과 시에만 산출.
     🔒 ?is_number 게이트 + ?c 포맷만(문자열은 절대 미삽입, 악성값 0바이트). 셀렉터는 정적 리터럴 맵만.
     키가 없으면 이 블록 전체가 0바이트 → 기존 골든 바이트 동일(whitespace_stripping). -->
<#macro layoutRules sel props>
<#if (props["layoutWidthPct"])?? && props["layoutWidthPct"]?is_number && (props["layoutWidthPct"] >= 10) && (props["layoutWidthPct"] <= 100)>

${sel} {
	width: ${props["layoutWidthPct"]?c}%;
}
</#if>
<#if (props["layoutHeightPx"])?? && props["layoutHeightPx"]?is_number && (props["layoutHeightPx"] >= 40) && (props["layoutHeightPx"] <= 2000)>

${sel} {
	height: ${props["layoutHeightPx"]?c}px;
	overflow-y: auto;
}
</#if>
</#macro>
<#if (slots["searchArea"])?? && (slots["searchArea"]?size > 0) && (slots["searchArea"][0].props)??>
<@layoutRules sel="#${stem}-list .search" props=slots["searchArea"][0].props/>
</#if>
<#if (slots["listToolbar"])?? && (slots["listToolbar"]?size > 0) && (slots["listToolbar"][0].props)??>
<@layoutRules sel="#${stem}-list .list-toolbar" props=slots["listToolbar"][0].props/>
</#if>
<#if (slots["listArea"])?? && (slots["listArea"]?size > 0) && (slots["listArea"][0].props)??>
<@layoutRules sel="#${stem}-list .list-area" props=slots["listArea"][0].props/>
</#if>
