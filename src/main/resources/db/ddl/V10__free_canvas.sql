-- =====================================================================
-- J-FORGE P13 — FREE_CANVAS(자유 배치) 아키타입 + CONTROL 원자 컨트롤 시드
-- 계약 §17 add-only. 멱등(ON CONFLICT DO NOTHING) — 재실행 시 중복/오류 0.
-- 기존 아키타입·모듈은 한 행도 수정하지 않는다(신규 행만 추가).
-- =====================================================================
SET search_path TO jforge;

INSERT INTO TB_FRG_COMMON_CODE (GRP_CODE, CODE, CODE_NAME, SORT_ORDER) VALUES
    ('ARCHETYPE', 'FREE_CANVAS', '자유 배치', 6),
    -- 원자 컨트롤 카테고리(§17.1) — 캔버스에 직접 놓는 최소 단위 부품.
    ('MODULE_CATEGORY', 'CONTROL', '컨트롤', 6)
ON CONFLICT (GRP_CODE, CODE) DO NOTHING;

INSERT INTO TB_FRG_MODULE_TYPE
    (MODULE_TYPE_CODE, MODULE_NAME, CATEGORY_CODE, PROP_SCHEMA_JSON, TEMPLATE_KEY, PREVIEW_KEY, SORT_ORDER)
VALUES
('BUTTON', '버튼', 'CONTROL',
 '{"title":"버튼","fields":[{"key":"text","label":"문구","type":"text","required":true,"default":"버튼"},{"key":"variant","label":"모양","type":"select","options":"primary:기본,secondary:보조,danger:위험","default":"primary"},{"key":"styleClass","label":"추가 클래스","type":"text","required":false}]}'::jsonb,
 'freeCanvas/item/button', 'preview/button', 21),
('LABEL', '라벨', 'CONTROL',
 '{"title":"라벨","fields":[{"key":"text","label":"문구","type":"text","required":true,"default":"라벨"},{"key":"level","label":"강조","type":"select","options":"title:제목,normal:본문,caption:작은설명","default":"normal"},{"key":"styleClass","label":"추가 클래스","type":"text","required":false}]}'::jsonb,
 'freeCanvas/item/label', 'preview/label', 22),
('TEXT_INPUT', '입력 상자', 'CONTROL',
 '{"title":"입력 상자","fields":[{"key":"name","label":"필드명","type":"text","required":true,"default":"field1"},{"key":"label","label":"라벨","type":"text","required":false,"default":"입력"},{"key":"inputType","label":"입력 유형","type":"select","options":"text:텍스트,number:숫자,date:날짜,email:이메일,tel:전화,password:비밀번호","default":"text"},{"key":"placeholder","label":"안내 문구","type":"text","required":false},{"key":"styleClass","label":"추가 클래스","type":"text","required":false}]}'::jsonb,
 'freeCanvas/item/textInput', 'preview/textInput', 23),
('IMAGE', '이미지', 'CONTROL',
 '{"title":"이미지","fields":[{"key":"src","label":"경로","type":"text","required":true,"default":"/images/sample.png"},{"key":"alt","label":"대체 문구","type":"text","required":false,"default":"이미지"},{"key":"fit","label":"맞춤","type":"select","options":"contain:비율유지,cover:꽉채움,fill:늘이기","default":"contain"},{"key":"styleClass","label":"추가 클래스","type":"text","required":false}]}'::jsonb,
 'freeCanvas/item/image', 'preview/image', 24)
ON CONFLICT (MODULE_TYPE_CODE) DO NOTHING;
