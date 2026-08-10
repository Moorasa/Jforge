-- =====================================================================
-- P11: 타겟 DB 접속정보 (프로젝트 1:1)
-- TB_FRG_PROJECT를 넓히지 않고 분리한다 — 자격증명이 일반 프로젝트 조회
-- (projectColumns)에 섞이지 않게 하기 위한 의도적 분리.
-- 🔒 비밀번호는 평문 저장 금지: AES-GCM 암호문(base64)만 보관한다.
-- 🔒 JDBC URL은 저장하지 않는다 — host/port/db 조각만 보관하고 서버가 조립한다
--    (드라이버 URL 파라미터 주입 차단. 계약 §15 참조).
-- 멱등(IF NOT EXISTS). 재실행 안전.
-- =====================================================================

SET search_path TO jforge;

CREATE TABLE IF NOT EXISTS TB_FRG_PROJECT_DB (
    PROJECT_ID      BIGINT        NOT NULL,
    DB_HOST         VARCHAR(255)  NOT NULL,
    DB_PORT         INTEGER       NOT NULL,
    DB_NAME         VARCHAR(64)   NOT NULL,
    DB_SCHEMA       VARCHAR(64)   NOT NULL DEFAULT 'public',
    DB_USERNAME     VARCHAR(64)   NOT NULL,
    DB_PASSWORD_ENC VARCHAR(2000) NOT NULL,   -- AES-GCM base64(iv||ciphertext)
    REG_DTM         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    MOD_DTM         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT PK_TB_FRG_PROJECT_DB PRIMARY KEY (PROJECT_ID),
    CONSTRAINT FK_TB_FRG_PROJECT_DB_PROJECT
        FOREIGN KEY (PROJECT_ID) REFERENCES TB_FRG_PROJECT (PROJECT_ID),
    CONSTRAINT CK_TB_FRG_PROJECT_DB_PORT CHECK (DB_PORT BETWEEN 1 AND 65535)
);
COMMENT ON TABLE TB_FRG_PROJECT_DB IS '타겟 DB 접속정보(읽기전용 스키마 조회 용도)';
COMMENT ON COLUMN TB_FRG_PROJECT_DB.DB_PASSWORD_ENC IS 'AES-GCM 암호문(base64). 평문/복호값은 API로 반환하지 않는다';
