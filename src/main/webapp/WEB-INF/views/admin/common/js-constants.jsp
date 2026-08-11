<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%-- 서버주입 상수: ViewType/Archetype 등 코드 상수. 스크립트릿 없이 EL만 사용. --%>
<script>
    // 빌더가 타겟 프로젝트별로 생성/관리할 상수 묶음 (P0-5 최소 시드)
    window.JWorks_JSForge = window.JWorks_JSForge || {};
    window.JWorks_JSForge.Constants = window.JWorks_JSForge.Constants || {
        VIEW_TYPE: { TABLE: "TABLE", TREE: "TREE", CARD: "CARD", FORM: "FORM" },
        ARCHETYPE: { MGMT_LIST_DETAIL: "MGMT_LIST_DETAIL", SIMPLE_LIST: "SIMPLE_LIST", DUAL_LAYOUT: "DUAL_LAYOUT" },
        ROLE: { ADMIN: "admin", USER: "user" }
    };

    // 번들 런타임(commonListTableView.js 등, JWorks 원본 그대로 복사됨)이 직접 참조하는
    // 전역 `Constants`. 네임스페이스로 감싸면 원본 코드가 ReferenceError를 낸다 — 원본 계약대로
    // 전역에 그대로 노출해야 한다(고객사별 튜닝값, 현재는 최소 기본값 시드).
    "undefined" == typeof Constants && (Constants = {
        DEFAULT_TABLE_VIEW_COUNT_PER_PAGE: 10,
        MIN_TABLE_VIEW_COUNT_PER_PAGE: 10,
        MAX_COUNT_PER_PAGE: 100,
        DEFAULT_CARD_VIEW_COUNT_PER_PAGE: 12,
        MIN_CARD_VIEW_COUNT_PER_PAGE: 4,
        DEFAULT_AJAX_TIMEOUT: 30000,
        DEFAULT_BASIC_INFO_COLLAPSE: false,
        TAB_VISIBILITY: {},
        DUAL_LAYOUT_TABLE_VIDE_DEFAULT_LEFT_FLEX: "1",
        DUAL_LAYOUT_TABLE_VIDE_DEFAULT_RIGHT_FLEX: "1",
        DUAL_LAYOUT_TREE_VIDE_DEFAULT_LEFT_FLEX: "1",
        DUAL_LAYOUT_TREE_VIDE_DEFAULT_RIGHT_FLEX: "1",
        DUAL_LAYOUT_CARD_VIDE_DEFAULT_LEFT_FLEX: "1",
        DUAL_LAYOUT_CARD_VIDE_DEFAULT_RIGHT_FLEX: "1"
    });
</script>
