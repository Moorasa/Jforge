package com.jworks.forge.gen.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jworks.forge.gen.context.TemplateContextBuilder;
import com.jworks.forge.project.domain.ForgeProject;
import com.jworks.forge.screen.domain.ForgeScreen;

import freemarker.template.Configuration;

/**
 * P4-3 렌더 스모크/인젝션 골든: 7개 아티팩트 템플릿을 실제 TemplateContextBuilder + GenEscaper로
 * 렌더해 (1) 문법 골격 존재, (2) props 반영, (3) 🔒 자유문자열 전량 이스케이프(원문 유출 0)를 검증.
 *
 * <p>reviewer 🔒 인젝션 검수의 회귀 고정점. 값이 실행코드/템플릿인젝션으로 평가되지 않고
 * 이스케이프된 리터럴로만 산출됨을 확인한다.
 */
class ArchetypeTemplateSetTest {

    private final Configuration cfg = new CodeGenTemplateConfig().codeGenFreemarkerConfiguration();
    private final TemplateRenderer renderer = new TemplateRenderer(cfg);
    private final TemplateContextBuilder builder = new TemplateContextBuilder(new ObjectMapper());

    /** 스키마_DEFINITION_JSON §6 예시(userMgmt/admin). */
    private static final String DEFINITION_JSON = """
            {
              "schemaVersion": 1,
              "archetype": "MGMT_LIST_DETAIL",
              "stem": "userMgmt",
              "role": "admin",
              "slots": {
                "searchArea": [
                  { "instanceId": "searchFilterBar_1", "moduleTypeCode": "SEARCH_FILTER_BAR",
                    "props": { "filters": [ { "name": "useYn", "label": "사용여부", "options": "Y:사용,N:미사용" } ],
                               "keywordYn": true, "dateRangeYn": false } }
                ],
                "listToolbar": [
                  { "instanceId": "toolbar_1", "moduleTypeCode": "TOOLBAR",
                    "props": { "buttons": [
                      { "actionCode": "add", "label": "추가", "styleClass": "btn-primary" },
                      { "actionCode": "delete", "label": "삭제", "styleClass": "btn-secondary" } ] } }
                ],
                "listArea": [
                  { "instanceId": "tableView_1", "moduleTypeCode": "TABLE_VIEW",
                    "props": { "columns": [
                      { "name": "userId", "displayName": "사용자ID", "displayYn": true, "sortYn": true },
                      { "name": "userName", "displayName": "이름", "displayYn": true, "sortYn": true },
                      { "name": "regDtm", "displayName": "등록일시", "displayYn": true, "sortYn": false } ],
                      "selectMode": "checkbox", "pagingYn": true, "excelYn": true, "csvYn": false } }
                ],
                "detailBasic": [],
                "detailTabs": []
              }
            }
            """;

    /** 🔒 인젝션 페이로드를 displayName/label/styleClass/actionCode/name/options에 심은 문서. */
    private static final String MALICIOUS_JSON = """
            {
              "schemaVersion": 1,
              "archetype": "MGMT_LIST_DETAIL",
              "stem": "userMgmt",
              "role": "admin",
              "slots": {
                "searchArea": [
                  { "instanceId": "searchFilterBar_1", "moduleTypeCode": "SEARCH_FILTER_BAR",
                    "props": { "filters": [ { "name": "x\\"><script>alert(1)</script>", "label": "<img src=x onerror=alert(1)>", "options": "a:<b>,c:${7*7}" } ],
                               "keywordYn": true, "dateRangeYn": true } }
                ],
                "listToolbar": [
                  { "instanceId": "toolbar_1", "moduleTypeCode": "TOOLBAR",
                    "props": { "buttons": [
                      { "actionCode": "add\\" onclick=\\"alert(1)", "label": "</script><script>evil()</script>", "styleClass": "btn a<b> \\"x\\"" } ] } }
                ],
                "listArea": [
                  { "instanceId": "tableView_1", "moduleTypeCode": "TABLE_VIEW",
                    "props": { "columns": [
                      { "name": "u\\"></td><script>x</script>", "displayName": "</script><script>alert(1)</script>", "displayYn": true, "sortYn": true } ],
                      "selectMode": "checkbox", "pagingYn": true, "excelYn": true, "csvYn": true } }
                ]
              }
            }
            """;

    private static final List<String> TEMPLATE_KEYS = List.of(
            "archetype/mgmtListDetail/shell",
            "archetype/mgmtListDetail/list",
            "archetype/mgmtListDetail/listJs",
            "archetype/mgmtListDetail/listCss",
            "module/tableView",
            "module/tableViewJs",
            "module/tableViewCss");

