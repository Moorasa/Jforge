package com.jworks.forge.gen.pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 🔒 원자적 파일쓰기 (P4-4, 계약 §5.1/§5.2). <b>P4 보안의 심장.</b>
 *
 * <p>대상 경로는 <b>반드시</b> {@code PathSafetyService.resolveSafeWritePath}가 반환한
 * 안전 절대경로여야 한다(이 클래스는 경로안전을 재해석하지 않고 전제한다 — 계약 §4.1).
 * 이 클래스는 그 절대경로에 대해 다음을 수행한다:
 *
 * <ol>
 *   <li><b>심볼릭링크 하드 거부(계약 §5.1)</b>: 대상이 이미 존재하고 심볼릭링크이면 실패.
 *       {@code resolveSafeWritePath}의 심볼릭링크 검증은 "최근접 <em>존재</em> 조상" 기준이라
 *       최종 파일 자체가 심볼릭링크인 경우를 못 잡으므로 여기서 {@code NOFOLLOW_LINKS}로 추가 방어.</li>
 *   <li><b>부모 디렉터리 생성</b>({@code Files.createDirectories}) — 대상이 이미 safeAbsPath라
 *       루트 하위가 보장된다. 생성 후 부모가 심볼릭링크가 되었는지 재확인.</li>
 *   <li><b>백업(계약 §5.2)</b>: 대상이 정규 파일로 존재하면 {@code {원본절대경로}.bak-{yyyyMMddHHmmss}}로
 *       먼저 복사 백업(원본 절대경로 + 정적 타임스탬프 접미사만 — 자유문자열 0).</li>
 *   <li><b>임시파일→rename</b>: 같은 디렉터리에 {@code {name}.tmp-{nonce}}로 UTF-8 쓰기 후
 *       {@code Files.move(ATOMIC_MOVE)}로 교체. 미지원 파일시스템이면 {@code REPLACE_EXISTING} 폴백.</li>
 * </ol>
 *
 * <p>쓰기 실패 시 임시파일을 정리해 최종 경로에 부분/깨진 파일을 남기지 않는다.
 *
 * <p>이 클래스는 <b>reviewer 🔒 보안검수 대상</b>이다.
 */
@Component
public class AtomicFileWriter {

    private static final Logger log = LoggerFactory.getLogger(AtomicFileWriter.class);

    /** 백업 접미사의 타임스탬프 포맷(정적). 자유문자열 없이 원본 절대경로 + 이 접미사만 붙인다. */
    private static final DateTimeFormatter BAK_TS =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * {@code safeAbsPath}에 {@code content}를 UTF-8로 원자적으로 쓴다.
     *
     * @param safeAbsPath   {@code resolveSafeWritePath}가 반환한 안전 절대경로(전제 — 재검증하지 않음)
     * @param canonicalRoot 검증 루트({@code targetRoot.toRealPath()}). 쓰기 직전 부모 실경로가 이
     *                      하위인지 재확인해 순수 TOCTOU(중간 디렉터리가 루트 밖 링크로 바뀜)를 차단한다(계약 §5.1).
     * @param content       쓸 내용(렌더 결과)
     * @throws AtomicWriteException 심볼릭링크 대상 거부·루트 이탈 재확인 실패·임시파일 쓰기/이동·백업 실패 시
     */
    public void write(Path safeAbsPath, Path canonicalRoot, String content) {
        if (safeAbsPath == null) {
            throw new AtomicWriteException("safeAbsPath가 null이다");
        }
        if (!safeAbsPath.isAbsolute()) {
            // 계약상 항상 절대경로여야 한다(방어적 하드 실패).
            throw new AtomicWriteException("safeAbsPath가 절대경로가 아니다: " + safeAbsPath);
        }
        if (canonicalRoot == null || !canonicalRoot.isAbsolute()) {
            throw new AtomicWriteException("canonicalRoot가 null이거나 절대경로가 아니다: " + canonicalRoot);
        }
        if (content == null) {
            throw new AtomicWriteException("content가 null이다: " + safeAbsPath.getFileName());
        }

        // 1) 🔒 최종 대상이 이미 존재하고 심볼릭링크면 하드 실패(백업/덮어쓰기 거부, 계약 §5.1).
        rejectIfSymlink(safeAbsPath, "대상 파일");

        // 2) 부모 디렉터리 확보(대상이 safeAbsPath라 루트 하위 보장). 생성 후 부모 심볼릭링크 재확인.
        Path parent = safeAbsPath.getParent();
        if (parent == null) {
            throw new AtomicWriteException("대상의 부모 디렉터리를 알 수 없다: " + safeAbsPath);
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new AtomicWriteException("부모 디렉터리 생성 실패: " + parent, e);
        }
        // 생성 결과가 심볼릭링크로 유도되지 않았는지 재확인(TOCTOU 축소).
        rejectIfSymlink(parent, "부모 디렉터리");

        // 🔒 쓰기 직전 canonicalRoot 하위 재확인(계약 §5.1 마지막 문장). createDirectories 이후 부모의
        //    실경로(심볼릭링크 해석)가 여전히 루트 하위인지 확인해, resolve 이후 중간 디렉터리가 루트 밖
        //    링크로 바뀐 순수 TOCTOU를 백업/쓰기 전에 하드 차단한다.
        final Path realParent;
        try {
            realParent = parent.toRealPath();
        } catch (IOException e) {
            throw new AtomicWriteException("부모 디렉터리 실경로 해석 실패: " + parent, e);
        }
        if (!realParent.startsWith(canonicalRoot)) {
            throw new AtomicWriteException(
                    "쓰기 직전 재확인 실패 — 대상 부모가 루트를 벗어난다: " + realParent
                            + " (root=" + canonicalRoot + ")");
        }

        // 3) 🔒 기존 정규 파일이면 먼저 백업(계약 §5.2). 심볼릭링크는 위에서 이미 거부됨.
        boolean targetExists = Files.exists(safeAbsPath, LinkOption.NOFOLLOW_LINKS);
        if (targetExists) {
            backup(safeAbsPath);
        }

        // 4) 임시파일(같은 디렉터리)에 UTF-8 쓰기 → 원자적 rename.
        Path tmp = parent.resolve(safeAbsPath.getFileName().toString()
                + ".tmp-" + UUID.randomUUID());
        try {
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            moveIntoPlace(tmp, safeAbsPath);
        } catch (IOException e) {
            cleanupQuietly(tmp);
            throw new AtomicWriteException("파일 쓰기 실패: " + safeAbsPath.getFileName(), e);
        } catch (RuntimeException e) {
            cleanupQuietly(tmp);
            throw e;
        }
    }

