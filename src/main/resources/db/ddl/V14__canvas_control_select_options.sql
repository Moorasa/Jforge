-- =====================================================================
-- J-FORGE — 캔버스 컨트롤 4종의 select options 형식 교정
-- 대상 테이블: TB_FRG_MODULE_TYPE (V1 DDL에서 이미 생성됨 — 재생성 금지, 시드 전용)
--
-- 배경: 속성패널의 "모양"(BUTTON)·"강조"(LABEL)·"입력 유형"(TEXT_INPUT)·"맞춤"(IMAGE)
--   드롭다운이 **아무 항목도 없이** 떴다.
--   원인은 V10 시드가 select 의 options 를 **문자열**로 넣은 것이다:
--       "options":"primary:기본,secondary:보조,danger:위험"
--   규약(docs/스키마_PROP_SCHEMA.md §50/§54)은 **배열** [{value,label}] 이고,
--   폼 렌더러(schemaFormRenderer.renderSelect)도 `Array.isArray(field.options)` 로만 읽는다
--   → 배열이 아니면 옵션 0개 = 빈 드롭다운.
--   V3/V4/V5/V7 시드는 전부 배열로 올바르게 들어가 있다. V10 만 어긋났다.
--   (문자열 형식은 **반복행 셀**의 options 값에서 쓰는 형태다 — §159. 그 형식을 최상위
--    select 필드에 그대로 옮긴 것으로 보인다.)
--
-- 조치: 4개 모듈의 PROP_SCHEMA_JSON 을 배열 형식으로 교정한다. 다른 필드는 그대로 둔다.
-- 멱등: options 가 이미 배열이면(jsonb_typeof = 'array') 건드리지 않는다. 재실행 안전.
-- add-only: V1~V13 은 재실행·수정하지 않는다.
-- 의존: V10(캔버스 컨트롤 시드).
-- =====================================================================
SET search_path TO jforge;

UPDATE TB_FRG_MODULE_TYPE
SET PROP_SCHEMA_JSON = '{"title":"버튼","fields":[
  {"key":"text","label":"문구","type":"text","required":true,"default":"버튼"},
  {"key":"variant","label":"모양","type":"select","default":"primary","options":[
    {"value":"primary","label":"기본"},
    {"value":"secondary","label":"보조"},
    {"value":"danger","label":"위험"}]},
  {"key":"styleClass","label":"추가 클래스","type":"text","required":false}]}'::jsonb
WHERE MODULE_TYPE_CODE = 'BUTTON'
  AND jsonb_typeof(PROP_SCHEMA_JSON #> '{fields,1,options}') IS DISTINCT FROM 'array';

UPDATE TB_FRG_MODULE_TYPE
SET PROP_SCHEMA_JSON = '{"title":"라벨","fields":[
  {"key":"text","label":"문구","type":"text","required":true,"default":"라벨"},
  {"key":"level","label":"강조","type":"select","default":"normal","options":[
    {"value":"title","label":"제목"},
    {"value":"normal","label":"본문"},
    {"value":"caption","label":"작은설명"}]},
  {"key":"styleClass","label":"추가 클래스","type":"text","required":false}]}'::jsonb
WHERE MODULE_TYPE_CODE = 'LABEL'
  AND jsonb_typeof(PROP_SCHEMA_JSON #> '{fields,1,options}') IS DISTINCT FROM 'array';

UPDATE TB_FRG_MODULE_TYPE
SET PROP_SCHEMA_JSON = '{"title":"입력 상자","fields":[
  {"key":"name","label":"필드명","type":"text","required":true,"default":"field1"},
  {"key":"label","label":"라벨","type":"text","required":false,"default":"입력"},
  {"key":"inputType","label":"입력 유형","type":"select","default":"text","options":[
    {"value":"text","label":"텍스트"},
    {"value":"number","label":"숫자"},
    {"value":"date","label":"날짜"},
    {"value":"email","label":"이메일"},
    {"value":"tel","label":"전화"},
    {"value":"password","label":"비밀번호"}]},
  {"key":"placeholder","label":"안내 문구","type":"text","required":false},
  {"key":"styleClass","label":"추가 클래스","type":"text","required":false}]}'::jsonb
WHERE MODULE_TYPE_CODE = 'TEXT_INPUT'
  AND jsonb_typeof(PROP_SCHEMA_JSON #> '{fields,2,options}') IS DISTINCT FROM 'array';

UPDATE TB_FRG_MODULE_TYPE
SET PROP_SCHEMA_JSON = '{"title":"이미지","fields":[
  {"key":"src","label":"경로","type":"text","required":true,"default":"/images/sample.png"},
  {"key":"alt","label":"대체 문구","type":"text","required":false,"default":"이미지"},
  {"key":"fit","label":"맞춤","type":"select","default":"contain","options":[
    {"value":"contain","label":"비율유지"},
    {"value":"cover","label":"꽉채움"},
    {"value":"fill","label":"늘이기"}]},
  {"key":"styleClass","label":"추가 클래스","type":"text","required":false}]}'::jsonb
WHERE MODULE_TYPE_CODE = 'IMAGE'
  AND jsonb_typeof(PROP_SCHEMA_JSON #> '{fields,2,options}') IS DISTINCT FROM 'array';