    private ForgeScreen screen(String definitionJson) {
        ForgeScreen s = new ForgeScreen();
        s.setStem("userMgmt");
        s.setRoleCode("admin");
        s.setArchetypeCode("MGMT_LIST_DETAIL");
        s.setDefinitionJson(definitionJson);
        return s;
    }

    private ForgeProject project() {
        ForgeProject p = new ForgeProject();
        p.setPackageBase("com.jworks.forge");
        p.setJspBasePath("WEB-INF/views");
        p.setJsBasePath("static/js");
        p.setCssBasePath("static/css");
        p.setRuntimeVer("1.0.0");
        return p;
    }

    private Map<String, String> renderAll(String json) {
        Map<String, Object> model = builder.build(screen(json), project());
        // listArea 뷰 본문 include 접미사는 파이프라인(ScreenGenerator)이 GenArtifacts.MODULE_ARTIFACTS
        // (JSP nameSuffix)에서 파생해 렌더 직전 model에 넣는다(계약 §8.1, 물리적 단일 소스). 이 테스트는
        // 파이프라인을 거치지 않고 템플릿만 렌더하므로, 그 파이프라인 제공 키를 여기서 동일하게 채운다.
        // (fixture의 listArea 뷰=TABLE_VIEW → ListTableView.)
        model.put("listAreaViewSuffix", "ListTableView");
        java.util.LinkedHashMap<String, String> out = new java.util.LinkedHashMap<>();
        for (String key : TEMPLATE_KEYS) {
            out.put(key, renderer.render(key, model));
        }
        return out;
    }

    @Test
    void 정상_예시JSON을_7개_템플릿으로_렌더한다() {
        Map<String, String> outs = renderAll(DEFINITION_JSON);
        assertEquals(TEMPLATE_KEYS.size(), outs.size());

        String shell = outs.get("archetype/mgmtListDetail/shell");
        assertTrue(shell.contains("../common/header.jsp"), "header.jsp 매니페스트 참조");
        assertTrue(shell.contains("userMgmtList.jsp"), "List include 배선");

        String list = outs.get("archetype/mgmtListDetail/list");
        assertTrue(list.contains("사용여부"), "필터 라벨 렌더");
        assertTrue(list.contains("추가") && list.contains("삭제"), "툴바 버튼 라벨 렌더");
        assertTrue(list.contains("class=\"btn btn-primary\""), "styleClass cssToken 통과");
        assertTrue(list.contains("data-action=\"add\""), "actionCode 속성");
        assertTrue(list.contains("userMgmtListTableView.jsp"), "TableView include 배선");

        String listJs = outs.get("archetype/mgmtListDetail/listJs");
        assertTrue(listJs.contains("window.MagicIAM_JSUserMgmtAdmin"), "네임스페이스");
        assertTrue(listJs.contains("__defined"), "IIFE __defined 골격");
        assertTrue(listJs.contains("MagicIAM_JSCommonList.init"), "commonList 배선");

        String tv = outs.get("module/tableView");
        assertTrue(tv.contains("data-name=\"userId\""), "컬럼 name 속성");
        assertTrue(tv.contains("사용자ID") && tv.contains("이름") && tv.contains("등록일시"), "3컬럼 헤더");
        assertTrue(tv.contains("id=\"select-all\""), "selectMode=checkbox 반영");
        assertTrue(tv.contains("id=\"pagination\""), "pagingYn 반영");
        assertTrue(tv.contains("data-action=\"excelDownload\""), "excelYn 반영");
        assertFalse(tv.contains("data-action=\"csvUpload\""), "csvYn=false 미반영");

        String tvJs = outs.get("module/tableViewJs");
        assertTrue(tvJs.contains("window.MagicIAM_JSUserMgmtAdminTableView"), "TableView 네임스페이스");
        assertTrue(tvJs.contains("MagicIAM_JSCommonListTableView.init"), "commonListTableView 배선");
        assertTrue(tvJs.contains("selectionType = \"checkbox\"".replace("= ", "")) || tvJs.contains("\"checkbox\""),
                "selectionType 배선");
        assertTrue(tvJs.contains("name: \"userId\""), "컬럼 정의 배열");

        // 🔒 배너 0 / 스크립트릿 0 (모든 산출물).
        for (Map.Entry<String, String> e : outs.entrySet()) {
            assertFalse(e.getValue().contains("JWORKS"), e.getKey() + ": JWORKS 배너 유출");
            assertScriptletFree(e.getKey(), e.getValue());
        }
    }

