-- =====================================================================
-- J-FORGE — DUAL_LAYOUT 패인 오배치 차단 (계약 §10.4)
--
-- 배경: leftArea/rightArea 는 **iframe 패인**이라 LAYOUT_FRAME 만 의미가 있는데,
--       허용 카테고리가 'VIEW' 라서 TABLE_VIEW 같은 뷰 모듈을 놓는 것이 막히지 않았다.
--       놓고 생성하면 슬롯 전제(listArea[0])가 어긋난 모듈 템플릿이 렌더 실패해
--       결과가 조용히 PARTIAL 이 되고 화면엔 빈 iframe 만 남았다.
--
-- 조치: 프레임 전용 카테고리 'FRAME' 을 신설하고 LAYOUT_FRAME 을 그리로 옮긴다.
--       ArchetypeSlots/slotMeta 의 leftArea/rightArea 허용 카테고리도 'FRAME' 하나로 좁힌다.
--       → 기존 DUAL 화면(LAYOUT_FRAME 배치)은 그대로 유효하고, 뷰 모듈 배치는 저장에서 막힌다.
--
-- ⚠ 이 파일은 기존 시드 행을 UPDATE 한다(V6 LAYOUT_FRAME 의 CATEGORY_CODE).
--    카테고리 값과 슬롯 화이트리스트를 **함께** 바꾸므로 정상 화면의 유효성은 유지된다.
-- =====================================================================
SET search_path TO jforge;

INSERT INTO TB_FRG_COMMON_CODE (GRP_CODE, CODE, CODE_NAME, SORT_ORDER) VALUES
    ('MODULE_CATEGORY', 'FRAME', '프레임', 8)
ON CONFLICT (GRP_CODE, CODE) DO NOTHING;

UPDATE TB_FRG_MODULE_TYPE
SET CATEGORY_CODE = 'FRAME'
WHERE MODULE_TYPE_CODE = 'LAYOUT_FRAME'
  AND CATEGORY_CODE <> 'FRAME';
