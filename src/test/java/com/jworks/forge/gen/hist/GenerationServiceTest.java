package com.jworks.forge.gen.hist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jworks.forge.common.web.NotFoundException;
import com.jworks.forge.gen.pipeline.GenFile;
import com.jworks.forge.gen.pipeline.GenResult;
import com.jworks.forge.gen.pipeline.ScreenGenerator;

/**
 * GenerationService(파사드) 단위 테스트 (P4-5). ScreenGenerator/GenHistMapper는 Mockito 스텁.
 *
 * <p>검증: (1)성공 시 GEN_HIST 1행 기록·응답 매핑, (2)FILE_LIST_JSON이 Jackson으로 안전 직렬화되어
 * 쓴 파일 목록을 담음, (3)PARTIAL/FAIL도 이력 기록(감사), (4)404(NotFoundException)는 전파되고
 * 이력 기록 안 함.
 */
class GenerationServiceTest {

    private ScreenGenerator screenGenerator;
    private GenHistMapper genHistMapper;
    private GenerationService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        screenGenerator = mock(ScreenGenerator.class);
        genHistMapper = mock(GenHistMapper.class);
        service = new GenerationService(screenGenerator, genHistMapper, objectMapper);
    }

    /** insert 시 DB가 PK/GEN_AT를 채우는 것을 흉내내는 스텁. */
    private void stubInsertFillsKeys(long genHistId, OffsetDateTime genAt) {
        when(genHistMapper.insert(any(GenHist.class))).thenAnswer(inv -> {
            GenHist h = inv.getArgument(0);
            h.setGenHistId(genHistId);
            h.setGenAt(genAt);
            return 1;
        });
    }

    @Test
    void 성공시_GEN_HIST_1행_기록하고_응답을_매핑한다() throws Exception {
        List<GenFile> files = List.of(
                GenFile.ok("shell", "WEB-INF/views/admin/userMgmt/userMgmt.jsp"),
                GenFile.ok("listTableViewJs", "static/js/admin/userMgmt/userMgmtListTableView.js"));
        when(screenGenerator.generate(10L)).thenReturn(new GenResult(GenResult.SUCCESS, files, null));
        OffsetDateTime now = OffsetDateTime.now();
        stubInsertFillsKeys(101L, now);

        GenerateResponse resp = service.generateAndRecord(10L);

        assertEquals(GenResult.SUCCESS, resp.resultCode());
        assertEquals(101L, resp.genHistId());
        assertEquals(now, resp.genAt());
        assertEquals(2, resp.files().size());

        // FILE_LIST_JSON: Jackson 직렬화(문자열 조립 0). 쓴 파일 경로/키/성공여부를 담음.
        ArgumentCaptor<GenHist> cap = ArgumentCaptor.forClass(GenHist.class);
        verify(genHistMapper).insert(cap.capture());
        GenHist recorded = cap.getValue();
        assertEquals(10L, recorded.getScreenId());
        assertEquals(GenResult.SUCCESS, recorded.getResultCode());

        JsonNode arr = objectMapper.readTree(recorded.getFileListJson());
        assertTrue(arr.isArray());
        assertEquals(2, arr.size());
        assertEquals("shell", arr.get(0).get("artifactKey").asText());
        assertEquals("WEB-INF/views/admin/userMgmt/userMgmt.jsp",
                arr.get(0).get("relativePath").asText());
        assertTrue(arr.get(0).get("success").asBoolean());
    }

    @Test
    void 부분실패도_이력에_기록되고_실패항목은_reason을_담는다() throws Exception {
        List<GenFile> files = List.of(
                GenFile.ok("shell", "a/b.jsp"),
                GenFile.fail("listCss", "a/b.css", "TemplateRenderException: boom"));
        when(screenGenerator.generate(10L)).thenReturn(new GenResult(GenResult.PARTIAL, files, null));
        stubInsertFillsKeys(102L, OffsetDateTime.now());

        GenerateResponse resp = service.generateAndRecord(10L);

        assertEquals(GenResult.PARTIAL, resp.resultCode());
        ArgumentCaptor<GenHist> cap = ArgumentCaptor.forClass(GenHist.class);
        verify(genHistMapper).insert(cap.capture());
        JsonNode arr = objectMapper.readTree(cap.getValue().getFileListJson());
        assertEquals(GenResult.PARTIAL, cap.getValue().getResultCode());
        assertFalse(arr.get(1).get("success").asBoolean());
        assertEquals("TemplateRenderException: boom", arr.get(1).get("reason").asText());
        // 성공 항목엔 reason 미포함.
        assertFalse(arr.get(0).has("reason"));
    }

    @Test
    void 전량실패_FAIL도_이력에_기록된다() {
        when(screenGenerator.generate(10L))
                .thenReturn(new GenResult(GenResult.FAIL, List.of(), "컨텍스트 구성 실패"));
        stubInsertFillsKeys(103L, OffsetDateTime.now());

        GenerateResponse resp = service.generateAndRecord(10L);

        assertEquals(GenResult.FAIL, resp.resultCode());
        assertEquals(103L, resp.genHistId());
        verify(genHistMapper).insert(any(GenHist.class));
    }

    @Test
    void 미존재_screenId는_404_전파되고_이력_기록하지_않는다() {
        when(screenGenerator.generate(99L)).thenThrow(new NotFoundException("screen not found"));

        assertThrows(NotFoundException.class, () -> service.generateAndRecord(99L));
        verifyNoInteractions(genHistMapper);
    }

    @Test
    void history는_매퍼_최신순_목록을_그대로_반환한다() {
        GenHist h = new GenHist();
        h.setGenHistId(1L);
        when(genHistMapper.selectByScreen(10L)).thenReturn(List.of(h));

        List<GenHist> out = service.history(10L);

        assertEquals(1, out.size());
        assertEquals(1L, out.get(0).getGenHistId());
        verify(genHistMapper).selectByScreen(10L);
    }
}
