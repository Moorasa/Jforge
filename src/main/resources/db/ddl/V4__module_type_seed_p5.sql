-- =====================================================================
-- J-FORGE 모듈타입 시드 (P5-2/P5-3/P5-4) — CARD_VIEW·TREE_VIEW·FORM_VIEW add-only
-- 대상 테이블: TB_FRG_MODULE_TYPE (V1 DDL에서 이미 생성됨 — 재생성 금지, 시드 전용)
-- 멱등(ON CONFLICT (MODULE_TYPE_CODE) DO NOTHING). 재실행 시 중복/오류 0.
-- add-only: V3(TABLE_VIEW/SEARCH_FILTER_BAR/TOOLBAR)는 재실행·수정하지 않는다.
-- 근거: static/js/admin/common/commonListCardView.js (MagicIAM 1:1, 계약 §8.6).
--   ⚠ commonListCardView.js는 카드 데이터(제목/부제/이미지)를 정적 props가 아니라
--     런타임 콜백(apiInfo.renderCallback, 243행)으로 그린다. 따라서 아래 titleField/subtitleField/
--     imageField/columns는 "어느 데이터 키를 무엇으로 바인딩할지"의 배선 힌트 설정 props다(§8.4 주의).
--   ⚠ imageField는 URL이 아니라 데이터 필드명이다(런타임이 경로를 조립). URL 직접수신 props 없음(§8.4).
-- PROP_SCHEMA_JSON: 규약 docs/스키마_PROP_SCHEMA.md 준수, ::jsonb 캐스팅.
-- TEMPLATE_KEY 'module/cardView' / PREVIEW_KEY 'preview/cardView' (계약 §8.2 1:1 정합).
-- 의존: P0-3(TB_FRG_MODULE_TYPE 테이블), P1-4(MODULE_CATEGORY), V3(선행 시드).
-- =====================================================================
SET search_path TO jforge;

-- ---------------------------------------------------------------------
-- CARD_VIEW (뷰). 근거: static/js/admin/common/commonListCardView.js
-- ---------------------------------------------------------------------
INSERT INTO TB_FRG_MODULE_TYPE
    (MODULE_TYPE_CODE, MODULE_NAME, CATEGORY_CODE, PROP_SCHEMA_JSON, TEMPLATE_KEY, PREVIEW_KEY, SORT_ORDER)
VALUES
    ('CARD_VIEW', '카드 뷰', 'VIEW',
     '{
  "title": "카드 뷰",
  "fields": [
    { "key": "titleField",    "label": "제목 필드(데이터 키)",   "type": "text", "required": true,  "default": "name" },
    { "key": "subtitleField", "label": "부제 필드(데이터 키)",   "type": "text", "required": false, "default": "id" },
    { "key": "imageField",    "label": "이미지 필드(데이터 키)", "type": "text", "required": false, "default": "" },
    {
      "key": "columns",
      "label": "카드 본문 항목",
      "type": "columns",
      "required": false,
      "default": [],
      "columns": [
        { "key": "name", "label": "필드명", "type": "text" },
        { "key": "displayName", "label": "표시명", "type": "text" },
        { "key": "displayYn", "label": "표시", "type": "boolean" },
        { "key": "sortYn", "label": "정렬", "type": "boolean" }
      ]
    },
    {
      "key": "selectMode",
      "label": "카드 선택 방식",
      "type": "select",
      "required": true,
      "default": "none",
      "options": [
        { "value": "none", "label": "선택 없음" },
        { "value": "checkbox", "label": "체크박스(다중)" },
        { "value": "radio", "label": "라디오(단일)" }
      ]
    },
    { "key": "pagingYn",       "label": "페이징 사용",   "type": "boolean", "required": false, "default": true },
    { "key": "categoryYn",     "label": "카테고리 사용", "type": "boolean", "required": false, "default": false },
    { "key": "cardStyleClass", "label": "카드 스타일 클래스", "type": "text", "required": false, "default": "" }
  ]
}'::jsonb,
     'module/cardView', 'preview/cardView', 4)
ON CONFLICT (MODULE_TYPE_CODE) DO NOTHING;

-- ---------------------------------------------------------------------
-- TREE_VIEW (뷰). 근거: static/js/admin/common/commonListTreeView.js (MagicIAM 1:1, 계약 §8.6).
--   ⚠ commonListTreeView.js는 계층 노드를 정적 props가 아니라 런타임 파서/콜백
--     (apiInfo.parser 494행, apiInfo.renderCallback 461행)으로 그린다. 따라서 아래
--     labelField/idField/parentField/iconField/selectMode 는 "어느 데이터 키를 무엇으로
--     바인딩할지"의 배선 힌트 설정 props다(§8.4 주의). 트리도 콜백 기반이다.
--   ⚠ URL 직접수신 props 없음(§8.4): iconField는 데이터 필드명, rootIconClass 는
--     cssToken 검증 대상 클래스 토큰(commonListTreeView.js 485행 root.iconClass). URL/href/src 없음.
-- PROP_SCHEMA_JSON: 규약 docs/스키마_PROP_SCHEMA.md 준수, ::jsonb 캐스팅.
-- TEMPLATE_KEY 'module/treeView' / PREVIEW_KEY 'preview/treeView' (계약 §8.2 1:1 정합).
-- ---------------------------------------------------------------------
INSERT INTO TB_FRG_MODULE_TYPE
    (MODULE_TYPE_CODE, MODULE_NAME, CATEGORY_CODE, PROP_SCHEMA_JSON, TEMPLATE_KEY, PREVIEW_KEY, SORT_ORDER)
