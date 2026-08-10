-- =====================================================================
-- J-FORGE P13-7 — FREE_CANVAS 중첩 컨테이너(PANEL) 시드
-- 계약 §17.8 add-only. 멱등(ON CONFLICT DO NOTHING).
-- PANEL 은 캔버스 전용 컨테이너다 — canvasArea 만 LAYOUT 카테고리를 허용하므로
-- 다른 아키타입 팔레트에는 나타나지 않는다(기존 화면 무영향).
-- =====================================================================
SET search_path TO jforge;

INSERT INTO TB_FRG_COMMON_CODE (GRP_CODE, CODE, CODE_NAME, SORT_ORDER) VALUES
    ('MODULE_CATEGORY', 'LAYOUT', '레이아웃', 7)
ON CONFLICT (GRP_CODE, CODE) DO NOTHING;

INSERT INTO TB_FRG_MODULE_TYPE
    (MODULE_TYPE_CODE, MODULE_NAME, CATEGORY_CODE, PROP_SCHEMA_JSON, TEMPLATE_KEY, PREVIEW_KEY, SORT_ORDER)
VALUES
('PANEL', '패널(프레임)', 'LAYOUT',
 '{"title":"패널(프레임)","fields":[{"key":"title","label":"제목(선택)","type":"text","required":false,"default":""},{"key":"borderYn","label":"테두리","type":"boolean","required":false,"default":true},{"key":"fillYn","label":"배경 채움","type":"boolean","required":false,"default":false},{"key":"styleClass","label":"추가 클래스","type":"text","required":false}]}'::jsonb,
 'freeCanvas/item/panel', 'preview/panel', 20)
ON CONFLICT (MODULE_TYPE_CODE) DO NOTHING;
