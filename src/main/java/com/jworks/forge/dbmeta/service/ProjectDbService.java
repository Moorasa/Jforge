package com.jworks.forge.dbmeta.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jworks.forge.dbmeta.SecretCipher;
import com.jworks.forge.dbmeta.TargetDbUrl;
import com.jworks.forge.dbmeta.domain.ProjectDbConnection;
import com.jworks.forge.dbmeta.dto.DbMetaDtos.ConnectionRequest;
import com.jworks.forge.dbmeta.dto.DbMetaDtos.ConnectionTestResult;
import com.jworks.forge.dbmeta.dto.DbMetaDtos.ConnectionView;
import com.jworks.forge.dbmeta.dto.DbMetaDtos.DbColumn;
import com.jworks.forge.dbmeta.dto.DbMetaDtos.DbTable;
import com.jworks.forge.dbmeta.mapper.ProjectDbConnectionMapper;
import com.jworks.forge.project.service.ForgeProjectService;

/**
 * 프로젝트별 타겟 DB 접속정보 관리 + 스키마 조회 파사드 (P11, 계약 §15).
 *
 * <p>🔒 규약:
 * <ul>
 *   <li>비밀번호는 {@link SecretCipher}로 <b>암호화해서만</b> 저장하고, 어떤 응답에도 싣지 않는다.</li>
 *   <li>접속 좌표는 {@link TargetDbUrl} 게이트를 통과한 것만 저장/사용한다(URL 문자열 미저장).</li>
 *   <li>암호화 키가 없으면({@link SecretCipher#isAvailable()} false) 저장·조회를 <b>거부</b>한다 —
 *       평문 폴백은 없다.</li>
 * </ul>
 */
@Service
public class ProjectDbService {

    private final ProjectDbConnectionMapper mapper;
    private final ForgeProjectService projectService;
    private final SecretCipher cipher;
    private final DbIntrospectionService introspection;

    public ProjectDbService(ProjectDbConnectionMapper mapper,
                            ForgeProjectService projectService,
                            SecretCipher cipher,
                            DbIntrospectionService introspection) {
        this.mapper = mapper;
        this.projectService = projectService;
        this.cipher = cipher;
        this.introspection = introspection;
    }

    /** 저장된 접속정보(비밀번호 제외). 미설정이면 {@code configured=false}. */
    @Transactional(readOnly = true)
    public ConnectionView get(Long projectId) {
        projectService.get(projectId); // 없으면 404
        ProjectDbConnection saved = mapper.selectByProject(projectId);
        if (saved == null) {
            return ConnectionView.none(cipher.isAvailable());
        }
        return new ConnectionView(true, cipher.isAvailable(),
                saved.getDbHost(), saved.getDbPort(), saved.getDbName(),
                saved.getDbSchema(), saved.getDbUsername());
    }

    /** 접속정보 저장(비밀번호 암호화). 비밀번호가 비어 있으면 기존 값을 유지한다. */
    @Transactional
    public ConnectionView save(Long projectId, ConnectionRequest request) {
        projectService.get(projectId); // 없으면 404
        requireSecret();
        TargetDbUrl target = target(request);
        TargetDbUrl.requireValidUsername(request.username());

        String encrypted = cipher.encrypt(resolvePassword(projectId, request.password()));

        ProjectDbConnection entity = new ProjectDbConnection();
        entity.setProjectId(projectId);
        entity.setDbHost(target.host());
        entity.setDbPort(target.port());
        entity.setDbName(target.database());
        entity.setDbSchema(target.schema());
        entity.setDbUsername(request.username());
        entity.setDbPasswordEnc(encrypted);
        mapper.upsert(entity);

        return new ConnectionView(true, true, target.host(), target.port(),
                target.database(), target.schema(), request.username());
    }

    /** 저장 없이 연결만 시험한다. 비밀번호가 비면 저장분을 쓴다(수정 화면에서 재입력 강요 방지). */
    @Transactional(readOnly = true)
    public ConnectionTestResult test(Long projectId, ConnectionRequest request) {
        projectService.get(projectId);
        requireSecret();
        TargetDbUrl target = target(request);
        return introspection.test(target, request.username(), resolvePassword(projectId, request.password()));
    }

    /** 저장된 접속정보로 테이블/뷰 목록을 조회한다. */
    @Transactional(readOnly = true)
    public List<DbTable> tables(Long projectId, String keyword) {
        Saved saved = requireSaved(projectId);
        return introspection.listTables(saved.target(), saved.username(), saved.password(), keyword);
    }

    /** 저장된 접속정보로 컬럼 목록을 조회한다. */
    @Transactional(readOnly = true)
    public List<DbColumn> columns(Long projectId, String table) {
        Saved saved = requireSaved(projectId);
        return introspection.listColumns(saved.target(), saved.username(), saved.password(), table);
    }

    /** 접속정보 삭제. */
    @Transactional
    public void delete(Long projectId) {
        projectService.get(projectId);
        mapper.deleteByProject(projectId);
    }

    // ---------------------------------------------------------------- internals

    /** 복호화까지 끝난 접속 일습(서비스 내부 전용 — 외부로 새지 않는다). */
    private record Saved(TargetDbUrl target, String username, String password) {
    }

    private Saved requireSaved(Long projectId) {
        projectService.get(projectId); // 없으면 404
        requireSecret();
        ProjectDbConnection saved = mapper.selectByProject(projectId);
        if (saved == null) {
            throw new IllegalArgumentException("이 프로젝트에 DB 접속정보가 없습니다. 먼저 연결을 설정하세요.");
        }
        TargetDbUrl target = TargetDbUrl.of(saved.getDbHost(), saved.getDbPort(),
                saved.getDbName(), saved.getDbSchema());
        return new Saved(target, saved.getDbUsername(), cipher.decrypt(saved.getDbPasswordEnc()));
    }

    private TargetDbUrl target(ConnectionRequest request) {
        return TargetDbUrl.of(request.host(), request.port(), request.database(), request.schema());
    }

    /** 요청 비밀번호가 비면 저장된 것을 복호화해 쓴다. 둘 다 없으면 거부. */
    private String resolvePassword(Long projectId, String provided) {
        if (provided != null && !provided.isBlank()) {
            return provided;
        }
        ProjectDbConnection saved = mapper.selectByProject(projectId);
        if (saved == null || saved.getDbPasswordEnc() == null) {
            throw new IllegalArgumentException("비밀번호를 입력하세요.");
        }
        return cipher.decrypt(saved.getDbPasswordEnc());
    }

    private void requireSecret() {
        if (!cipher.isAvailable()) {
            throw new IllegalArgumentException(
                    "암호화 키를 준비하지 못해 DB 연결 기능을 쓸 수 없습니다(forge.secret.key 설정 또는 홈 디렉터리 쓰기 권한 확인).");
        }
    }
}
