package com.jworks.forge.catalog.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 모듈 카탈로그/속성스키마 폼렌더 도그푸딩 화면 (P2-5). 3종 세트(JSP+JS+CSS)의 JSP 진입.
 * 좌: 모듈 카탈로그 목록, 우: 선택 모듈의 PROP_SCHEMA_JSON 속성폼 프리뷰.
 * 데이터는 JS가 /api/module-types(목록)·/api/module-types/{code}(스키마)를 소비.
 */
@Controller
public class CatalogPageController {

    @GetMapping("/admin/catalog")
    public String list() {
        return "admin/catalog/list";
    }
}
