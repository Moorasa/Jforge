package com.jworks.forge.screen.web;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

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

import com.jworks.forge.screen.domain.ForgeScreen;
import com.jworks.forge.screen.dto.ForgeScreenDuplicateRequest;
import com.jworks.forge.screen.dto.ForgeScreenRequest;
import com.jworks.forge.screen.service.ForgeScreenService;

/**
 * 화면 CRUD REST API (P3-1).
 * 목록은 메타만(경량), 단건은 DEFINITION_JSON 원문 포함.
 * DEFINITION_JSON 본문 갱신은 이 태스크 범위 밖(P3-6b 전용 엔드포인트).
 */
@RestController
@RequestMapping("/api/screens")
public class ForgeScreenController {

    private final ForgeScreenService service;

    public ForgeScreenController(ForgeScreenService service) {
        this.service = service;
    }

    /** 프로젝트별 화면 목록(DEFINITION_JSON 본문 제외). */
    @GetMapping
    public List<ForgeScreen> list(@RequestParam Long projectId) {
        return service.listByProject(projectId);
    }

    /** 단건(DEFINITION_JSON 원문 포함). */
    @GetMapping("/{id}")
    public ForgeScreen get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<ForgeScreen> create(@Valid @RequestBody ForgeScreenRequest req) {
        ForgeScreen created = service.create(req);
        return ResponseEntity
                .created(URI.create("/api/screens/" + created.getScreenId()))
                .body(created);
    }

    /** 메타 수정(screenName/archetypeCode/roleCode/statusCode). DEFINITION_JSON 본문 미갱신. */
    @PutMapping("/{id}")
    public ForgeScreen update(@PathVariable Long id, @Valid @RequestBody ForgeScreenRequest req) {
        return service.updateMeta(id, req);
    }

    /**
     * DEFINITION_JSON 본문 저장(P3-5b 서버 구조검증 경유). 본문 = DEFINITION_JSON 전체(원문 JSON).
     *
     * <p>이 엔드포인트가 <b>P3-6b 저장 API가 그대로 재사용/확장</b>할 정식 저장 지점이다
     * (경로 {@code PUT /api/screens/{id}/definition}, 요청 바디 raw JSON, 응답 = 저장 후 단건).
     * 본문은 {@code String}으로 무가공 수신해(Jackson 재직렬화로 인한 훼손 방지) 검증기·매퍼에 전달한다.
     * 구조 위반 시 {@link com.jworks.forge.screen.validation.DefinitionValidationException} → 400,
     * 미존재 화면은 404.
     */
    @PutMapping(value = "/{id}/definition", consumes = "application/json")
    public ForgeScreen updateDefinition(@PathVariable Long id, @RequestBody String definitionJsonRaw) {
        return service.updateDefinition(id, definitionJsonRaw);
    }

    /**
     * 화면 복제(P7-4): 원본 slots 보존 + 이름/stem 교체 새 화면 생성.
     * 원본 미존재 404, stem 형식/구조 위반 400.
     */
    @PostMapping("/{id}/duplicate")
    public ResponseEntity<ForgeScreen> duplicate(
            @PathVariable Long id, @Valid @RequestBody ForgeScreenDuplicateRequest req) {
        ForgeScreen created = service.duplicate(id, req);
        return ResponseEntity
                .created(URI.create("/api/screens/" + created.getScreenId()))
                .body(created);
    }

    /** 상태 삭제(STATUS_CODE='DELETED'). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
