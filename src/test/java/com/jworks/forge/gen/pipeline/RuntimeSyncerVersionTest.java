package com.jworks.forge.gen.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jworks.forge.gen.safety.PathSafetyService;

/**
 * 🔒 P6-1 런타임 버전관리(RuntimeSyncer 버전 비교·갱신) 검증(계약 §11).
 *
 * <ul>
 *   <li><b>신규</b>(마커 없음): 전량 복사 + 버전 마커 스탬프.</li>
 *   <li><b>동일 버전</b>: 전량 skip(재복사 0).</li>
 *   <li><b>구버전</b>: 백업(.bak) 후 덮어쓰기 갱신 + 마커 상향.</li>
 *   <li><b>상위 버전</b>: 다운그레이드 차단(skip, 변경 0).</li>
 *   <li><b>compareVersions</b> 단위.</li>
 * </ul>
 */
class RuntimeSyncerVersionTest {

    @TempDir
    Path targetRoot;

    private RuntimeSyncer syncer() {
        var pathSafety = new PathSafetyService();
        return new RuntimeSyncer(pathSafety, new AtomicFileWriter());
    }

    private List<GenFile> sync() throws IOException {
        return syncer().sync(targetRoot, targetRoot.toRealPath());
    }

    private Path marker() {
        return targetRoot.resolve(RuntimeSyncer.RUNTIME_VERSION_MARKER.replace("/", java.io.File.separator));
    }

    private void writeMarker(String version) throws IOException {
        Path m = marker();
        Files.createDirectories(m.getParent());
        Files.writeString(m, "<forge-runtime version=\"" + version + "\"/>\n", StandardCharsets.UTF_8);
    }

    private long backupCount() throws IOException {
        try (Stream<Path> s = Files.walk(targetRoot)) {
            return s.filter(p -> p.getFileName().toString().contains(".bak")).count();
        }
    }

    @Test
    void 신규타겟은_전량_복사하고_버전마커를_스탬프한다() throws IOException {
        List<GenFile> results = sync();
        long copied = results.stream().filter(f -> f.artifactKey().startsWith("runtime:")).count();
        assertTrue(copied > 0, "신규 타겟은 런타임 전량 복사");
        assertTrue(Files.exists(marker()), "버전 마커 생성");
        String mk = Files.readString(marker(), StandardCharsets.UTF_8);
        assertTrue(mk.contains("version=\"" + RuntimeSyncer.RUNTIME_SET_VERSION + "\""), "마커에 현재 버전");
        // 대표 런타임 파일이 실제로 복사됨.
        assertTrue(Files.exists(targetRoot.resolve("static/js/admin/common/commonSection.js")),
                "commonSection.js 복사됨");
    }

    @Test
    void 동일버전_마커면_전량_skip한다() throws IOException {
        sync(); // 1회차: 복사 + 마커 스탬프(현재 버전)
        List<GenFile> second = sync(); // 2회차: 동일 버전 → skip
        long runtimeSecond = second.stream().filter(f -> f.artifactKey().startsWith("runtime:")).count();
        assertEquals(0, runtimeSecond, "동일 버전은 재복사 0");
        assertEquals(0, backupCount(), "동일 버전은 백업(.bak) 0");
    }

    @Test
    void 구버전_마커면_백업후_갱신하고_마커를_상향한다() throws IOException {
        // 구버전 마커 + 낡은 내용의 런타임 파일 1개 선배치.
        writeMarker("0.9.0");
        Path stale = targetRoot.resolve("static/js/admin/common/commonSection.js");
        Files.createDirectories(stale.getParent());
        Files.writeString(stale, "OLD_STALE_CONTENT", StandardCharsets.UTF_8);

        List<GenFile> results = sync();
        long updated = results.stream().filter(f -> f.artifactKey().startsWith("runtime:")).count();
        assertTrue(updated > 0, "구버전은 갱신(덮어쓰기)");
        // 낡은 파일이 실제 번들 내용으로 교체됨(+ .bak 백업 존재).
        String now = Files.readString(stale, StandardCharsets.UTF_8);
        assertFalse(now.equals("OLD_STALE_CONTENT"), "구버전 파일이 번들 내용으로 갱신됨");
        assertTrue(backupCount() > 0, "갱신 시 .bak 백업 생성");
        // 마커 상향.
        String mk = Files.readString(marker(), StandardCharsets.UTF_8);
        assertTrue(mk.contains("version=\"" + RuntimeSyncer.RUNTIME_SET_VERSION + "\""), "마커 현재 버전으로 상향");
    }

    @Test
    void 상위버전_마커면_다운그레이드_차단하고_변경하지_않는다() throws IOException {
        writeMarker("99.0.0");
        Path stale = targetRoot.resolve("static/js/admin/common/commonSection.js");
        Files.createDirectories(stale.getParent());
        Files.writeString(stale, "FUTURE_CONTENT", StandardCharsets.UTF_8);

        List<GenFile> results = sync();
        long touched = results.stream().filter(f -> f.artifactKey().startsWith("runtime:")).count();
        assertEquals(0, touched, "상위 버전은 skip(다운그레이드 차단)");
        assertEquals("FUTURE_CONTENT", Files.readString(stale, StandardCharsets.UTF_8), "파일 변경 0");
        assertEquals(0, backupCount(), "백업 0");
    }

    @Test
    void compareVersions_단위() {
        assertTrue(RuntimeSyncer.compareVersions("1.0.0", "1.0.0") == 0, "동일");
        assertTrue(RuntimeSyncer.compareVersions("0.9.0", "1.0.0") < 0, "구버전<현재");
        assertTrue(RuntimeSyncer.compareVersions("1.2.0", "1.0.0") > 0, "상위>현재");
        assertTrue(RuntimeSyncer.compareVersions("1.0", "1.0.0") == 0, "누락 세그먼트=0");
        assertTrue(RuntimeSyncer.compareVersions("2.0.0", "10.0.0") < 0, "정수 비교(문자열 아님)");
    }
}
