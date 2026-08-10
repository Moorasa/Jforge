package com.jworks.forge.gen.pipeline;

/**
 * 생성 파이프라인의 <b>파일 단위 결과</b> (P4-4, 계약 §5.3).
 *
 * <p>계획된 아티팩트 1건의 렌더→경로안전→원자적 쓰기 결과를 담는다. GEN_HIST의
 * {@code FILE_LIST_JSON} 직렬화(P4-5)와 로깅에 그대로 쓰인다. {@code relativePath}는
 * 계약 §1.2 규칙(정적 세그먼트 + role + stem)으로 조립된 값이며 자유문자열을 포함하지 않는다.
 *
 * @param artifactKey 아티팩트 식별 키(정적 상수, 예: {@code shell}, {@code listTableViewJs})
 * @param relativePath 타겟 루트 기준 상대 쓰기 경로(정적 세그먼트+role+stem, 자유문자열 0)
 * @param success 렌더+경로안전+쓰기 전량 성공 여부
 * @param reason 실패 사유(성공 시 {@code null}). 원문 페이로드가 아니라 예외 메시지 요약만 담는다.
 * @param contentHash P12(계약 §16): 쓴 내용의 SHA-256(16진). 다음 생성 때 디스크와 대조해
 *                    <b>외부 수정(드리프트)</b>을 판정하는 기준이다. 미기록이면 {@code null}.
 */
public record GenFile(
        String artifactKey,
        String relativePath,
        boolean success,
        String reason,
        String contentHash) {

    /** 성공 결과(해시 미기록 — 런타임 복사 등). */
    public static GenFile ok(String artifactKey, String relativePath) {
        return new GenFile(artifactKey, relativePath, true, null, null);
    }

    /** 성공 결과 + 내용 해시(P12 드리프트 기준). */
    public static GenFile ok(String artifactKey, String relativePath, String contentHash) {
        return new GenFile(artifactKey, relativePath, true, null, contentHash);
    }

    /** 실패 결과. {@code relativePath}는 조립까지 성공했다면 담고, 조립 전이면 {@code null}일 수 있다. */
    public static GenFile fail(String artifactKey, String relativePath, String reason) {
        return new GenFile(artifactKey, relativePath, false, reason, null);
    }
}
