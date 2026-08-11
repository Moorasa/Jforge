-- =====================================================================
-- J-FORGE 모듈타입 시드 (P5.5a/b) — DETAIL_BASIC·ASSOCIATE_TABS add-only
-- 대상 테이블: TB_FRG_MODULE_TYPE (V1 DDL에서 이미 생성됨 — 재생성 금지, 시드 전용)
-- 멱등(ON CONFLICT (MODULE_TYPE_CODE) DO NOTHING). 재실행 시 중복/오류 0.
-- add-only: V3(TABLE_VIEW/SEARCH_FILTER_BAR/TOOLBAR)·V4(CARD/TREE/FORM_VIEW)는 재실행·수정하지 않는다.
-- 근거: static/js/admin/common/commonSection.js (JWorks 1:1, 계약 §9.1).
--   ⚠ commonSection.js는 상세 값(기본정보 값·연관 iframe 로드)을 정적 props가 아니라 런타임/도메인
--     콜백으로 채운다(registEventBasicInfo 88행 editCallback 등, registEventAssociateInfo 310행
--     iframe.contentWindow.location.replace(tab.location)). 따라서 아래 props는 "어떤 필드/탭을
--     어떤 라벨·바인딩으로 그릴지"의 배선 힌트 설정 props다(§9.3 주의).
--   ⚠ URL 직접수신 props 없음(§9.3): ASSOCIATE_TABS tabs[].location(iframe src)은 props가 아니라
--     산출 후 도메인이 채우는 배선점이다. tabClass는 cssToken 클래스 토큰, frameId는 DOM id.
--   ⚠ detailToolbar 슬롯은 기존 TOOLBAR(ACTION, V3) 재사용 — 신규 모듈 없음(계약 §9.1).
-- PROP_SCHEMA_JSON: 규약 docs/스키마_PROP_SCHEMA.md 준수, ::jsonb 캐스팅.
-- CATEGORY_CODE 'DETAIL'은 V2 MODULE_CATEGORY 그룹 내 코드(4행). ArchetypeSlots detailBasic/detailTabs
-- 허용 카테고리 {DETAIL,VIEW}와 정합.
-- TEMPLATE_KEY/PREVIEW_KEY는 스튜디오 카탈로그/프리뷰 식별자다(파이프라인 산출은 archetype 레벨
-- archetype/mgmtListDetail/detail(Js/Css)에서 조립 — GenArtifacts.ARCHETYPE_DETAIL_ARTIFACTS).
-- 의존: P0-3(TB_FRG_MODULE_TYPE), P1-4(MODULE_CATEGORY), V3/V4(선행 시드).
-- =====================================================================
SET search_path TO jforge;

-- ---------------------------------------------------------------------
-- DETAIL_BASIC (상세). 근거: static/js/admin/common/commonSection.js registEventBasicInfo(88행).
--   보기/수정 2모드(view-mode/edit-mode), 접기(button-detail-collapse/expand 113·117행),
--   속성칩(handleBasicInfoAttribute.renderView 159행, attribute-chip-container).
--   fields[{name,label,type,requiredYn,styleClass}]는 detail.ftl이 정적 골격으로 그린다(§9.3).
--   type은 산출 시 허용목록(text/number/date/email/tel/password/select/textarea/checkbox/radio)으로
--   리터럴 매핑(미허용→text) — <input type="..."> 속성 탈출 차단(formView 동형).
-- ---------------------------------------------------------------------
INSERT INTO TB_FRG_MODULE_TYPE
    (MODULE_TYPE_CODE, MODULE_NAME, CATEGORY_CODE, PROP_SCHEMA_JSON, TEMPLATE_KEY, PREVIEW_KEY, SORT_ORDER)
VALUES
    ('DETAIL_BASIC', '기본정보', 'DETAIL',
     '{
  "title": "기본정보",
  "fields": [
    {
      "key": "fields",
      "label": "기본정보 필드",
      "type": "columns",
      "required": false,
      "default": [],
      "columns": [
        { "key": "name", "label": "필드명(데이터 키)", "type": "text" },
        { "key": "label", "label": "라벨", "type": "text" },
        {
          "key": "type",
          "label": "입력 유형",
          "type": "select",
          "options": [
            { "value": "text", "label": "텍스트" },
            { "value": "number", "label": "숫자" },
            { "value": "date", "label": "날짜" },
            { "value": "email", "label": "이메일" },
            { "value": "tel", "label": "전화" },
            { "value": "password", "label": "비밀번호" },
            { "value": "select", "label": "선택박스" },
            { "value": "textarea", "label": "여러줄" },
            { "value": "checkbox", "label": "체크박스" },
            { "value": "radio", "label": "라디오" }
          ]
        },
        { "key": "requiredYn", "label": "필수", "type": "boolean" },
        { "key": "styleClass", "label": "스타일 클래스", "type": "text" }
      ]
    },
    { "key": "editableYn",     "label": "수정 가능(수정/저장 버튼)", "type": "boolean", "required": false, "default": true },
    { "key": "attributeYn",    "label": "속성칩 영역 사용",         "type": "boolean", "required": false, "default": false },
    { "key": "basicStyleClass","label": "기본정보 스타일 클래스",   "type": "text",    "required": false, "default": "" }
  ]
}'::jsonb,
     'module/detailBasic', 'preview/detailBasic', 7),

-- ---------------------------------------------------------------------
-- ASSOCIATE_TABS (상세). 근거: static/js/admin/common/commonSection.js registEventAssociateInfo(247행).
--   section#associate-info .tabs .tab + .contents iframe.{tabClass}. 탭 클릭 시 iframe이
--   tab.location으로 location.replace(310행) — location(iframe src)은 도메인 채움 배선점(§9 (B)).
--   tabs[{label,tabClass,frameId}]: label=탭 라벨(htmlText), tabClass=탭·iframe 매칭 클래스 토큰
--   (cssToken→jsString), frameId=iframe DOM id(htmlAttr/jsString). URL 직접수신 props 없음(§9.3).
-- ---------------------------------------------------------------------
    ('ASSOCIATE_TABS', '연관 탭', 'DETAIL',
     '{
  "title": "연관 탭",
  "fields": [
    {
      "key": "tabs",
      "label": "연관 탭 목록",
      "type": "columns",
      "required": false,
      "default": [],
      "columns": [
        { "key": "label", "label": "탭 라벨", "type": "text" },
        { "key": "tabClass", "label": "탭 클래스(탭·iframe 매칭 토큰)", "type": "text" },
        { "key": "frameId", "label": "iframe ID", "type": "text" }
      ]
    }
  ]
}'::jsonb,
     'module/associateTabs', 'preview/associateTabs', 8)
ON CONFLICT (MODULE_TYPE_CODE) DO NOTHING;
