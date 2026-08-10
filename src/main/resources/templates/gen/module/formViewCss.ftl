<#-- P5-4: MGMT_LIST_DETAIL+FORM_VIEW 뷰. -->
<#--
  아티팩트: ListFormView (CSS) → {stem}ListFormView.css (계약 §8.2)
  역할: 이 화면 FORM_VIEW의 최소 스코프 스타일. 공통 폼 스타일은 번들 commonListFormView.css.
  🔒 자유문자열(field.name/label/styleClass 등) 미삽입 — 고정 셀렉터만. 배너 0.
-->
#form-view .layout-body .form-view-form {
	display: flex;
	flex-direction: column;
	gap: 12px;
}

#form-view .form-field {
	display: flex;
	flex-direction: column;
	gap: 4px;
}

#form-view .form-field-label {
	font-weight: 600;
}

#form-view .form-field-label .required-mark {
	color: #dc2626;
}

#form-view .form-field-input {
	width: 100%;
	box-sizing: border-box;
}

#form-view .empty-case {
	width: 100%;
	text-align: center;
}
