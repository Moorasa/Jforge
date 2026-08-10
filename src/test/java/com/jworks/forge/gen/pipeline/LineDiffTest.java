package com.jworks.forge.gen.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** P12(계약 §16.3): 덮어쓰기 전에 "무엇이 어떻게 바뀌는지" 보여주는 diff. */
class LineDiffTest {

    @Test
    void 같은_내용은_identical이다() {
        LineDiff.Result result = LineDiff.diff("a\nb\nc\n", "a\nb\nc\n", 3);

        assertTrue(result.identical());
        assertFalse(result.tooLarge());
        assertTrue(result.hunks().isEmpty());
    }

    @Test
    void 한_줄_변경은_추가와_삭제로_표시된다() {
        LineDiff.Result result = LineDiff.diff("a\nb\nc\n", "a\nB\nc\n", 1);

        assertFalse(result.identical());
        assertEquals(1, result.hunks().size());
        List<String> lines = result.hunks().get(0).lines();
        assertTrue(lines.contains("-b"), "삭제된 줄");
        assertTrue(lines.contains("+B"), "추가된 줄");
        assertTrue(lines.contains(" a"), "context 로 앞줄 포함");
    }

    @Test
    void 추가만_있는_경우도_잡는다() {
        LineDiff.Result result = LineDiff.diff("a\nc\n", "a\nb\nc\n", 1);

        assertFalse(result.identical());
        List<String> lines = result.hunks().get(0).lines();
        assertTrue(lines.contains("+b"));
        assertFalse(lines.stream().anyMatch(l -> l.startsWith("-")), "삭제는 없어야 한다");
    }

    @Test
    void 신규_파일은_전량_추가로_보인다() {
        LineDiff.Result result = LineDiff.diff("", "a\nb\n", 3);

        assertFalse(result.identical());
        List<String> lines = result.hunks().get(0).lines();
        assertTrue(lines.contains("+a"));
        assertTrue(lines.contains("+b"));
    }

    /** 성능 가드: 앞뒤 공통부를 제거한 구간이 상한을 넘으면 계산을 포기한다(메모리 폭주 방지). */
    @Test
    void 차이가_너무_크면_tooLarge로_수렴한다() {
        StringBuilder oldText = new StringBuilder();
        StringBuilder newText = new StringBuilder();
        for (int i = 0; i < LineDiff.MAX_DIFF_LINES + 10; i++) {
            oldText.append("old-").append(i).append('\n');
            newText.append("new-").append(i).append('\n');
        }

        LineDiff.Result result = LineDiff.diff(oldText.toString(), newText.toString(), 3);

        assertTrue(result.tooLarge());
        assertFalse(result.identical());
        assertTrue(result.hunks().isEmpty());
    }

    /** 큰 파일이라도 앞뒤가 같으면(=실제 변경이 작으면) 정상 계산된다. */
    @Test
    void 큰_파일이라도_변경이_작으면_계산된다() {
        StringBuilder base = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            base.append("line-").append(i).append('\n');
        }
        String oldText = base.toString();
        String newText = oldText.replace("line-2500\n", "line-2500-CHANGED\n");

        LineDiff.Result result = LineDiff.diff(oldText, newText, 2);

        assertFalse(result.tooLarge(), "공통 앞뒤를 잘라내면 비교 구간은 작다");
        assertTrue(result.hunks().get(0).lines().contains("+line-2500-CHANGED"));
    }
}
