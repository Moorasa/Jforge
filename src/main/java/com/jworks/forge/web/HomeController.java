package com.jworks.forge.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 도그푸딩 셸 진입 (P0-5).
 * JSP 뷰 리졸버(prefix /WEB-INF/views/, suffix .jsp)로 admin/home.jsp 렌더.
 */
@Controller
public class HomeController {

    @GetMapping({"/", "/admin"})
    public String home() {
        return "admin/home";
    }
}
