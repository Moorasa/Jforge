package com.jworks.forge.dbmeta.domain;

/**
 * TB_FRG_PROJECT_DB 엔티티 (P11). 타겟 DB 접속 좌표 + 암호화된 비밀번호.
 *
 * <p>🔒 {@code dbPasswordEnc}는 AES-GCM 암호문(base64)이며 <b>API 응답에 포함하지 않는다</b>
 * (컨트롤러가 별도 뷰 DTO로 변환).
 */
public class ProjectDbConnection {

    private Long projectId;
    private String dbHost;
    private Integer dbPort;
    private String dbName;
    private String dbSchema;
    private String dbUsername;
    private String dbPasswordEnc;

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public String getDbHost() { return dbHost; }
    public void setDbHost(String dbHost) { this.dbHost = dbHost; }

    public Integer getDbPort() { return dbPort; }
    public void setDbPort(Integer dbPort) { this.dbPort = dbPort; }

    public String getDbName() { return dbName; }
    public void setDbName(String dbName) { this.dbName = dbName; }

    public String getDbSchema() { return dbSchema; }
    public void setDbSchema(String dbSchema) { this.dbSchema = dbSchema; }

    public String getDbUsername() { return dbUsername; }
    public void setDbUsername(String dbUsername) { this.dbUsername = dbUsername; }

    public String getDbPasswordEnc() { return dbPasswordEnc; }
    public void setDbPasswordEnc(String dbPasswordEnc) { this.dbPasswordEnc = dbPasswordEnc; }
}
