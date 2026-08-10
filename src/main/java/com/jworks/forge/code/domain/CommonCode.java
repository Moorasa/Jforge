package com.jworks.forge.code.domain;

/**
 * TB_FRG_COMMON_CODE. 화면 셀렉트/코드 렌더용 공통코드.
 */
public class CommonCode {

    private String grpCode;
    private String code;
    private String codeName;
    private Integer sortOrder;
    private String useYn;

    public String getGrpCode() { return grpCode; }
    public void setGrpCode(String grpCode) { this.grpCode = grpCode; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getCodeName() { return codeName; }
    public void setCodeName(String codeName) { this.codeName = codeName; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getUseYn() { return useYn; }
    public void setUseYn(String useYn) { this.useYn = useYn; }
}
