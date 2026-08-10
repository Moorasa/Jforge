package com.jworks.forge.catalog.domain;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonRawValue;

/**
 * TB_FRG_MODULE_TYPE 엔티티. 팔레트 모듈 카탈로그(속성스키마+템플릿+프리뷰 키).
 * (컬럼 대문자스네이크 ↔ 필드 카멜은 map-underscore-to-camel-case로 매핑)
 *
 * <p>PROP_SCHEMA_JSON(JSONB)은 <b>원문 그대로</b> 전달한다(스키마_PROP_SCHEMA.md §2.1 신뢰경계).
 * 매퍼가 JSONB를 {@code ::text}로 뽑아 String으로 받고, 응답 직렬화 시
 * {@link JsonRawValue}로 <em>재해석/재가공 없이</em> JSON 트리로 그대로 실어보낸다
 * (문자열 이스케이프 왕복 없음 → 내용 훼손 방지).
 */
public class ModuleType {

    private String moduleTypeCode;
    private String moduleName;
    private String categoryCode;

    /** JSONB 원문(문자열). 매퍼에서 PROP_SCHEMA_JSON::text 로 채운다. */
    private String propSchemaJson;

    private String templateKey;
    private String previewKey;
    private String useYn;
    private Integer sortOrder;
    private OffsetDateTime regDtm;
    private OffsetDateTime modDtm;

    public String getModuleTypeCode() { return moduleTypeCode; }
    public void setModuleTypeCode(String moduleTypeCode) { this.moduleTypeCode = moduleTypeCode; }

    public String getModuleName() { return moduleName; }
    public void setModuleName(String moduleName) { this.moduleName = moduleName; }

    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }

    /**
     * 응답 바디에 JSONB 원문을 <b>날 것(raw)</b>으로 삽입한다.
     * DB가 널(공백문자열 등)로 온 경우를 대비해 빈값이면 빈 객체로 대체(항상 유효 JSON 토큰 보장).
     */
    @JsonRawValue
    public String getPropSchemaJson() {
        return (propSchemaJson == null || propSchemaJson.isBlank()) ? "{}" : propSchemaJson;
    }
    public void setPropSchemaJson(String propSchemaJson) { this.propSchemaJson = propSchemaJson; }

    public String getTemplateKey() { return templateKey; }
    public void setTemplateKey(String templateKey) { this.templateKey = templateKey; }

    public String getPreviewKey() { return previewKey; }
    public void setPreviewKey(String previewKey) { this.previewKey = previewKey; }

    public String getUseYn() { return useYn; }
    public void setUseYn(String useYn) { this.useYn = useYn; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public OffsetDateTime getRegDtm() { return regDtm; }
    public void setRegDtm(OffsetDateTime regDtm) { this.regDtm = regDtm; }

    public OffsetDateTime getModDtm() { return modDtm; }
    public void setModDtm(OffsetDateTime modDtm) { this.modDtm = modDtm; }
}