VALUES
    ('TREE_VIEW', '트리 뷰', 'VIEW',
     '{
  "title": "트리 뷰",
  "fields": [
    { "key": "labelField",    "label": "라벨 필드(데이터 키)",   "type": "text", "required": true,  "default": "name" },
    { "key": "idField",       "label": "ID 필드(데이터 키)",     "type": "text", "required": true,  "default": "id" },
    { "key": "parentField",   "label": "부모 필드(데이터 키)",   "type": "text", "required": true,  "default": "parentNo" },
    { "key": "iconField",     "label": "아이콘 필드(데이터 키)", "type": "text", "required": false, "default": "" },
    {
      "key": "selectMode",
      "label": "노드 선택 방식",
      "type": "select",
      "required": true,
      "default": "single",
      "options": [
        { "value": "single", "label": "단일 선택" },
        { "value": "checkbox", "label": "체크박스(다중)" }
      ]
    },
    { "key": "rootLabel",     "label": "루트 라벨",         "type": "text",    "required": false, "default": "전체" },
    { "key": "rootIconClass", "label": "루트 아이콘 클래스", "type": "text",    "required": false, "default": "" },
    { "key": "orderingYn",    "label": "정렬(순서) 사용",   "type": "boolean", "required": false, "default": false },
    { "key": "searchYn",      "label": "검색 사용",         "type": "boolean", "required": false, "default": true },
    { "key": "treeStyleClass","label": "트리 스타일 클래스", "type": "text",   "required": false, "default": "" }
  ]
}'::jsonb,
     'module/treeView', 'preview/treeView', 5)
ON CONFLICT (MODULE_TYPE_CODE) DO NOTHING;

-- ---------------------------------------------------------------------
-- FORM_VIEW (뷰). 근거: static/js/admin/common/commonListFormView.js (MagicIAM 1:1, 계약 §8.6).
--   ⚠ commonListFormView.js init 시그니처: formView.init = function(options)(29행). 필수 옵션
--     options.$container(31행), 선택 apiInfo(37행: url·renderCallback), selectionType(38행, 기본 'checkbox').
--     render(data)는 비어 있다(127~128행) — 폼 본문은 산출 JSP 정적 골격/도메인 콜백 소관.
--     팝업 모드는 런타임 message 수신(SET_EMPTY_MODE / mode==='popup', 72~81행).
--   ⚠ 아래 fields[{name,label,type,requiredYn,styleClass}]는 "어느 입력 필드를 어떤 라벨/타입/필수로
--     그릴지"의 배선 힌트 설정 props다(§8.4 주의 — 런타임 render는 비어있어 폼 골격을 JSP가 정적으로 그림).
--   ⚠ URL 직접수신 props 없음(§8.4): name/type은 데이터 바인딩/입력유형이며 URL/href/src 아님.
--     type은 산출 시 허용 목록(text/number/date/email/tel/password/select/textarea/checkbox/radio)으로
--     화이트리스트 매핑(미허용은 text) — <input type="..."> 속성 탈출 차단.
--   ⚠ selectionType(체크박스 선택) → PROP_SCHEMA selectionType(기본 checkbox, commonListFormView.js 38행).
-- PROP_SCHEMA_JSON: 규약 docs/스키마_PROP_SCHEMA.md 준수, ::jsonb 캐스팅.
-- TEMPLATE_KEY 'module/formView' / PREVIEW_KEY 'preview/formView' (계약 §8.2 1:1 정합).
-- ---------------------------------------------------------------------
INSERT INTO TB_FRG_MODULE_TYPE
    (MODULE_TYPE_CODE, MODULE_NAME, CATEGORY_CODE, PROP_SCHEMA_JSON, TEMPLATE_KEY, PREVIEW_KEY, SORT_ORDER)
VALUES
    ('FORM_VIEW', '폼 뷰', 'VIEW',
     '{
  "title": "폼 뷰",
  "fields": [
    {
      "key": "selectionType",
      "label": "행 선택 방식",
      "type": "select",
      "required": true,
      "default": "checkbox",
      "options": [
        { "value": "checkbox", "label": "체크박스(다중)" },
        { "value": "none", "label": "선택 없음" }
      ]
    },
    {
      "key": "fields",
      "label": "폼 입력 필드",
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
    { "key": "formStyleClass", "label": "폼 스타일 클래스", "type": "text", "required": false, "default": "" }
  ]
}'::jsonb,
     'module/formView', 'preview/formView', 6)
ON CONFLICT (MODULE_TYPE_CODE) DO NOTHING;
