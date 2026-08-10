package com.jworks.forge.gen.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jworks.forge.project.domain.ForgeProject;
import com.jworks.forge.screen.domain.ForgeScreen;

/**
 * P4-2 TemplateContextBuilder 단위테스트: 계약 §2.1 키 규격 변환, 접근 경로,
 * stem 재검증/불일치 정책, forward-compat 스킵.
 */
class TemplateContextBuilderTest {

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

    private ForgeScreen screen(String stem, String definitionJson) {
        ForgeScreen s = new ForgeScreen();
        s.setStem(stem);
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

    @Test
    @SuppressWarnings("unchecked")
    void 예시JSON을_계약_키규격_맵으로_변환한다() {
        Map<String, Object> model = builder.build(screen("userMgmt", DEFINITION_JSON), project());

        assertEquals("userMgmt", model.get("stem"));
        assertEquals("admin", model.get("role"));
        assertEquals("MGMT_LIST_DETAIL", model.get("archetype"));
        assertEquals("com.jworks.forge", model.get("packageBase"));
        assertEquals("WEB-INF/views", model.get("jspBasePath"));
        assertEquals("static/js", model.get("jsBasePath"));
        assertEquals("static/css", model.get("cssBasePath"));
        assertEquals("1.0.0", model.get("runtimeVer"));

        Map<String, List<Map<String, Object>>> slots =
                (Map<String, List<Map<String, Object>>>) model.get("slots");

        // AC 접근 경로: slots["listArea"][0]["props"]["columns"]
        Map<String, Object> tableView = slots.get("listArea").get(0);
        assertEquals("tableView_1", tableView.get("instanceId"));
        assertEquals("TABLE_VIEW", tableView.get("moduleTypeCode"));
        Map<String, Object> props = (Map<String, Object>) tableView.get("props");
        List<Map<String, Object>> columns = (List<Map<String, Object>>) props.get("columns");
        assertEquals(3, columns.size());
        assertEquals("userId", columns.get(0).get("name"));
        assertEquals("사용자ID", columns.get(0).get("displayName"));   // 원문 보존(이스케이프 없음)
        assertEquals(Boolean.TRUE, props.get("pagingYn"));
        assertEquals("checkbox", props.get("selectMode"));

        // 순서 보존 확인(배열 순서 = 렌더 순서).
        assertEquals("regDtm", columns.get(2).get("name"));

        // 다른 슬롯도 정상 변환.
        assertEquals("SEARCH_FILTER_BAR", slots.get("searchArea").get(0).get("moduleTypeCode"));
        assertEquals("btn-primary",
                ((List<Map<String, Object>>) ((Map<String, Object>) slots.get("listToolbar").get(0)
                        .get("props")).get("buttons")).get(0).get("styleClass"));
    }

    @Test
    void stem_정규식_위반값은_하드실패() {
        for (String bad : List.of("../x", "1abc", "a b", "a.b")) {
            assertThrows(TemplateContextException.class,
                    () -> builder.build(screen(bad, DEFINITION_JSON), project()),
                    "위반 stem은 하드 실패해야 한다: " + bad);
        }
    }

    @Test
    void packageBase_정규식_위반값은_하드실패() {
        // 🔒 P4-6: packageBase는 stub 폴더 경로(점→슬래시)로 조립되므로 앵커드 정규식 재검증.
        for (String bad : List.of(
                "com..jworks", "com/jworks", "com.jworks;drop", "com.Jworks",
                "../../etc", "com.jworks-forge", "1com.jworks", "com.jworks.")) {
            ForgeProject p = project();
            p.setPackageBase(bad);
            assertThrows(TemplateContextException.class,
                    () -> builder.build(screen("userMgmt", DEFINITION_JSON), p),
                    "위반 packageBase는 하드 실패해야 한다: " + bad);
        }
    }

    @Test
    void packageBase_정상값은_통과() {
        for (String ok : List.of("com.jworks.forge", "a", "com.a1.b2c3")) {
            ForgeProject p = project();
            p.setPackageBase(ok);
            Map<String, Object> model = builder.build(screen("userMgmt", DEFINITION_JSON), p);
            assertEquals(ok, model.get("packageBase"), "정상 packageBase 통과: " + ok);
        }
    }

    @Test
    void stem_불일치시_메타_우선() {
        // 메타 stem = "orderMgmt", DEFINITION 내부 stem = "userMgmt" → 메타 우선(경고).
        Map<String, Object> model = builder.build(screen("orderMgmt", DEFINITION_JSON), project());
        assertEquals("orderMgmt", model.get("stem"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 미지원_slotKey와_moduleTypeCode는_스킵되고_나머지는_정상() {
        String json = """
                {
                  "schemaVersion": 1, "archetype": "MGMT_LIST_DETAIL",
                  "stem": "userMgmt", "role": "admin",
                  "slots": {
                    "listArea": [
                      { "instanceId": "tableView_1", "moduleTypeCode": "TABLE_VIEW", "props": { "columns": [] } },
                      { "instanceId": "future_1", "moduleTypeCode": "FUTURE_MODULE", "props": {} }
                    ],
                    "unknownSlot": [
                      { "instanceId": "x_1", "moduleTypeCode": "TABLE_VIEW", "props": {} }
                    ]
                  }
                }
                """;
        Map<String, Object> model = builder.build(screen("userMgmt", json), project());
        Map<String, List<Map<String, Object>>> slots =
                (Map<String, List<Map<String, Object>>>) model.get("slots");

        // 미지원 slotKey 스킵.
        assertFalse(slots.containsKey("unknownSlot"), "미지원 slotKey는 스킵되어야 한다");
        // 미지원 moduleTypeCode 인스턴스만 스킵, TABLE_VIEW는 남는다.
        assertEquals(1, slots.get("listArea").size());
        assertEquals("tableView_1", slots.get("listArea").get(0).get("instanceId"));
    }

    @Test
    void definitionJson_null이어도_메타로_구성된다() {
        Map<String, Object> model = builder.build(screen("userMgmt", null), project());
        assertEquals("userMgmt", model.get("stem"));
        assertTrue(((Map<?, ?>) model.get("slots")).isEmpty());
    }

    @Test
    void definitionJson_파싱실패는_하드실패() {
        assertThrows(TemplateContextException.class,
                () -> builder.build(screen("userMgmt", "{ not json"), project()));
    }
}
