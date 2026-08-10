-- J-FORGE POPUP 아키타입 + POPUP_FORM 모듈 시드.
-- 근거: MagicIAM v2 admin/*/*Popup.jsp, commonPopup.js/commonPopup.css.
SET search_path TO jforge;

INSERT INTO TB_FRG_COMMON_CODE (GRP_CODE, CODE, CODE_NAME, SORT_ORDER)
VALUES ('ARCHETYPE', 'POPUP', '팝업', 4)
ON CONFLICT (GRP_CODE, CODE) DO NOTHING;

INSERT INTO TB_FRG_MODULE_TYPE
    (MODULE_TYPE_CODE, MODULE_NAME, CATEGORY_CODE, PROP_SCHEMA_JSON, TEMPLATE_KEY, PREVIEW_KEY, SORT_ORDER)
VALUES
    ('POPUP_FORM', '팝업 입력 폼', 'VIEW',
     '{
  "title": "팝업 입력 폼",
  "fields": [
    { "key": "popupTitle", "label": "팝업 제목", "type": "text", "required": true, "default": "정보 입력" },
    { "key": "bodyTitle", "label": "본문 제목", "type": "text", "required": false, "default": "" },
    {
      "key": "size",
      "label": "팝업 크기",
      "type": "select",
      "required": true,
      "default": "medium",
      "options": [
        { "value": "small", "label": "작게" },
        { "value": "medium", "label": "보통" },
        { "value": "large", "label": "크게" }
      ]
    },
    {
      "key": "fields",
      "label": "입력 필드",
      "type": "columns",
      "required": false,
      "default": [
        { "name": "name", "label": "이름", "type": "text", "requiredYn": true }
      ],
      "columns": [
        { "key": "name", "label": "필드명", "type": "text" },
        { "key": "label", "label": "라벨", "type": "text" },
        {
          "key": "type", "label": "입력 유형", "type": "select",
          "options": [
            { "value": "text", "label": "텍스트" },
            { "value": "number", "label": "숫자" },
            { "value": "date", "label": "날짜" },
            { "value": "email", "label": "이메일" },
            { "value": "select", "label": "선택박스" },
            { "value": "textarea", "label": "여러 줄" }
          ]
        },
        { "key": "requiredYn", "label": "필수", "type": "boolean" }
      ]
    },
    { "key": "confirmText", "label": "확인 버튼 문구", "type": "text", "required": true, "default": "확인" },
    { "key": "cancelYn", "label": "취소 버튼 사용", "type": "boolean", "required": false, "default": true }
  ]
}'::jsonb,
     'archetype/popupForm', 'preview/popupForm', 10)
ON CONFLICT (MODULE_TYPE_CODE) DO NOTHING;
