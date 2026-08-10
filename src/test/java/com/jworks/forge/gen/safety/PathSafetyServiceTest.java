package com.jworks.forge.gen.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * P1-1 수용기준: 악용 입력 셋(상위탈출/절대/UNC/드라이브/심링크/비허용확장자)이
 * 전부 쓰기 거부되고 루트 밖에 파일이 생기지 않는다.
 */
class PathSafetyServiceTest {

    private final PathSafetyService svc = new PathSafetyService();

    // ---------- 정상 경로 ----------

    @Test
    void 정상_상대경로는_루트하위_절대경로로_해석(@TempDir Path root) throws IOException {
        Path resolved = svc.resolveSafeWritePath(root, "admin/user/userList.jsp");
        assertTrue(resolved.startsWith(root.toRealPath()));
        assertEquals("userList.jsp", resolved.getFileName().toString());
    }

    @Test
    void 허용확장자_5종_모두_통과(@TempDir Path root) {
        for (String ext : new String[] {"jsp", "js", "css", "java", "xml"}) {
            svc.resolveSafeWritePath(root, "gen/Sample." + ext);
        }
    }

    // ---------- 상위 탈출 ----------

    @Test
    void 상위탈출_단순(@TempDir Path root) {
        assertThrows(PathSafetyException.class,
                () -> svc.resolveSafeWritePath(root, "../evil.js"));
    }

    @Test
    void 상위탈출_중간우회(@TempDir Path root) {
        assertThrows(PathSafetyException.class,
                () -> svc.resolveSafeWritePath(root, "a/b/../../../evil.js"));
    }

    // ---------- 절대경로 / 드라이브 / UNC / 루트앵커 ----------

    @Test
    void 절대경로_윈도우드라이브(@TempDir Path root) {
        assertThrows(PathSafetyException.class,
                () -> svc.resolveSafeWritePath(root, "C:\\Windows\\System32\\evil.js"));
    }

    @Test
    void 절대경로_유닉스루트(@TempDir Path root) {
        assertThrows(PathSafetyException.class,
                () -> svc.resolveSafeWritePath(root, "/etc/passwd.xml"));
    }

    @Test
    void UNC_경로(@TempDir Path root) {
        assertThrows(PathSafetyException.class,
                () -> svc.resolveSafeWritePath(root, "\\\\attacker\\share\\evil.js"));
    }

    @Test
    void 콜론_드라이브_또는_ADS(@TempDir Path root) {
        assertThrows(PathSafetyException.class,
                () -> svc.resolveSafeWritePath(root, "foo.js:hidden.js"));
    }

    // ---------- 확장자 화이트리스트 ----------

    @Test
    void 비허용확장자_exe(@TempDir Path root) {
        assertThrows(PathSafetyException.class,
                () -> svc.resolveSafeWritePath(root, "admin/evil.exe"));
    }

    @Test
    void 비허용확장자_bat(@TempDir Path root) {
        assertThrows(PathSafetyException.class,
                () -> svc.resolveSafeWritePath(root, "admin/evil.bat"));
    }

    @Test
    void 확장자없음(@TempDir Path root) {
        assertThrows(PathSafetyException.class,
                () -> svc.resolveSafeWritePath(root, "admin/noext"));
    }

    @Test
    void 불법문자_포함_경로는_안전예외로_변환(@TempDir Path root) {
        // 윈도우 불법 파일명 문자(<>|)가 raw InvalidPathException이 아니라
        // PathSafetyException으로 나와야 계약이 샌다.
        assertThrows(PathSafetyException.class,
                () -> svc.resolveSafeWritePath(root, "admin/foo<bar>.js"));
    }

    // ---------- 빈 입력 ----------

    @Test
    void 빈_경로(@TempDir Path root) {
        assertThrows(PathSafetyException.class, () -> svc.resolveSafeWritePath(root, "  "));
    }

    // ---------- 심볼릭링크 탈출 ----------

    @Test
    void 심볼릭링크로_루트밖_탈출_차단(@TempDir Path root, @TempDir Path outside) throws IOException {
        // 루트 안의 'link' 가 루트 밖 outside 디렉터리를 가리키게 만든다.
        Path link = root.resolve("link");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException e) {
            // 윈도우에서 심링크 생성 권한이 없으면 이 케이스는 건너뛴다.
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "심링크 생성 불가 환경: " + e.getMessage());
            return;
        }

        assertThrows(PathSafetyException.class,
                () -> svc.resolveSafeWritePath(root, "link/evil.js"));

        // 루트 밖에 파일이 생기지 않았음을 확인
        assertFalse(Files.exists(outside.resolve("evil.js")));
    }
}
