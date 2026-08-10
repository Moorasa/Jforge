package com.jworks.forge.gen.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 🔒 원자적 파일쓰기 단위 테스트 (P4-4, 계약 §5.1/§5.2).
 * 원자성(임시→rename)·기존파일 {@code .bak} 백업·심볼릭링크 하드거부·UTF-8을 검증한다.
 */
class AtomicFileWriterTest {

    private final AtomicFileWriter writer = new AtomicFileWriter();

    @Test
    void 새_파일을_UTF8로_쓴다(@TempDir Path dir) throws IOException {
        Path root = dir.toRealPath();
        Path target = dir.resolve("a").resolve("fooList.jsp");
        writer.write(target, root, "안녕 <world>");

        assertTrue(Files.exists(target), "파일 생성");
        assertEquals("안녕 <world>", Files.readString(target, StandardCharsets.UTF_8));
        // 임시파일이 남지 않았는지(원자적 rename 후 정리).
        assertNoTmpLeftover(target);
    }

    @Test
    void 기존_파일_존재시_bak_타임스탬프_백업_후_덮어쓴다(@TempDir Path dir) throws IOException {
        Path root = dir.toRealPath();
        Path target = dir.resolve("fooList.js");
        Files.writeString(target, "old-content", StandardCharsets.UTF_8);

        writer.write(target, root, "new-content");

        assertEquals("new-content", Files.readString(target), "덮어쓰기 반영");
        // {원본파일명}.bak-* 백업이 같은 디렉터리에 생겼고 원본 내용을 보존.
        List<Path> baks;
        try (var s = Files.list(dir)) {
            baks = s.filter(p -> p.getFileName().toString().startsWith("fooList.js.bak-")).toList();
        }
        assertEquals(1, baks.size(), "백업 1건");
        assertEquals("old-content", Files.readString(baks.get(0)), "백업은 원본 내용 보존");
    }

    @Test
    void 백업_파일명은_원본_절대경로_파생만_사용한다(@TempDir Path dir) throws IOException {
        Path root = dir.toRealPath();
        Path target = dir.resolve("userMgmtList.css");
        Files.writeString(target, "x", StandardCharsets.UTF_8);
        writer.write(target, root, "y");

        try (var s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().contains(".bak-")).forEach(p -> {
                String name = p.getFileName().toString();
                // 원본파일명 + ".bak-" + 타임스탬프(숫자, 선택적 -nanoTime)만 — 자유문자열 0.
                assertTrue(name.matches("userMgmtList\\.css\\.bak-\\d+(-\\d+)?"),
                        "백업 파일명이 정적 파생 규칙을 벗어남: " + name);
            });
        }
    }

    @Test
    void 대상이_심볼릭링크면_하드실패하고_링크_대상은_수정되지_않는다(@TempDir Path dir) throws IOException {
        Path root = dir.toRealPath();
        Path realFile = dir.resolve("real.jsp");
        Files.writeString(realFile, "REAL", StandardCharsets.UTF_8);
        Path link = dir.resolve("link.jsp");
        try {
            Files.createSymbolicLink(link, realFile);
        } catch (IOException | UnsupportedOperationException e) {
            // Windows 비관리자 등 심볼릭링크 생성 불가 환경 — 테스트 스킵.
            assumeTrue(false, "심볼릭링크 생성 불가 환경 — 스킵");
            return;
        }

        assertThrows(AtomicWriteException.class, () -> writer.write(link, root, "EVIL"),
                "심볼릭링크 대상 쓰기는 하드 실패해야 한다");
        // 링크가 가리키던 실제 파일이 오염되지 않았는지.
        assertEquals("REAL", Files.readString(realFile), "링크 대상 파일 미변경");
    }

    @Test
    void content_null이면_하드실패(@TempDir Path dir) throws IOException {
        Path root = dir.toRealPath();
        Path target = dir.resolve("x.jsp");
        assertThrows(AtomicWriteException.class, () -> writer.write(target, root, null));
        assertFalse(Files.exists(target), "실패 시 파일 생성 안 됨");
    }

    /**
     * 🔒 쓰기 직전 canonicalRoot 하위 재확인(계약 §5.1). 심볼릭링크 없이도, 조작된(대상과 무관한)
     * canonicalRoot를 넘기면 부모 실경로가 그 루트 하위가 아니므로 하드 실패하고 파일을 쓰지 않는다.
     * 정상 루트를 넘기면 통과한다(재확인 로직 자체의 단위검증).
     */
    @Test
    void 부모_실경로가_canonicalRoot_밖이면_쓰기직전_재확인이_하드실패한다(
            @TempDir Path dir, @TempDir Path otherRoot) throws IOException {
        Path root = dir.toRealPath();
        Path bogusRoot = otherRoot.toRealPath(); // dir과 무관한 별개 루트.
        Path target = dir.resolve("sub").resolve("fooList.jsp");

        // 조작된 루트: 부모(dir/sub)는 bogusRoot 하위가 아니므로 재확인 실패.
        AtomicWriteException ex = assertThrows(AtomicWriteException.class,
                () -> writer.write(target, bogusRoot, "X"),
                "부모 실경로가 canonicalRoot 밖이면 하드 실패");
        assertTrue(ex.getMessage().contains("재확인 실패"), "재확인 실패 사유: " + ex.getMessage());
        assertFalse(Files.exists(target), "재확인 실패 시 파일 생성 안 됨");

        // 정상 루트면 통과(회귀 0).
        writer.write(target, root, "X");
        assertTrue(Files.exists(target), "정상 루트는 통과");
    }

    @Test
    void canonicalRoot_null이면_하드실패(@TempDir Path dir) {
        Path target = dir.resolve("x.jsp");
        assertThrows(AtomicWriteException.class, () -> writer.write(target, null, "x"));
        assertFalse(Files.exists(target), "실패 시 파일 생성 안 됨");
    }

    private void assertNoTmpLeftover(Path target) throws IOException {
        Path parent = target.getParent();
        try (var s = Files.list(parent)) {
            boolean anyTmp = s.anyMatch(p -> p.getFileName().toString().contains(".tmp-"));
            assertFalse(anyTmp, "임시파일이 남았다");
        }
        assertFalse(Files.isSymbolicLink(target));
        assertTrue(Files.exists(target, LinkOption.NOFOLLOW_LINKS));
    }
}
