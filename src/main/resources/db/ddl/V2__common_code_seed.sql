-- =====================================================================
-- J-FORGE 공통코드 시드 (P1-4)
-- 멱등(ON CONFLICT DO NOTHING). GRP_CODE 기준으로 화면 셀렉트가 사용.
-- =====================================================================
SET search_path TO jforge;

INSERT INTO TB_FRG_COMMON_CODE (GRP_CODE, CODE, CODE_NAME, SORT_ORDER) VALUES
    -- 화면 아키타입
    ('ARCHETYPE', 'MGMT_LIST_DETAIL', '관리화면(목록+상세)', 1),
    ('ARCHETYPE', 'SIMPLE_LIST',      '단순 목록',           2),
    ('ARCHETYPE', 'DUAL_LAYOUT',      '좌우 2단',            3),
    -- 롤
    ('ROLE', 'admin', '관리자', 1),
    ('ROLE', 'user',  '사용자', 2),
    -- 뷰타입 4종
    ('VIEW_TYPE', 'TABLE', '테이블', 1),
    ('VIEW_TYPE', 'TREE',  '트리',   2),
    ('VIEW_TYPE', 'CARD',  '카드',   3),
    ('VIEW_TYPE', 'FORM',  '폼',     4),
    -- 모듈 카테고리
    ('MODULE_CATEGORY', 'VIEW',   '뷰',       1),
    ('MODULE_CATEGORY', 'FILTER', '필터',     2),
    ('MODULE_CATEGORY', 'ACTION', '액션',     3),
    ('MODULE_CATEGORY', 'DETAIL', '상세',     4),
    ('MODULE_CATEGORY', 'WIDGET', '위젯',     5),
    -- DB 타입
    ('DB_TYPE', 'POSTGRES', 'PostgreSQL', 1),
    -- 화면 상태
    ('SCREEN_STATUS', 'DRAFT',     '작성중', 1),
    ('SCREEN_STATUS', 'PUBLISHED', '배포됨', 2),
    ('SCREEN_STATUS', 'DELETED',   '폐기(논리삭제)', 3),
    -- 생성 결과
    ('GEN_RESULT', 'SUCCESS', '성공',   1),
    ('GEN_RESULT', 'PARTIAL', '부분성공', 2),
    ('GEN_RESULT', 'FAIL',    '실패',   3)
ON CONFLICT (GRP_CODE, CODE) DO NOTHING;
