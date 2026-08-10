package com.jworks.forge.project.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 프로젝트 관리 도그푸딩 화면 (P1-3). 3종 세트(JSP+JS+CSS)의 JSP 진입.
 */
@Controller
public class ProjectPageController {

    @GetMapping("/admin/projects")
    public String list() {
        return "admin/project/list";
    }
}
