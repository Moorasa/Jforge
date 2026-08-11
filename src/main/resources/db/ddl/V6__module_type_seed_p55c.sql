-- =====================================================================
-- J-FORGE 모듈타입 시드 (P5-5c) — LAYOUT_FRAME add-only (DUAL_LAYOUT 패인)
-- 대상 테이블: TB_FRG_MODULE_TYPE (V1 DDL에서 이미 생성됨 — 재생성 금지, 시드 전용)
-- 멱등(ON CONFLICT (MODULE_TYPE_CODE) DO NOTHING). 재실행 시 중복/오류 0.
-- add-only: V3/V4/V5는 재실행·수정하지 않는다.
-- 근거: static/js/admin/common/commonSection.js dual-layout-area(326~503행, JWorks 1:1, 계약 §10).
--   ⚠ DUAL_LAYOUT의 leftArea/rightArea는 iframe 패인(.layout-left > iframe / .layout-right > iframe)이다.
--     LAYOUT_FRAME은 그 패인 1개(=iframe 1개)를 나타낸다. iframe src(패인 로드 URL)은 props가 아니라
--     산출 후 도메인이 채우는 배선점이다(§10.3, URL props 없음). frameId=iframe DOM id, title=iframe title,
--     paneClass=cssToken 클래스 토큰.
-- CATEGORY_CODE 'VIEW'는 V2 MODULE_CATEGORY 그룹 내 코드 — ArchetypeSlots leftArea/rightArea 허용
--   카테고리 {VIEW,FILTER,DETAIL}에 부합.
-- TEMPLATE_KEY/PREVIEW_KEY는 스튜디오 카탈로그/프리뷰 식별자다(파이프라인 산출은 archetype 레벨
--   archetype/dualLayout/shell(Js/Css)에서 조립 — GenArtifacts.ARCHETYPE_ARTIFACTS.DUAL_LAYOUT).
-- 의존: P0-3(TB_FRG_MODULE_TYPE), P1-4(MODULE_CATEGORY), V3/V4/V5(선행 시드).
-- =====================================================================
SET search_path TO jforge;

INSERT INTO TB_FRG_MODULE_TYPE
    (MODULE_TYPE_CODE, MODULE_NAME, CATEGORY_CODE, PROP_SCHEMA_JSON, TEMPLATE_KEY, PREVIEW_KEY, SORT_ORDER)
VALUES
    ('LAYOUT_FRAME', '레이아웃 프레임', 'VIEW',
     '{
  "title": "레이아웃 프레임",
  "fields": [
    { "key": "frameId",   "label": "프레임 ID(iframe id)", "type": "text", "required": false, "default": "" },
    { "key": "title",     "label": "프레임 제목",          "type": "text", "required": false, "default": "" },
    { "key": "paneClass", "label": "패인 스타일 클래스",   "type": "text", "required": false, "default": "" }
  ]
}'::jsonb,
     'module/layoutFrame', 'preview/layoutFrame', 9)
ON CONFLICT (MODULE_TYPE_CODE) DO NOTHING;
