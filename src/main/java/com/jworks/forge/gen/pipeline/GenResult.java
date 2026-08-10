package com.jworks.forge.gen.pipeline;

import java.util.List;

/**
 * 생성 파이프라인의 <b>화면 단위 결과</b> (P4-4, 계약 §5.3).
 *
 * <p>{@link ScreenGenerator#generate(Long)}가 반환한다. GEN_HIST 기록·트리거 API는 P4-5이며
 * 이 태스크는 결과 반환까지다. {@code resultCode}는 계약 §5.3 판정 기준을 따른다.
 *
 * <ul>
 *   <li>{@code SUCCESS} — 계획된 아티팩트 전량 성공.</li>
 *   <li>{@code PARTIAL} — 1건 이상 성공 + 1건 이상 실패.</li>
 *   <li>{@code FAIL} — 0건 성공(전량 실패), 또는 입력 로드/컨텍스트 구성 실패로 아무 파일도 못 씀.</li>
 * </ul>
 *
 * @param resultCode {@code SUCCESS}/{@code PARTIAL}/{@code FAIL}
 * @param files 파일 단위 결과(성공/실패 모두 포함, 계획 순서 보존)
 * @param failReason 전체 실패(FAIL) 사유 요약(정상/부분 성공 시 {@code null})
 */
public record GenResult(
        String resultCode,
        List<GenFile> files,
        String failReason) {

    public static final String SUCCESS = "SUCCESS";
    public static final String PARTIAL = "PARTIAL";
    public static final String FAIL = "FAIL";

    public GenResult {
        files = (files == null) ? List.of() : List.copyOf(files);
    }

    /** 파일 결과 목록으로부터 RESULT_CODE를 판정해 결과를 만든다(계약 §5.3). */
    static GenResult of(List<GenFile> files) {
        long ok = files.stream().filter(GenFile::success).count();
        String code;
        if (ok == files.size() && !files.isEmpty()) {
            code = SUCCESS;
        } else if (ok == 0) {
            code = FAIL;
        } else {
            code = PARTIAL;
        }
        return new GenResult(code, files, null);
    }

    /** 입력 로드/컨텍스트 구성 단계 실패 — 아무 파일도 쓰지 못한 전체 실패. */
    static GenResult fail(String failReason) {
        return new GenResult(FAIL, List.of(), failReason);
    }
}
