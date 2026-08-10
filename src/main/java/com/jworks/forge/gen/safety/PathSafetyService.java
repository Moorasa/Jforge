package com.jworks.forge.gen.safety;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * J-FORGE 생성 엔진의 **파일쓰기 경로 안전 계층** (P1-1).
 *
 * <p>빌더는 "타겟 프로젝트 폴더에 직접 파일을 쓰는" 도구라 경로가 핵심 공격면이다.
 * 모든 쓰기 경로는 반드시 이 서비스를 통과해 {@link #resolveSafeWritePath}로 정규화·검증된
 * 절대경로를 얻은 뒤에만 사용해야 한다.
 *
 * <p>차단 대상: 상위 탈출(../), 절대경로, 드라이브 문자(:)/ADS, UNC(\\host), 루트 밖으로
 * 향하는 심볼릭링크, 화이트리스트 밖 확장자.
 */
@Service
public class PathSafetyService {

    /** 생성 아티팩트에 허용되는 확장자(소문자, 점 제외). */
    public static final Set<String> DEFAULT_ALLOWED_EXTENSIONS =
            Set.of("jsp", "js", "css", "java", "xml");

    private final Set<String> allowedExtensions;

    public PathSafetyService() {
        this(DEFAULT_ALLOWED_EXTENSIONS);
    }

    public PathSafetyService(Set<String> allowedExtensions) {
        // 대소문자 무시를 위해 소문자로 보관
        this.allowedExtensions = Set.copyOf(
                allowedExtensions.stream().map(e -> e.toLowerCase(Locale.ROOT)).toList());
    }

    /**
     * {@code relativePath}를 {@code targetRoot} 하위의 안전한 절대경로로 해석한다.
     *
     * @param targetRoot   프로젝트의 TARGET_ROOT_PATH (반드시 존재하는 절대 디렉터리)
     * @param relativePath 루트 기준 상대 쓰기 경로 (예: {@code admin/foo/fooList.jsp})
     * @return 정규화된, 루트 하위임이 보장된 절대경로
     * @throws PathSafetyException 규칙 위반 시
     */
    public Path resolveSafeWritePath(Path targetRoot, String relativePath) {
        if (targetRoot == null) {
            throw new PathSafetyException("targetRoot가 null이다");
        }
        if (relativePath == null || relativePath.isBlank()) {
            throw new PathSafetyException("relativePath가 비어 있다");
        }

        // 1) 문자열 수준 선차단 — 절대경로/드라이브/UNC/루트앵커
        rejectRawUnsafe(relativePath);

        // 2) 루트를 실경로로 확정 (존재해야 함)
        final Path canonicalRoot = realPathOf(targetRoot, "targetRoot");
        if (!canonicalRoot.isAbsolute()) {
            throw new PathSafetyException("targetRoot는 절대경로여야 한다: " + targetRoot);
        }

        // 3) 상대경로를 붙여 정규화 → 루트 밖 탈출(../) 차단
        //    불법 문자(<>|)·NUL 등은 InvalidPathException을 안전 예외로 변환
        final Path candidate;
        try {
            candidate = canonicalRoot.resolve(relativePath).normalize();
        } catch (java.nio.file.InvalidPathException e) {
            throw new PathSafetyException("경로에 사용할 수 없는 문자가 있다: " + relativePath);
        }
        if (!candidate.startsWith(canonicalRoot)) {
            throw new PathSafetyException("루트를 벗어나는 경로다: " + relativePath);
        }

        // 4) 확장자 화이트리스트
        checkExtension(candidate);

        // 5) 심볼릭링크 탈출 차단 — 아직 없는 파일이라 존재하는 최근접 조상을 실경로로 검증
        final Path existingAncestor = nearestExistingAncestor(candidate);
        final Path realAncestor = realPathOf(existingAncestor, "경로 조상");
        if (!realAncestor.startsWith(canonicalRoot)) {
            throw new PathSafetyException("심볼릭링크로 루트를 벗어난다: " + relativePath);
        }

        return candidate;
    }

    private void rejectRawUnsafe(String relativePath) {
        // 드라이브 문자 / 대체 데이터 스트림(:) 차단
        if (relativePath.indexOf(':') >= 0) {
            throw new PathSafetyException("드라이브/콜론이 포함된 경로는 금지: " + relativePath);
        }
        // UNC(\\host, //host) 및 루트 앵커(/, \) 차단
        String s = relativePath.replace('\\', '/');
        if (s.startsWith("/")) {
            throw new PathSafetyException("절대/UNC/루트앵커 경로는 금지: " + relativePath);
        }
        // 명시적 상위 참조 요소 차단 (정규화 이전 방어)
        for (String seg : s.split("/")) {
            if (seg.equals("..")) {
                throw new PathSafetyException("상위 참조(..)는 금지: " + relativePath);
            }
        }
        // Path API가 절대로 판단하는 경우(플랫폼별) 차단
        try {
            if (Path.of(relativePath).isAbsolute()) {
                throw new PathSafetyException("절대경로는 금지: " + relativePath);
            }
        } catch (java.nio.file.InvalidPathException e) {
            throw new PathSafetyException("경로에 사용할 수 없는 문자가 있다: " + relativePath);
        }
    }

    private void checkExtension(Path candidate) {
        String name = candidate.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            throw new PathSafetyException("확장자가 없거나 숨김파일이다: " + name);
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!allowedExtensions.contains(ext)) {
            throw new PathSafetyException("허용되지 않은 확장자: ." + ext);
        }
    }

    private Path nearestExistingAncestor(Path candidate) {
        Path p = candidate;
        while (p != null && !java.nio.file.Files.exists(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            p = p.getParent();
        }
        // 루트가 존재하므로 최소한 루트에서 멈춘다
        return p;
    }

    private Path realPathOf(Path p, String what) {
        try {
            return p.toRealPath();
        } catch (IOException e) {
            throw new PathSafetyException(what + " 실경로 해석 실패: " + p);
        }
    }
}
