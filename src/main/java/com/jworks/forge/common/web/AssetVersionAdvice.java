package com.jworks.forge.common.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 정적 자산 캐시버스팅 버전을 모든 빌더 뷰(@Controller)에 자동 주입한다.
 *
 * <p><b>왜 필요한가</b>: 빌더 JSP 는 JS/CSS 를 {@code ...css?v=20260807-2} 처럼 <b>손으로 올린
 * 버전</b>으로 링크했고, 특히 {@code header.jsp}(번들 런타임 매니페스트)는 {@code ?v=} 가 아예
 * 없었다. 그래서 파일을 고쳐도 브라우저가 <b>캐시된 옛 파일</b>을 계속 썼다 — 좌우 2단 CSS,
 * 삭제 확인 로직이 코드상 멀쩡한데도 "안 된다"로 보인 원인이 이것이었다.
 * JSP 는 {@code ?v=${assetVer}} 로 참조한다.
 *
 * <h2>개발 모드: 파일 변경 시각을 따라간다</h2>
 * 처음에는 값을 <b>JVM 시작 시각</b>으로 고정했는데, 그러면 <b>앱을 안 내리고 JS/CSS 만 고치는</b>
 * 흔한 개발 흐름에서 여전히 캐시가 물린다(실제로 그렇게 물렸다). 그래서 기본값
 * {@code forge.asset-version.watch=true} 에서는 정적 자산 디렉터리의 <b>최신 수정 시각</b>을
 * 값으로 쓴다 — 파일을 고치는 즉시 링크가 바뀌므로 새로고침만으로 반영된다.
 *
 * <p>디렉터리 훑기 비용이 걱정되면 {@code forge.asset-version.watch=false} 로 두면 되고,
 * 그때는 JVM 시작 시각으로 고정된다(운영 배포 기준). 훑기에 실패해도 시작 시각으로 안전하게
 * 폴백하므로 이 기능이 페이지 렌더를 깨뜨리지 않는다.
 *
 * <p>{@code annotations = Controller.class} 로 <b>뷰 컨트롤러에만</b> 붙는다 — @RestController
 * (API)는 모델을 안 쓰므로 대상에서 제외한다.
 */
@ControllerAdvice(annotations = Controller.class)
public class AssetVersionAdvice {

    /** 앱 시작 시각. watch 가 꺼져 있거나 훑기에 실패했을 때 쓰는 안정적인 폴백. */
    private static final long STARTED_AT = System.currentTimeMillis();

    /** 개발 중 정적 자산을 읽는 소스 경로(클래스패스 복사본이 아니라 원본이 먼저 바뀐다). */
    private static final Path[] WATCH_ROOTS = {
        Paths.get("src", "main", "resources", "static"),
        Paths.get("target", "classes", "static")
    };

    private final boolean watch;

    public AssetVersionAdvice(
            @Value("${forge.asset-version.watch:true}") boolean watch) {
        this.watch = watch;
    }

    @ModelAttribute("assetVer")
    public long assetVer() {
        if (!watch) {
            return STARTED_AT;
        }
        long newest = 0L;
        for (Path root : WATCH_ROOTS) {
            newest = Math.max(newest, newestModified(root));
        }
        return newest > 0 ? newest : STARTED_AT;
    }

    /** 디렉터리 하위 파일의 최신 수정 시각(ms). 접근 불가·부재면 0 — 호출측이 폴백한다. */
    private static long newestModified(Path root) {
        if (!Files.isDirectory(root)) {
            return 0L;
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .mapToLong(AssetVersionAdvice::lastModifiedQuiet)
                    .max()
                    .orElse(0L);
        } catch (IOException | UncheckedIOException e) {
            return 0L;  // 훑기 실패가 화면 렌더를 막지 않는다
        }
    }

    private static long lastModifiedQuiet(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}
