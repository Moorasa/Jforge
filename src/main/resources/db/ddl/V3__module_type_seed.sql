-- =====================================================================
-- J-FORGE 모듈타입 시드 (P2-1)
-- 대상 테이블: TB_FRG_MODULE_TYPE (V1 DDL에서 이미 생성됨 — 재생성 금지, 시드 전용)
-- 멱등(ON CONFLICT (MODULE_TYPE_CODE) DO NOTHING). 재실행 시 중복/오류 0.
-- PROP_SCHEMA_JSON: docs/스키마_PROP_SCHEMA.md §3 확정 JSON을 그대로 JSONB 캐스팅.
-- CATEGORY_CODE 값은 V2의 MODULE_CATEGORY 그룹(VIEW/FILTER/ACTION/DETAIL/WIDGET) 내 코드.
-- V1 DDL이 TEMPLATE_KEY/PREVIEW_KEY 컬럼을 이미 보유 → ALTER 보강 불필요.
-- 의존: P0-3(TB_FRG_MODULE_TYPE 테이블), P1-4(MODULE_CATEGORY 공통코드 시드).
-- =====================================================================
SET search_path TO jforge;

-- ---------------------------------------------------------------------
-- TABLE_VIEW (뷰). 근거: static/js/admin/common/commonListTableView.js
-- ---------------------------------------------------------------------
INSERT INTO TB_FRG_MODULE_TYPE
    (MODULE_TYPE_CODE, MODULE_NAME, CATEGORY_CODE, PROP_SCHEMA_JSON, TEMPLATE_KEY, PREVIEW_KEY, SORT_ORDER)
VALUES
    ('TABLE_VIEW', '테이블 뷰', 'VIEW',
     '{
  "title": "테이블 뷰",
  "fields": [
    {
      "key": "columns",
      "label": "컬럼 목록",
      "type": "columns",
      "required": true,
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
      "label": "행 선택 방식",
      "type": "select",
      "required": true,
      "default": "none",
      "options": [
        { "value": "none", "label": "선택 없음" },
        { "value": "checkbox", "label": "체크박스(다중)" },
        { "value": "radio", "label": "라디오(단일)" }
      ]
    },
    { "key": "pagingYn", "label": "페이징 사용", "type": "boolean", "required": false, "default": true },
    { "key": "excelYn",  "label": "엑셀 내보내기", "type": "boolean", "required": false, "default": false },
    { "key": "csvYn",    "label": "CSV 내보내기",  "type": "boolean", "required": false, "default": false }
  ]
}'::jsonb,
     'module/tableView', 'preview/tableView', 1),

-- ---------------------------------------------------------------------
-- SEARCH_FILTER_BAR (필터). 근거: commonListTableView.getCurrentSearchData()
-- ---------------------------------------------------------------------
    ('SEARCH_FILTER_BAR', '검색 필터 바', 'FILTER',
     '{
  "title": "검색 필터 바",
  "fields": [
    {
      "key": "filters",
      "label": "필터 목록",
      "type": "columns",
      "required": false,
      "default": [],
      "columns": [
        { "key": "name", "label": "필드명", "type": "text" },
        { "key": "label", "label": "라벨", "type": "text" },
        { "key": "options", "label": "선택 옵션(value:label, 콤마구분)", "type": "text" }
      ]
    },
    { "key": "keywordYn",   "label": "키워드 검색 사용", "type": "boolean", "required": false, "default": true },
    { "key": "dateRangeYn", "label": "날짜범위(datepicker) 사용", "type": "boolean", "required": false, "default": false }
  ]
}'::jsonb,
     'module/searchFilterBar', 'preview/searchFilterBar', 2),

-- ---------------------------------------------------------------------
-- TOOLBAR (액션).
-- ---------------------------------------------------------------------
    ('TOOLBAR', '툴바', 'ACTION',
     '{
  "title": "툴바",
  "fields": [
    {
      "key": "buttons",
      "label": "버튼 목록",
      "type": "columns",
      "required": true,
      "default": [
        { "actionCode": "add",    "label": "추가", "styleClass": "btn-primary" },
        { "actionCode": "delete", "label": "삭제", "styleClass": "btn-secondary" }
      ],
      "columns": [
        { "key": "actionCode", "label": "액션코드(add/delete/save 등)", "type": "text" },
        { "key": "label",      "label": "버튼 라벨", "type": "text" },
        { "key": "styleClass", "label": "스타일 클래스", "type": "text" }
      ]
    }
  ]
}'::jsonb,
     'module/toolbar', 'preview/toolbar', 3)
ON CONFLICT (MODULE_TYPE_CODE) DO NOTHING;
