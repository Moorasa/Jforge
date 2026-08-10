package com.jworks.forge.gen.hist;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonRawValue;

/**
 * TB_FRG_GEN_HIST 엔티티. 생성(파일쓰기) 1회의 산출 파일 목록/결과 이력 (P4-5).
 * (컬럼 대문자스네이크 ↔ 필드 카멜은 map-underscore-to-camel-case로 매핑)
 *
 * <p>{@code FILE_LIST_JSON}(JSONB)은 <b>원문 그대로</b> 왕복한다({@link com.jworks.forge.screen.domain.ForgeScreen}의
 * DEFINITION_JSON 매핑과 동일 방식). 매퍼가 JSONB를 {@code ::text}로 뽑아 String으로 받고,
 * 응답 직렬화 시 {@link JsonRawValue}로 <em>재해석/재가공 없이</em> JSON 트리로 그대로 실어보낸다.
 * INSERT 시에는 {@link GenerationService}가 Jackson으로 안전 직렬화한 문자열을 넣고 매퍼가
 * {@code ::jsonb}로 캐스팅한다(문자열 조립 0).
 */
public class GenHist {

    private Long genHistId;
    private Long screenId;
    private OffsetDateTime genAt;

    /** JSONB 원문(문자열). 조회 시 FILE_LIST_JSON::text 로 채운다. INSERT 시 Jackson 직렬화 결과. */
    private String fileListJson;

    private String resultCode;

    public Long getGenHistId() { return genHistId; }
    public void setGenHistId(Long genHistId) { this.genHistId = genHistId; }

    public Long getScreenId() { return screenId; }
    public void setScreenId(Long screenId) { this.screenId = screenId; }

    public OffsetDateTime getGenAt() { return genAt; }
    public void setGenAt(OffsetDateTime genAt) { this.genAt = genAt; }

    /**
     * 응답 바디에 JSONB 원문을 <b>날 것(raw)</b>으로 삽입한다.
     * DB가 널/공백으로 온 경우를 대비해 빈값이면 빈 배열로 대체(항상 유효 JSON 토큰 보장).
     */
    @JsonRawValue
    public String getFileListJson() {
        return (fileListJson == null || fileListJson.isBlank()) ? "[]" : fileListJson;
    }
    public void setFileListJson(String fileListJson) { this.fileListJson = fileListJson; }

    public String getResultCode() { return resultCode; }
    public void setResultCode(String resultCode) { this.resultCode = resultCode; }
}
