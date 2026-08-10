-- =====================================================================
-- J-FORGE 코어 스키마 (P0-3)
-- 대상 스키마: jforge / PostgreSQL
-- 네이밍: 대문자 스네이크, 코드 _CODE / 식별자 _ID / 불린 _YN, SELECT * 금지
-- 설계는 문서모델(DEFINITION_JSON)에 저장(유연), 모듈 카탈로그만 정규화
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS jforge;
SET search_path TO jforge;

-- ---------------------------------------------------------------------
-- 공통코드 (그룹+코드 복합키). 다른 표의 *_CODE 값의 근거.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS TB_FRG_COMMON_CODE (
    GRP_CODE    VARCHAR(50)  NOT NULL,
    CODE        VARCHAR(50)  NOT NULL,
    CODE_NAME   VARCHAR(200) NOT NULL,
    SORT_ORDER  INTEGER      NOT NULL DEFAULT 0,
    USE_YN      CHAR(1)      NOT NULL DEFAULT 'Y',
    REG_DTM     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    MOD_DTM     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT PK_TB_FRG_COMMON_CODE PRIMARY KEY (GRP_CODE, CODE),
    CONSTRAINT CK_TB_FRG_COMMON_CODE_USE_YN CHECK (USE_YN IN ('Y','N'))
);
COMMENT ON TABLE TB_FRG_COMMON_CODE IS 'J-FORGE 공통코드 (아키타입/롤/뷰타입/카테고리 등)';

-- ---------------------------------------------------------------------
-- 프로젝트: 타겟 프로젝트별 출력 경로/런타임 설정
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS TB_FRG_PROJECT (
    PROJECT_ID       BIGINT       GENERATED ALWAYS AS IDENTITY,
    PROJECT_NAME     VARCHAR(200) NOT NULL,
    TARGET_ROOT_PATH VARCHAR(1000) NOT NULL,   -- 모든 파일쓰기의 루트(경로안전 계층의 기준)
    PACKAGE_BASE     VARCHAR(300),
    JSP_BASE_PATH    VARCHAR(500),
    JS_BASE_PATH     VARCHAR(500),
    CSS_BASE_PATH    VARCHAR(500),
    DB_TYPE_CODE     VARCHAR(50),
    RUNTIME_VER      VARCHAR(50),
    USE_YN           CHAR(1)      NOT NULL DEFAULT 'Y',
    REG_DTM          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    MOD_DTM          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT PK_TB_FRG_PROJECT PRIMARY KEY (PROJECT_ID),
    CONSTRAINT CK_TB_FRG_PROJECT_USE_YN CHECK (USE_YN IN ('Y','N'))
);
COMMENT ON TABLE TB_FRG_PROJECT IS '빌더가 산출물을 쓰는 타겟 프로젝트';
COMMENT ON COLUMN TB_FRG_PROJECT.TARGET_ROOT_PATH IS '파일쓰기 루트. 경로안전 계층이 이 하위로만 허용';

-- ---------------------------------------------------------------------
-- 화면: 설계 문서(DEFINITION_JSON)가 source of truth
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS TB_FRG_SCREEN (
    SCREEN_ID       BIGINT       GENERATED ALWAYS AS IDENTITY,
    PROJECT_ID      BIGINT       NOT NULL,
    SCREEN_NAME     VARCHAR(200) NOT NULL,
    STEM            VARCHAR(100) NOT NULL,     -- 파일 접두(stem): {stem}.jsp, {stem}List.jsp ...
    ARCHETYPE_CODE  VARCHAR(50)  NOT NULL,     -- MGMT_LIST_DETAIL / SIMPLE_LIST / DUAL_LAYOUT
    ROLE_CODE       VARCHAR(50)  NOT NULL,     -- admin / user
    DEFINITION_JSON JSONB        NOT NULL DEFAULT '{}'::jsonb,
    STATUS_CODE     VARCHAR(50)  NOT NULL DEFAULT 'DRAFT',
    REG_DTM         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    MOD_DTM         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT PK_TB_FRG_SCREEN PRIMARY KEY (SCREEN_ID),
    CONSTRAINT FK_TB_FRG_SCREEN_PROJECT
        FOREIGN KEY (PROJECT_ID) REFERENCES TB_FRG_PROJECT (PROJECT_ID)
);
CREATE INDEX IF NOT EXISTS IX_TB_FRG_SCREEN_PROJECT ON TB_FRG_SCREEN (PROJECT_ID);
COMMENT ON TABLE TB_FRG_SCREEN IS '조립된 화면 1개. DEFINITION_JSON이 설계 원본';

-- ---------------------------------------------------------------------
-- 모듈 타입: 팔레트 카탈로그(정규화). scaffold-module-type 산출물의 등록처
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS TB_FRG_MODULE_TYPE (
    MODULE_TYPE_CODE VARCHAR(50)  NOT NULL,
    MODULE_NAME      VARCHAR(200) NOT NULL,
    CATEGORY_CODE    VARCHAR(50)  NOT NULL,     -- VIEW / FILTER / ACTION / DETAIL / WIDGET ...
    PROP_SCHEMA_JSON JSONB        NOT NULL DEFAULT '{}'::jsonb,  -- 속성패널 폼 스키마
    TEMPLATE_KEY     VARCHAR(200) NOT NULL,     -- FreeMarker 템플릿 참조 키
    PREVIEW_KEY      VARCHAR(200),              -- 라이브 프리뷰 파셜 참조 키
    USE_YN           CHAR(1)      NOT NULL DEFAULT 'Y',
    SORT_ORDER       INTEGER      NOT NULL DEFAULT 0,
    REG_DTM          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    MOD_DTM          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT PK_TB_FRG_MODULE_TYPE PRIMARY KEY (MODULE_TYPE_CODE),
    CONSTRAINT CK_TB_FRG_MODULE_TYPE_USE_YN CHECK (USE_YN IN ('Y','N'))
);
COMMENT ON TABLE TB_FRG_MODULE_TYPE IS '팔레트 모듈 카탈로그(속성스키마+템플릿+프리뷰 키)';

-- ---------------------------------------------------------------------
-- 생성 이력: 저장(생성) 1회 = 산출 파일 목록/결과
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS TB_FRG_GEN_HIST (
    GEN_HIST_ID    BIGINT       GENERATED ALWAYS AS IDENTITY,
    SCREEN_ID      BIGINT       NOT NULL,
    GEN_AT         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    FILE_LIST_JSON JSONB        NOT NULL DEFAULT '[]'::jsonb,   -- 쓴 파일 경로/해시 목록
    RESULT_CODE    VARCHAR(50)  NOT NULL,                        -- SUCCESS / PARTIAL / FAIL
    CONSTRAINT PK_TB_FRG_GEN_HIST PRIMARY KEY (GEN_HIST_ID),
    CONSTRAINT FK_TB_FRG_GEN_HIST_SCREEN
        FOREIGN KEY (SCREEN_ID) REFERENCES TB_FRG_SCREEN (SCREEN_ID)
);
CREATE INDEX IF NOT EXISTS IX_TB_FRG_GEN_HIST_SCREEN ON TB_FRG_GEN_HIST (SCREEN_ID);
COMMENT ON TABLE TB_FRG_GEN_HIST IS '생성 이력(파일 목록/결과)';
