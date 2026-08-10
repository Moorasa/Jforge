package com.jworks.forge.project.service;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jworks.forge.common.web.NotFoundException;
import com.jworks.forge.project.domain.ForgeProject;
import com.jworks.forge.project.dto.ForgeProjectRequest;
import com.jworks.forge.project.mapper.ForgeProjectMapper;

/**
 * 프로젝트 CRUD 비즈니스 로직. TARGET_ROOT_PATH 절대성 검증 포함.
 */
@Service
public class ForgeProjectService {

    private static final String DEFAULT_JSP_BASE_PATH = "jsp";
    private static final String DEFAULT_JS_BASE_PATH = "js";
    private static final String DEFAULT_CSS_BASE_PATH = "css";

    private final ForgeProjectMapper mapper;

    public ForgeProjectService(ForgeProjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<ForgeProject> list() {
        return mapper.selectActiveList();
    }

    public ForgeProject get(Long id) {
        ForgeProject p = mapper.selectActiveById(id);
        if (p == null) {
            throw new NotFoundException("project not found: " + id);
        }
        return p;
    }

    @Transactional
    public ForgeProject create(ForgeProjectRequest req) {
        validateTargetRootPath(req.targetRootPath());
        ForgeProject p = toEntity(new ForgeProject(), req);
        mapper.insert(p);
        return get(p.getProjectId());
    }

    @Transactional
    public ForgeProject update(Long id, ForgeProjectRequest req) {
        get(id); // 존재 확인(없으면 404)
        validateTargetRootPath(req.targetRootPath());
        ForgeProject p = toEntity(new ForgeProject(), req);
        p.setProjectId(id);
        mapper.update(p);
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        int affected = mapper.softDelete(id);
        if (affected == 0) {
            throw new NotFoundException("project not found: " + id);
        }
    }

    /** TARGET_ROOT_PATH는 반드시 절대경로여야 한다(경로안전 계층의 루트가 되므로). */
    private void validateTargetRootPath(String targetRootPath) {
        try {
            if (!Path.of(targetRootPath).isAbsolute()) {
                throw new IllegalArgumentException("targetRootPath는 절대경로여야 함: " + targetRootPath);
            }
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("targetRootPath 형식 오류: " + targetRootPath);
        }
    }

    private ForgeProject toEntity(ForgeProject p, ForgeProjectRequest req) {
        p.setProjectName(req.projectName());
        p.setTargetRootPath(req.targetRootPath());
        p.setPackageBase(emptyToNull(req.packageBase()));
        p.setJspBasePath(defaultIfBlank(req.jspBasePath(), DEFAULT_JSP_BASE_PATH));
        p.setJsBasePath(defaultIfBlank(req.jsBasePath(), DEFAULT_JS_BASE_PATH));
        p.setCssBasePath(defaultIfBlank(req.cssBasePath(), DEFAULT_CSS_BASE_PATH));
        p.setDbTypeCode(req.dbTypeCode());
        p.setRuntimeVer(req.runtimeVer());
        return p;
    }

    private String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }
}
