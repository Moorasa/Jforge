package com.jworks.forge.screen.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jworks.forge.project.mapper.ForgeProjectMapper;
import com.jworks.forge.screen.domain.ForgeScreen;
import com.jworks.forge.screen.dto.ForgeScreenDuplicateRequest;
import com.jworks.forge.screen.mapper.ForgeScreenMapper;
import com.jworks.forge.screen.validation.DefinitionValidationException;
import com.jworks.forge.screen.validation.DefinitionValidator;

/**
 * P7-4 화면 복제({@link ForgeScreenService#duplicate}) 검증.
 *
 * <p>고정 계약: (1) slots(모듈 배치·props)는 <b>무가공 보존</b>, (2) 골격 stem 만 새 값으로 교체
 * (archetype/role/schemaVersion 은 원본 유지), (3) 새 화면 메타는 원본 프로젝트/아키타입/role +
 * 새 이름/stem + DRAFT, (4) 교체 결과 전체가 {@link DefinitionValidator} 재검증을 경유.
 */
class ForgeScreenDuplicateTest {

    private ForgeScreenMapper mapper;
    private DefinitionValidator validator;
    private ForgeScreenService service;
    private final ObjectMapper json = new ObjectMapper();

    private static final String SRC_DEFINITION = """
            {"schemaVersion":1,"archetype":"MGMT_LIST_DETAIL","stem":"userMgmt","role":"admin",
             "slots":{"listArea":[{"instanceId":"table_1","moduleTypeCode":"TABLE_VIEW",
             "props":{"columns":[{"name":"userId","displayName":"ID"}],"selectMode":"checkbox"}}]}}
            """;

    @BeforeEach
    void setUp() {
        mapper = mock(ForgeScreenMapper.class);
        ForgeProjectMapper projectMapper = mock(ForgeProjectMapper.class);
        validator = mock(DefinitionValidator.class);
        service = new ForgeScreenService(mapper, projectMapper, validator);

        when(mapper.selectById(10L)).thenReturn(source());
        // insert 시 생성 키(99) 채움 + 이후 단건 재조회는 저장본 그대로 반환.
        when(mapper.insert(any(ForgeScreen.class))).thenAnswer(inv -> {
            ForgeScreen s = inv.getArgument(0);
            s.setScreenId(99L);
            when(mapper.selectById(99L)).thenReturn(s);
            return 1;
        });
    }

    private ForgeScreen source() {
        ForgeScreen s = new ForgeScreen();
        s.setScreenId(10L);
        s.setProjectId(1L);
        s.setScreenName("사용자 관리");
        s.setStem("userMgmt");
        s.setArchetypeCode("MGMT_LIST_DETAIL");
        s.setRoleCode("admin");
        s.setStatusCode("READY");
        s.setDefinitionJson(SRC_DEFINITION);
        return s;
    }

    @Test
    void 복제는_slots_보존_stem_교체_DRAFT_로_새_화면을_만든다() throws Exception {
        ForgeScreen created = service.duplicate(10L,
                new ForgeScreenDuplicateRequest("사용자 관리 복사본", "userMgmtCopy"));

        assertEquals(99L, created.getScreenId());
        assertEquals(1L, created.getProjectId(), "원본 프로젝트 유지");
        assertEquals("사용자 관리 복사본", created.getScreenName());
        assertEquals("userMgmtCopy", created.getStem());
        assertEquals("MGMT_LIST_DETAIL", created.getArchetypeCode(), "아키타입 유지");
        assertEquals("admin", created.getRoleCode(), "role 유지");
        assertEquals("DRAFT", created.getStatusCode(), "복제본은 DRAFT 시작");

        // DEFINITION: stem 만 교체, slots/archetype/role/schemaVersion 은 원본 보존.
        JsonNode def = json.readTree(created.getDefinitionJson());
        assertEquals("userMgmtCopy", def.get("stem").asText());
        assertEquals("MGMT_LIST_DETAIL", def.get("archetype").asText());
        assertEquals("admin", def.get("role").asText());
        assertEquals(1, def.get("schemaVersion").asInt());
        assertEquals(json.readTree(SRC_DEFINITION).get("slots"), def.get("slots"),
                "slots(모듈 배치·props) 무가공 보존");

        // 교체 결과 전체가 구조 재검증을 경유했는지(쓰기 경계 재통과).
        ArgumentCaptor<String> validated = ArgumentCaptor.forClass(String.class);
        verify(validator).validate(validated.capture());
        assertEquals(created.getDefinitionJson(), validated.getValue());
    }

    @Test
    void 원본_DEFINITION_이_객체가_아니면_400_계열_검증예외() {
        ForgeScreen broken = source();
        broken.setDefinitionJson("[1,2,3]");
        when(mapper.selectById(10L)).thenReturn(broken);

        assertThrows(DefinitionValidationException.class, () ->
                service.duplicate(10L, new ForgeScreenDuplicateRequest("복사본", "userMgmtCopy")));
        verify(mapper, org.mockito.Mockito.never()).insert(any());
    }

    @Test
    void 미존재_원본은_404() {
        when(mapper.selectById(anyLong())).thenReturn(null);
        assertThrows(com.jworks.forge.common.web.NotFoundException.class, () ->
                service.duplicate(404L, new ForgeScreenDuplicateRequest("복사본", "x")));
    }
}
