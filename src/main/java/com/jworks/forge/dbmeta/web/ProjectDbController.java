package com.jworks.forge.dbmeta.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jworks.forge.dbmeta.dto.DbMetaDtos.ConnectionRequest;
import com.jworks.forge.dbmeta.dto.DbMetaDtos.ConnectionTestResult;
import com.jworks.forge.dbmeta.dto.DbMetaDtos.ConnectionView;
import com.jworks.forge.dbmeta.dto.DbMetaDtos.DbColumns;
import com.jworks.forge.dbmeta.dto.DbMetaDtos.DbTable;
import com.jworks.forge.dbmeta.service.ProjectDbService;

import jakarta.validation.Valid;

/**
 * 타겟 DB 접속정보 + 스키마 조회 API (P11, 계약 §15).
 *
 * <p>🔒 응답에는 비밀번호(평문·암호문)가 절대 포함되지 않는다. 조회는 읽기전용 카탈로그 한정이다.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/db")
public class ProjectDbController {

    private final ProjectDbService service;

    public ProjectDbController(ProjectDbService service) {
        this.service = service;
    }

    /** 저장된 접속정보(비밀번호 제외). 미설정이면 configured=false. */
    @GetMapping
    public ConnectionView get(@PathVariable Long projectId) {
        return service.get(projectId);
    }

    /** 접속정보 저장(비밀번호는 암호화 저장). */
    @PutMapping
    public ConnectionView save(@PathVariable Long projectId,
                               @Valid @RequestBody ConnectionRequest request) {
        return service.save(projectId, request);
    }

    /** 저장 없이 연결만 시험. */
    @PostMapping("/test")
    public ConnectionTestResult test(@PathVariable Long projectId,
                                     @Valid @RequestBody ConnectionRequest request) {
        return service.test(projectId, request);
    }

    /** 스키마의 테이블/뷰 목록(선택 keyword 부분일치). */
    @GetMapping("/tables")
    public List<DbTable> tables(@PathVariable Long projectId,
                                @RequestParam(name = "keyword", required = false) String keyword) {
        return service.tables(projectId, keyword);
    }

    /** 테이블의 컬럼 목록(+PK 표시). */
    @GetMapping("/tables/{table}/columns")
    public DbColumns columns(@PathVariable Long projectId, @PathVariable String table) {
        return new DbColumns(table, service.columns(projectId, table));
    }

    /** 접속정보 삭제. */
    @DeleteMapping
    public ResponseEntity<Void> delete(@PathVariable Long projectId) {
        service.delete(projectId);
        return ResponseEntity.noContent().build();
    }
}
