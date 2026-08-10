package com.jworks.forge.screen.domain;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonRawValue;

/**
 * TB_FRG_SCREEN 엔티티. 조립된 화면 1개. DEFINITION_JSON이 설계 원본(source of truth).
 * (컬럼 대문자스네이크 ↔ 필드 카멜은 map-underscore-to-camel-case로 매핑)
 *
 * <p>DEFINITION_JSON(JSONB)은 <b>원문 그대로</b> 왕복한다(스키마_DEFINITION_JSON.md §5 신뢰경계 —
 * catalog의 PROP_SCHEMA_JSON과 동일 방식). 매퍼가 JSONB를 {@code ::text}로 뽑아 String으로 받고,
 * 응답 직렬화 시 {@link JsonRawValue}로 <em>재해석/재가공 없이</em> JSON 트리로 그대로 실어보낸다
 * (문자열 이스케이프 왕복 없음 → 바이트 동등 왕복 보존). props의 표시 문자열은 서버가 이스케이프하지 않으며
 * 방어 책임은 소비자(프리뷰/생성기)에 있다.
 */
public class ForgeScreen {

    private Long screenId;
    private Long projectId;
    private String screenName;
    private String stem;
    private String archetypeCode;
    private String roleCode;

    /** JSONB 원문(문자열). 매퍼에서 DEFINITION_JSON::text 로 채운다(단건 조회만). */
    private String definitionJson;

    private String statusCode;
    private OffsetDateTime regDtm;
    private OffsetDateTime modDtm;

    public Long getScreenId() { return screenId; }
    public void setScreenId(Long screenId) { this.screenId = screenId; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public String getScreenName() { return screenName; }
    public void setScreenName(String screenName) { this.screenName = screenName; }

    public String getStem() { return stem; }
    public void setStem(String stem) { this.stem = stem; }

    public String getArchetypeCode() { return archetypeCode; }
    public void setArchetypeCode(String archetypeCode) { this.archetypeCode = archetypeCode; }

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }

    /**
     * 응답 바디에 JSONB 원문을 <b>날 것(raw)</b>으로 삽입한다.
     * DB가 널(공백문자열 등)로 온 경우를 대비해 빈값이면 빈 객체로 대체(항상 유효 JSON 토큰 보장).
     * 목록 조회에서는 이 컬럼을 뽑지 않으므로(경량화) null → "{}" 로 나가는 대신
     * 목록 응답에서는 {@code null}을 그대로 두기 위해 목록 매퍼가 이 필드를 채우지 않는다.
     */
    @JsonRawValue
    public String getDefinitionJson() {
        return (definitionJson == null || definitionJson.isBlank()) ? null : definitionJson;
    }
    public void setDefinitionJson(String definitionJson) { this.definitionJson = definitionJson; }

    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }

    public OffsetDateTime getRegDtm() { return regDtm; }
    public void setRegDtm(OffsetDateTime regDtm) { this.regDtm = regDtm; }

    public OffsetDateTime getModDtm() { return modDtm; }
    public void setModDtm(OffsetDateTime modDtm) { this.modDtm = modDtm; }
}