    @Test
    void 악성_페이로드는_전부_이스케이프되어_실행코드로_평가되지_않는다() {
        Map<String, String> outs = renderAll(MALICIOUS_JSON);

        for (Map.Entry<String, String> e : outs.entrySet()) {
            String key = e.getKey();
            String body = e.getValue();

            // 원문 <script>/<img onerror>가 태그로 유출되면 안 됨.
            assertFalse(body.contains("<script>alert(1)</script>"),
                    key + ": <script> 원문 유출");
            assertFalse(body.contains("<script>evil()</script>"),
                    key + ": <script> 원문 유출");
            assertFalse(body.contains("<img src=x onerror=alert(1)>"),
                    key + ": onerror 원문 유출");
            assertFalse(body.contains("<script>x</script>"),
                    key + ": <script> 원문 유출");

            // FreeMarker 인터폴레이션(${7*7}=49)이 평가되면 안 됨 — 데이터로만 취급.
            assertFalse(body.contains("49"), key + ": ${7*7} 템플릿인젝션 평가됨");

            // 배너/스크립트릿 0 유지.
            assertFalse(body.contains("JWORKS"), key + ": JWORKS 배너 유출");
            assertScriptletFree(key, body);
        }

        // JS 문맥: </script>가 닫는 태그로 유출되면 안 됨(jsString이 \x3C/\/ 처리).
        String tvJs = outs.get("module/tableViewJs");
        assertFalse(tvJs.contains("</script>"), "JS 컬럼정의에서 </script> 유출");

        // CSS 토큰: 위반 토큰(a<b>, "x")은 드롭되고 유효 토큰(btn)만 남음.
        String list = outs.get("archetype/mgmtListDetail/list");
        assertFalse(list.contains("a<b>"), "styleClass 위반 토큰 유출");
        assertFalse(list.contains("\"x\""), "styleClass 위반 토큰(\"x\") 유출");
        assertTrue(list.contains("class=\"btn"), "cssToken 유효 토큰(btn) 통과");
    }

    /**
     * 🔒 B1 회귀 고정: role이 미검증 자유문자열로 생성 컨텍스트/템플릿에 흘러들던 저장형 XSS/JS 인젝션을
     * TemplateContextBuilder(계층1, §5.1 재검증)가 하드 차단함을 단언한다. 인젝션 페이로드 role은
     * 템플릿 렌더까지 가지 못하고 컨텍스트 구성 단계에서 TemplateContextException으로 거부되는 게 정상.
     */
    @Test
    void role_인젝션_페이로드는_컨텍스트_구성에서_하드차단된다() {
        List<String> maliciousRoles = List.of(
                "admin\"/><script>alert(1)</script>",
                "a\";evil()//",
                "a</script>",
                "ad min",
                "../admin",
                "Admin");
        for (String bad : maliciousRoles) {
            ForgeScreen s = screen(DEFINITION_JSON);
            s.setRoleCode(bad);
            assertThrows(com.jworks.forge.gen.context.TemplateContextException.class,
                    () -> builder.build(s, project()),
                    "인젝션 role은 컨텍스트 구성에서 하드 실패해야 한다: " + bad);
        }
    }

    /** 정상 role(admin/user)은 계층1 재검증을 통과해 정상 렌더된다(회귀 0). */
    @Test
    void 정상_role은_렌더_통과한다() {
        for (String ok : List.of("admin", "user")) {
            ForgeScreen s = screen(DEFINITION_JSON);
            s.setRoleCode(ok);
            Map<String, Object> model = builder.build(s, project());
            assertEquals(ok, model.get("role"));
            String shell = renderer.render("archetype/mgmtListDetail/shell", model);
            // 자산 URL은 /css/{role}/{stem}/ (파이프라인 쓰기경로와 일치, 리터럴 admin 중복 없음).
            assertTrue(shell.contains("/css/" + ok + "/"), "정상 role은 경로에 반영");
            assertFalse(shell.contains("/css/admin/" + ok + "/"), "자산 URL에 리터럴 admin 중복 없음");
        }
    }

    /** 스크립트릿(<% %>) 부재 검증. JSP 산출물만 대상(JS/CSS는 애초에 무관). */
    private void assertScriptletFree(String key, String body) {
        if (!key.contains("Js") && !key.contains("Css")) {
            // JSP: EL/지시자(<%@)와 스크립트릿(<% ), 표현식(<%=), 선언(<%!)을 구분.
            assertFalse(body.matches("(?s).*<%[^@\\-].*"),
                    key + ": 스크립트릿(<% ) 발견");
            assertFalse(body.contains("<%="), key + ": 표현식 스크립트릿(<%=) 발견");
            assertFalse(body.contains("<%!"), key + ": 선언 스크립트릿(<%!) 발견");
        }
    }
}
