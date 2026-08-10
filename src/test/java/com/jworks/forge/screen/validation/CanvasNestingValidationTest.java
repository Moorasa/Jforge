package com.jworks.forge.screen.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jworks.forge.catalog.domain.ModuleType;
import com.jworks.forge.catalog.mapper.ModuleTypeMapper;
import com.jworks.forge.code.domain.CommonCode;
import com.jworks.forge.code.mapper.CommonCodeMapper;

/**
 * §17.8(P13-7) 캔버스 중첩 참조 검증.
 *
 * <p>저장 시점에는 <b>하드 실패</b>로 원인을 알리고, 산출 시점({@code TemplateContextBuilder})에는
 * 같은 위반을 루트로 수렴시킨다({@code FreeCanvasGenerationTest}) — 이 테스트는 앞쪽 게이트를 고정한다.
 */
class CanvasNestingValidationTest {

    private DefinitionValidator validator;

    @BeforeEach
    void setUp() {
        CommonCodeMapper commonCodeMapper = mock(CommonCodeMapper.class);
        ModuleTypeMapper moduleTypeMapper = mock(ModuleTypeMapper.class);
        // 공통코드/카테고리 실존은 이 테스트의 관심사가 아니므로 통과시킨다.
        when(commonCodeMapper.selectByGroup(anyString()))
                .thenReturn(List.of(code("FREE_CANVAS"), code("admin")));
        when(moduleTypeMapper.selectActiveByCode("PANEL")).thenReturn(moduleType("LAYOUT"));
        when(moduleTypeMapper.selectActiveByCode("BUTTON")).thenReturn(moduleType("CONTROL"));
        when(moduleTypeMapper.selectActiveByCode("TABLE_VIEW")).thenReturn(moduleType("VIEW"));
        validator = new DefinitionValidator(commonCodeMapper, moduleTypeMapper);
    }

    private static CommonCode code(String value) {
        CommonCode c = new CommonCode();
        c.setCode(value);
        return c;
    }

    private static ModuleType moduleType(String category) {
        ModuleType mt = new ModuleType();
        mt.setCategoryCode(category);
        return mt;
    }

    private static String definition(String items) {
        return """
                {
                  "schemaVersion": 1, "archetype": "FREE_CANVAS", "stem": "freeScreen", "role": "admin",
                  "slots": { "canvasArea": [ """ + items + " ] }\n}";
    }

    private static String item(String id, String code, String parentId) {
        return "{ \"instanceId\": \"" + id + "\", \"moduleTypeCode\": \"" + code + "\", \"props\": {"
                + (parentId == null ? "" : " \"layoutParentId\": \"" + parentId + "\"") + " } }";
    }

    @Test
    void 패널_자식은_통과한다() {
        assertDoesNotThrow(() -> validator.validate(definition(
                item("panel_1", "PANEL", null) + ", " + item("tableView_1", "TABLE_VIEW", "panel_1"))));
    }

    @Test
    void 없는_부모는_거부된다() {
        var e = assertThrows(DefinitionValidationException.class, () -> validator.validate(definition(
                item("tableView_1", "TABLE_VIEW", "panelGhost"))));
        assertTrue(messageOf(e).contains("캔버스에 없습니다"), messageOf(e));
    }

    @Test
    void 컨테이너가_아닌_부모는_거부된다() {
        var e = assertThrows(DefinitionValidationException.class, () -> validator.validate(definition(
                item("button_1", "BUTTON", null) + ", " + item("table_1", "TABLE_VIEW", "button_1"))));
        assertTrue(messageOf(e).contains("담을 수 없는"), messageOf(e));
    }

    @Test
    void 자기_자신을_부모로_두면_거부된다() {
        var e = assertThrows(DefinitionValidationException.class, () -> validator.validate(definition(
                item("panel_1", "PANEL", "panel_1"))));
        assertTrue(messageOf(e).contains("자기 자신"), messageOf(e));
    }

    @Test
    void 순환은_거부된다() {
        var e = assertThrows(DefinitionValidationException.class, () -> validator.validate(definition(
                item("panelA", "PANEL", "panelB") + ", " + item("panelB", "PANEL", "panelA"))));
        assertTrue(messageOf(e).contains("순환"), messageOf(e));
    }

    @Test
    void 최대_깊이를_넘으면_거부된다() {
        // p1 > p2 > p3 > p4 (4단계) — MAX_CANVAS_DEPTH=3 초과.
        String items = item("p1", "PANEL", null) + ", " + item("p2", "PANEL", "p1") + ", "
                + item("p3", "PANEL", "p2") + ", " + item("p4", "PANEL", "p3");
        var e = assertThrows(DefinitionValidationException.class,
                () -> validator.validate(definition(items)));
        assertTrue(messageOf(e).contains("중첩 깊이"), messageOf(e));
    }

    private String messageOf(DefinitionValidationException e) {
        List<String> violations = e.getViolations();
        return violations == null ? String.valueOf(e.getMessage()) : String.join(" | ", violations);
    }
}