    /** 대상이 존재하고 심볼릭링크면 하드 실패(NOFOLLOW). 미존재/정규 파일·디렉터리는 통과. */
    private void rejectIfSymlink(Path path, String what) {
        // isSymbolicLink는 존재하지 않으면 false를 반환하므로 별도 존재 확인 불필요.
        if (Files.isSymbolicLink(path)) {
            throw new AtomicWriteException(
                    what + "이(가) 심볼릭링크라 쓰기를 거부한다: " + path);
        }
        // 이중 확인: NOFOLLOW 속성 조회로도 링크 여부를 재확인(존재할 때만).
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            try {
                BasicFileAttributes attrs = Files.readAttributes(
                        path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attrs.isSymbolicLink()) {
                    throw new AtomicWriteException(
                            what + "이(가) 심볼릭링크라 쓰기를 거부한다: " + path);
                }
            } catch (IOException e) {
                throw new AtomicWriteException(what + " 속성 조회 실패: " + path, e);
            }
        }
    }

    /** 기존 정규 파일을 {@code {원본절대경로}.bak-{timestamp}}로 복사 백업(계약 §5.2). */
    private void backup(Path target) {
        String ts = LocalDateTime.now().format(BAK_TS);
        // 원본 절대경로 + 정적 접미사만 — 파일명 조립에 자유문자열 0.
        Path bak = target.resolveSibling(target.getFileName().toString() + ".bak-" + ts);
        // 동일 초에 재생성되면 백업 파일명 충돌 → 나노초 접미사로 유일화(여전히 정적 파생).
        if (Files.exists(bak, LinkOption.NOFOLLOW_LINKS)) {
            bak = target.resolveSibling(
                    target.getFileName().toString() + ".bak-" + ts + "-" + System.nanoTime());
        }
        try {
            // COPY_ATTRIBUTES 없이 내용만 복사. NOFOLLOW로 링크 추종 방지(원본은 정규 파일임이 보장됨).
            Files.copy(target, bak,
                    StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
            log.info("[AtomicWrite] 기존 파일 백업: {} -> {}", target.getFileName(), bak.getFileName());
        } catch (IOException e) {
            throw new AtomicWriteException("기존 파일 백업 실패: " + target.getFileName(), e);
        }
    }

    /** 임시파일을 최종 위치로 원자적 이동. ATOMIC_MOVE 미지원 시 REPLACE_EXISTING 폴백. */
    private void moveIntoPlace(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            log.debug("[AtomicWrite] ATOMIC_MOVE 미지원 — REPLACE_EXISTING 폴백: {}", target.getFileName());
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void cleanupQuietly(Path tmp) {
        try {
            Files.deleteIfExists(tmp);
        } catch (IOException e) {
            // 정리 실패는 로그만(최종 경로엔 부분 파일 없음 — 임시파일만 남을 수 있음).
            log.warn("[AtomicWrite] 임시파일 정리 실패(무해): {}", tmp, e);
        }
    }
}
