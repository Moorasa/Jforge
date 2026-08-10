package com.jworks.forge.gen.pipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * 줄 단위 diff (P12, 계약 §16.3). <b>순수 함수 · 외부 의존성 0.</b>
 *
 * <p>"덮어쓰기 n건"이라는 숫자만으로는 덮어써도 되는지 판단할 수 없다. 이 클래스는 디스크의 현재
 * 내용과 생성 예정 내용의 차이를 통합 diff 형태(hunk)로 만들어 사용자가 <b>보고</b> 결정하게 한다.
 *
 * <p><b>성능 가드</b>: 공통 앞/뒤를 먼저 잘라내고 남은 구간에만 LCS를 적용한다. 생성물은 대개
 * 거의 같으므로 실제 비교 구간은 작다. 그래도 큰 경우({@link #MAX_DIFF_LINES} 초과)에는 계산을
 * 포기하고 {@code tooLarge}로 알린다(무한정 메모리를 쓰지 않는다).
 */
public final class LineDiff {

    /** LCS를 적용할 최대 구간(앞뒤 공통부 제거 후 기준). */
    static final int MAX_DIFF_LINES = 1500;

    private LineDiff() {
    }

    /**
     * diff 결과.
     *
     * @param identical 완전히 동일한가
     * @param tooLarge  차이가 너무 커 계산을 포기했는가(hunks는 비어 있음)
     * @param hunks     변경 구간 목록
     */
    public record Result(boolean identical, boolean tooLarge, List<Hunk> hunks) {
    }

    /**
     * 변경 구간 1개.
     *
     * @param oldStart 1-기반 시작 줄(현재 내용)
     * @param newStart 1-기반 시작 줄(생성 예정 내용)
     * @param lines    {@code " "}(동일) / {@code "-"}(삭제) / {@code "+"}(추가) 접두가 붙은 줄
     */
    public record Hunk(int oldStart, int oldCount, int newStart, int newCount, List<String> lines) {
    }

    /** 두 텍스트의 줄 diff. {@code context}는 변경 주변에 함께 보여줄 동일 줄 수. */
    public static Result diff(String oldText, String newText, int context) {
        String[] oldLines = split(oldText);
        String[] newLines = split(newText);

        if (java.util.Arrays.equals(oldLines, newLines)) {
            return new Result(true, false, List.of());
        }

        // 1) 공통 앞부분/뒷부분을 잘라 비교 구간을 좁힌다.
        int prefix = 0;
        int max = Math.min(oldLines.length, newLines.length);
        while (prefix < max && oldLines[prefix].equals(newLines[prefix])) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < max - prefix
                && oldLines[oldLines.length - 1 - suffix].equals(newLines[newLines.length - 1 - suffix])) {
            suffix++;
        }

        int oldMid = oldLines.length - prefix - suffix;
        int newMid = newLines.length - prefix - suffix;
        if (oldMid > MAX_DIFF_LINES || newMid > MAX_DIFF_LINES) {
            return new Result(false, true, List.of());
        }

        // 2) 좁힌 구간만 LCS.
        List<String> ops = lcsOps(oldLines, newLines, prefix, oldMid, newMid);

        // 3) 앞/뒤 공통부에서 context 만큼만 덧붙여 hunk 하나로 묶는다.
        List<String> lines = new ArrayList<>(ops.size() + context * 2);
        int leadStart = Math.max(0, prefix - context);
        for (int i = leadStart; i < prefix; i++) {
            lines.add(" " + oldLines[i]);
        }
        lines.addAll(ops);
        int tailEnd = Math.min(suffix, context);
        for (int i = 0; i < tailEnd; i++) {
            lines.add(" " + oldLines[oldLines.length - suffix + i]);
        }

        int oldCount = (prefix - leadStart) + oldMid + tailEnd;
        int newCount = (prefix - leadStart) + newMid + tailEnd;
        Hunk hunk = new Hunk(leadStart + 1, oldCount, leadStart + 1, newCount, lines);
        return new Result(false, false, List.of(hunk));
    }

    /** 좁힌 구간에 대한 LCS 기반 연산 목록({@code -}/{@code +}/{@code " "}). */
    private static List<String> lcsOps(String[] oldLines, String[] newLines,
                                       int offset, int oldMid, int newMid) {
        int[][] dp = new int[oldMid + 1][newMid + 1];
        for (int i = oldMid - 1; i >= 0; i--) {
            for (int j = newMid - 1; j >= 0; j--) {
                dp[i][j] = oldLines[offset + i].equals(newLines[offset + j])
                        ? dp[i + 1][j + 1] + 1
                        : Math.max(dp[i + 1][j], dp[i][j + 1]);
            }
        }
        List<String> ops = new ArrayList<>(oldMid + newMid);
        int i = 0;
        int j = 0;
        while (i < oldMid && j < newMid) {
            if (oldLines[offset + i].equals(newLines[offset + j])) {
                ops.add(" " + oldLines[offset + i]);
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                ops.add("-" + oldLines[offset + i]);
                i++;
            } else {
                ops.add("+" + newLines[offset + j]);
                j++;
            }
        }
        while (i < oldMid) {
            ops.add("-" + oldLines[offset + i++]);
        }
        while (j < newMid) {
            ops.add("+" + newLines[offset + j++]);
        }
        return ops;
    }

    private static String[] split(String text) {
        return (text == null ? "" : text).split("\n", -1);
    }
}
