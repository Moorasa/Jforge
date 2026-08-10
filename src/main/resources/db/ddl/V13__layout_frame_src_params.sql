-- =====================================================================
-- J-FORGE — LAYOUT_FRAME 속성 확장 (계약 §19 / §19.4)
-- 대상 테이블: TB_FRG_MODULE_TYPE (V1 DDL에서 이미 생성됨 — 재생성 금지, 시드 전용)
-- 배경: §10.3 에서는 iframe src 를 산출하지 않았다(도메인 배선점). §19 부터 `frameSrc` 가
--   게이트를 통과하면 같은 출처 절대경로로 산출하고, §19.4 부터 `frameParams` 를 생성기가
--   퍼센트 인코딩해 쿼리스트링으로 붙인다.
-- 조치: PROP_SCHEMA_JSON 에 두 필드를 add-only 로 넣어 속성 패널이 편집할 수 있게 한다.
--   - frameSrc    : text    — 스튜디오 화면 피커가 채우거나 손으로 입력
--   - frameParams : columns — {name, value} 반복행. 값 인코딩은 **생성기**가 한다
--                   (사용자가 쓴 문자열을 그대로 이어붙이면 값 안의 &/= 가 파라미터 경계를
--                    깨서 넣은 적 없는 파라미터가 만들어진다 — 파라미터 밀수)
-- ⚠ 이 파일은 기존 시드 행을 UPDATE 한다(V6 LAYOUT_FRAME 의 PROP_SCHEMA_JSON).
--   멱등: 이미 frameSrc 키가 있으면 아무것도 하지 않는다. 재실행 시 중복/오류 0.
-- add-only: V1~V12 는 재실행·수정하지 않는다.
-- 의존: V6(LAYOUT_FRAME 시드), V12(카테고리 FRAME 이관).
-- =====================================================================
SET search_path TO jforge;

UPDATE TB_FRG_MODULE_TYPE
SET PROP_SCHEMA_JSON = '{
  "title": "레이아웃 프레임",
  "fields": [
    { "key": "frameId",   "label": "프레임 ID(iframe id)", "type": "text", "required": false, "default": "" },
    { "key": "title",     "label": "프레임 제목",          "type": "text", "required": false, "default": "" },
    { "key": "paneClass", "label": "패인 스타일 클래스",   "type": "text", "required": false, "default": "" },
    { "key": "frameSrc",  "label": "불러올 화면 경로",     "type": "text", "required": false, "default": "",
      "help": "같은 앱 안의 / 로 시작하는 경로. 타겟 앱에서 실제로 매핑한 주소여야 합니다." },
    {
      "key": "frameParams",
      "label": "화면에 넘길 파라미터",
      "type": "columns",
      "required": false,
      "default": [],
      "help": "값은 생성 시 자동으로 인코딩됩니다. 하나라도 형식이 어긋나면 안전을 위해 주소 전체가 산출되지 않습니다.",
      "columns": [
        { "key": "name",  "label": "이름", "type": "text" },
        { "key": "value", "label": "값",   "type": "text" }
      ]
    }
  ]
}'::jsonb
WHERE MODULE_TYPE_CODE = 'LAYOUT_FRAME'
  AND NOT (PROP_SCHEMA_JSON -> 'fields' @> '[{"key":"frameSrc"}]'::jsonb);
