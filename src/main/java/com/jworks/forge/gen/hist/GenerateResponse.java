package com.jworks.forge.gen.hist;

import java.time.OffsetDateTime;
import java.util.List;

import com.jworks.forge.gen.pipeline.GenFile;

/**
 * 생성 트리거 API 응답 DTO (P4-5). {@code POST /api/screens/{id}/generate}의 200 바디.
 *
 * @param resultCode {@code SUCCESS}/{@code PARTIAL}/{@code FAIL}
 * @param files 파일 단위 결과(성공/실패 모두, 계획 순서 보존)
 * @param failReason 전체 실패(FAIL) 사유 요약(정상/부분 성공 시 {@code null}). 예: 타겟 경로 미존재.
 *        UI가 "왜 실패했는지"를 사용자에게 표시하도록 응답에 포함한다(P6 진단성).
 * @param genHistId 기록된 GEN_HIST 행의 PK
 * @param genAt 기록 시각(DB DEFAULT now()로 채워진 값)
 */
public record GenerateResponse(
        String resultCode,
        List<GenFile> files,
        String failReason,
        Long genHistId,
        OffsetDateTime genAt) {
}
