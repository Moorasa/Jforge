package com.jworks.forge.project.web;

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
import org.springframework.web.bind.annotation.RestController;

import com.jworks.forge.project.domain.ForgeProject;
import com.jworks.forge.project.dto.ForgeProjectRequest;
import com.jworks.forge.project.service.ForgeProjectService;

/**
 * 프로젝트 CRUD REST API (P1-2).
 */
@RestController
@RequestMapping("/api/projects")
public class ForgeProjectController {

    private final ForgeProjectService service;

    public ForgeProjectController(ForgeProjectService service) {
        this.service = service;
    }

    @GetMapping
    public List<ForgeProject> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ForgeProject get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<ForgeProject> create(@Valid @RequestBody ForgeProjectRequest req) {
        ForgeProject created = service.create(req);
        return ResponseEntity
                .created(URI.create("/api/projects/" + created.getProjectId()))
                .body(created);
    }

    @PutMapping("/{id}")
    public ForgeProject update(@PathVariable Long id, @Valid @RequestBody ForgeProjectRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
