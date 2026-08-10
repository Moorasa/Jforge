SET search_path TO jforge;

INSERT INTO TB_FRG_COMMON_CODE (GRP_CODE, CODE, CODE_NAME, SORT_ORDER)
VALUES ('ARCHETYPE', 'DASHBOARD', '대시보드', 5)
ON CONFLICT (GRP_CODE, CODE) DO NOTHING;

INSERT INTO TB_FRG_MODULE_TYPE
    (MODULE_TYPE_CODE, MODULE_NAME, CATEGORY_CODE, PROP_SCHEMA_JSON, TEMPLATE_KEY, PREVIEW_KEY, SORT_ORDER)
VALUES
('BAR_CHART', '막대 차트', 'WIDGET',
 '{"title":"막대 차트","fields":[{"key":"title","label":"제목","type":"text","default":"진행률"},{"key":"value","label":"값(0~100)","type":"number","default":65},{"key":"unit","label":"단위","type":"text","default":"%"}]}'::jsonb,
 'module/barChart', 'preview/barChart', 11),
('SEMICIRCLE_CHART', '반원 차트', 'WIDGET',
 '{"title":"반원 차트","fields":[{"key":"title","label":"제목","type":"text","default":"달성률"},{"key":"value","label":"값(0~100)","type":"number","default":72},{"key":"unit","label":"단위","type":"text","default":"%"}]}'::jsonb,
 'module/semicircleChart', 'preview/semicircleChart', 12),
('EMPTY_STATE', '빈 상태 안내', 'WIDGET',
 '{"title":"빈 상태 안내","fields":[{"key":"title","label":"제목","type":"text","default":"데이터가 없습니다"},{"key":"description","label":"설명","type":"text","default":"조건을 변경하거나 새 항목을 추가하세요."},{"key":"actionText","label":"버튼 문구","type":"text","default":"새로 만들기"}]}'::jsonb,
 'module/emptyState', 'preview/emptyState', 13),
('CHAT_WIDGET', '채팅 위젯', 'WIDGET',
 '{"title":"채팅 위젯","fields":[{"key":"title","label":"제목","type":"text","default":"채팅 상담"},{"key":"welcomeMessage","label":"첫 안내 문구","type":"text","default":"안녕하세요. 무엇을 도와드릴까요?"},{"key":"placeholder","label":"입력 안내","type":"text","default":"메시지를 입력하세요"}]}'::jsonb,
 'module/chatWidget', 'preview/chatWidget', 14)
ON CONFLICT (MODULE_TYPE_CODE) DO NOTHING;

-- 원본 commonListTableView의 업로드 기능을 기존 TABLE_VIEW 속성에 보강한다.
UPDATE TB_FRG_MODULE_TYPE
SET PROP_SCHEMA_JSON = jsonb_set(
    PROP_SCHEMA_JSON, '{fields}',
    (PROP_SCHEMA_JSON->'fields') ||
    '[{"key":"excelUploadYn","label":"엑셀 업로드","type":"boolean","required":false,"default":false},
      {"key":"csvUploadYn","label":"CSV 업로드","type":"boolean","required":false,"default":false}]'::jsonb)
WHERE MODULE_TYPE_CODE = 'TABLE_VIEW'
  AND NOT (PROP_SCHEMA_JSON->'fields' @> '[{"key":"excelUploadYn"}]'::jsonb);
