package com.jworks.forge.project.domain;

import java.time.OffsetDateTime;

/**
 * TB_FRG_PROJECT 엔티티. 빌더가 산출물을 쓰는 타겟 프로젝트.
 * (컬럼 대문자스네이크 ↔ 필드 카멜은 map-underscore-to-camel-case로 매핑)
 */
public class ForgeProject {

    private Long projectId;
    private String projectName;
    private String targetRootPath;
    private String packageBase;
    private String jspBasePath;
    private String jsBasePath;
    private String cssBasePath;
    private String dbTypeCode;
    private String runtimeVer;
    private String useYn;
    private OffsetDateTime regDtm;
    private OffsetDateTime modDtm;

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public String getTargetRootPath() { return targetRootPath; }
    public void setTargetRootPath(String targetRootPath) { this.targetRootPath = targetRootPath; }

    public String getPackageBase() { return packageBase; }
    public void setPackageBase(String packageBase) { this.packageBase = packageBase; }

    public String getJspBasePath() { return jspBasePath; }
    public void setJspBasePath(String jspBasePath) { this.jspBasePath = jspBasePath; }

    public String getJsBasePath() { return jsBasePath; }
    public void setJsBasePath(String jsBasePath) { this.jsBasePath = jsBasePath; }

    public String getCssBasePath() { return cssBasePath; }
    public void setCssBasePath(String cssBasePath) { this.cssBasePath = cssBasePath; }

    public String getDbTypeCode() { return dbTypeCode; }
    public void setDbTypeCode(String dbTypeCode) { this.dbTypeCode = dbTypeCode; }

    public String getRuntimeVer() { return runtimeVer; }
    public void setRuntimeVer(String runtimeVer) { this.runtimeVer = runtimeVer; }

    public String getUseYn() { return useYn; }
    public void setUseYn(String useYn) { this.useYn = useYn; }

    public OffsetDateTime getRegDtm() { return regDtm; }
    public void setRegDtm(OffsetDateTime regDtm) { this.regDtm = regDtm; }

    public OffsetDateTime getModDtm() { return modDtm; }
    public void setModDtm(OffsetDateTime modDtm) { this.modDtm = modDtm; }
}
