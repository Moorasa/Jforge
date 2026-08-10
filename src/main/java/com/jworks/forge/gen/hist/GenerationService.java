package com.jworks.forge.gen.hist;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jworks.forge.gen.pipeline.GenFile;
import com.jworks.forge.gen.pipeline.GenResult;
import com.jworks.forge.gen.pipeline.ScreenGenerator;

/**
 * 생성 파사드 (P4-5). {@link ScreenGenerator}(P4 보안의 심장)를 <b>감싸</b> 생성 결과를
 * {@code TB_FRG_GEN_HIST}에 기록하고 API 응답 DTO로 변환한다. ScreenGenerator의 시그니처/책임은
 * 건드리지 않는다(파사드 오케스트레이션만).
 *
 * <p>흐름:
 * <ol>
 *   <li>{@link ScreenGenerator#generate(Long)} 호출(화면/프로젝트 미존재 시
 *       {@link com.jworks.forge.common.web.NotFoundException}(404) 그대로 전파).</li>
 *   <li>{@link GenResult#files()}를 Jackson {@link ObjectMapper}로 안전 직렬화 → FILE_LIST_JSON
 *       문자열(문자열 {@code +} 조립 0).</li>
 *   <li>{@link GenHistMapper#insert}로 이력 1행 기록. <b>성공/부분/실패 모두 기록</b>(FAIL도 감사 대상).</li>
 * </ol>
 *
 * <p>{@code generate}는 파일쓰기 부분실패를 예외로 던지지 않고 {@link GenResult}로 결과를 반환하는
 * 현 구조를 활용한다 → 파일쓰기가 부분/전량 실패해도 GEN_HIST INSERT는 트랜잭션 안에서 커밋되어
 * 이력이 남는다. 404(미존재)만 트랜잭션 롤백(기록할 대상 자체가 없음).
 */
@Service
public class GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationService.class);

    private final ScreenGenerator screenGenerator;
    private final GenHistMapper genHistMapper;
    private final ObjectMapper objectMapper;

    public GenerationService(
            ScreenGenerator screenGenerator,
            GenHistMapper genHistMapper,
            ObjectMapper objectMapper) {
        this.screenGenerator = screenGenerator;
        this.genHistMapper = genHistMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 화면을 생성(파일쓰기)하고 결과를 GEN_HIST에 기록한다.
     *
     * @param screenId 대상 화면 ID
     * @return 결과코드 + 파일 목록 + 기록된 GEN_HIST_ID/GEN_AT
     * @throws com.jworks.forge.common.web.NotFoundException 화면/프로젝트 미존재 시(404)
     */
    @Transactional
    public GenerateResponse generateAndRecord(Long screenId) {
        // 1) 생성(파일쓰기). 404는 그대로 전파(기록할 대상 없음 → 트랜잭션 롤백).
        GenResult result = screenGenerator.generate(screenId);

        // 2) 파일 목록을 Jackson으로 안전 직렬화(문자열 조립 0).
        String fileListJson = serializeFiles(result.files());

        // 3) 성공/부분/실패 모두 이력 기록(FAIL도 감사 대상).
        GenHist hist = new GenHist();
        hist.setScreenId(screenId);
        hist.setFileListJson(fileListJson);
        hist.setResultCode(result.resultCode());
        try {
            genHistMapper.insert(hist); // useGeneratedKeys → genHistId/genAt 채움
        } catch (RuntimeException e) {
            // 파일쓰기는 비트랜잭션 부수효과라 롤백되지 않는다 → INSERT 실패 시 "파일은 이미 디스크에
            // 있으나 GEN_HIST 행은 롤백"되는 불일치가 생긴다. DB 행은 못 남기더라도 감사 흔적을
            // 내구성 있는 ERROR 로그로 남긴 뒤 재던져(트랜잭션 롤백 유지) 호출자에 실패를 알린다.
            log.error("[Gen] 이력 기록 실패 — 파일은 이미 생성됨(디스크 잔존), GEN_HIST 롤백. "
                    + "screenId={} result={} files={}",
                    screenId, result.resultCode(), fileListJson, e);
            throw e;
        }

        log.info("[Gen] 이력 기록 — screenId={} result={} genHistId={}",
                screenId, result.resultCode(), hist.getGenHistId());

        return new GenerateResponse(
                result.resultCode(),
                result.files(),
                result.failReason(),
                hist.getGenHistId(),
                hist.getGenAt());
    }

    /** 화면별 생성 이력 메타 목록(최신순). */
    @Transactional(readOnly = true)
    public List<GenHist> history(Long screenId) {
        return genHistMapper.selectByScreen(screenId);
    }

    /**
     * {@link GenFile} 목록을 FILE_LIST_JSON 배열 문자열로 안전 직렬화한다.
     * 각 항목은 {@code {artifactKey, relativePath, success}} (+ 실패 시 {@code reason} 요약).
     * Jackson {@link ObjectMapper}만 사용하며 문자열 {@code +} 조립은 하지 않는다.
     */
    private String serializeFiles(List<GenFile> files) {
        ArrayNode arr = objectMapper.createArrayNode();
        if (files != null) {
            for (GenFile f : files) {
                ObjectNode node = arr.addObject();
                node.put("artifactKey", f.artifactKey());
                node.put("relativePath", f.relativePath());
                node.put("success", f.success());
                if (!f.success() && f.reason() != null) {
                    node.put("reason", f.reason());
                }
                // P12(계약 §16): 내용 해시. 다음 생성의 드리프트 판정(GenPlanner) 기준값이다.
                if (f.contentHash() != null) {
                    node.put("hash", f.contentHash());
                }
            }
        }
        try {
            return objectMapper.writeValueAsString(arr);
        } catch (JsonProcessingException e) {
            // ArrayNode 직렬화는 사실상 실패하지 않지만, 방어적으로 유효 JSON 토큰을 보장한다.
            log.warn("[Gen] FILE_LIST_JSON 직렬화 실패 — 빈 배열로 대체 : {}", e.getMessage());
            return "[]";
        }
    }
}
