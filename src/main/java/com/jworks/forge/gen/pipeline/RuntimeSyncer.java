package com.jworks.forge.gen.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.jworks.forge.gen.safety.PathSafetyException;
import com.jworks.forge.gen.safety.PathSafetyService;

/**
 * 🔒 런타임 공통(1층위) 동기화 (P4-6, 기획서 6장). <b>P4 보안 코어의 일부 — reviewer 🔒 검수 대상.</b>
 *
 * <p>번들 런타임(jQuery 3.7.1 + jworks 6종 + admin/common 공통 JS/CSS — {@code header.jsp} 매니페스트가
 * 참조하는 실제 파일들)을 타겟 프로젝트 루트 하위로 <b>1회 복사</b>한다. 이미 존재하면 <b>재복사 skip</b>
 * (버전비교·갱신은 P6 이관). 화면을 반복 생성해도 런타임 중복 생성은 0이 된다.
 *
 * <p>보안 요지:
 * <ul>
 *   <li><b>복사 대상 목록은 전부 정적 상수</b>({@link #RUNTIME_RELATIVE_PATHS}). DB/사용자 입력에서 온
 *       경로는 하나도 없다 — 매니페스트를 코드 상수로 미러링한 화이트리스트뿐이다(자유문자열 0).</li>
 *   <li><b>모든 타겟 쓰기는 {@link PathSafetyService#resolveSafeWritePath} + {@link AtomicFileWriter}
 *       경유</b>. 이 클래스는 직접 {@code Files.write}로 타겟에 쓰지 않는다(원본을 classpath에서 읽어
 *       내용 문자열로만 넘긴다).</li>
 *   <li><b>skip 판정은 대상 존재확인(NOFOLLOW)</b>. 존재하면 write를 호출하지 않으므로 백업·덮어쓰기도
 *       발생하지 않는다(런타임 재복사 0).</li>
 * </ul>
 *
 * <p><b>P6 이관</b>: 런타임 버전 비교/갱신, 화면 반복패턴(2층위) 공통 파셜·상수 추출은 P6 스코프다.
 * P4는 "존재하면 skip" 최소 정책만 구현한다.
 */
@Component
public class RuntimeSyncer {

    private static final Logger log = LoggerFactory.getLogger(RuntimeSyncer.class);

    /**
     * 🔒 복사할 번들 런타임 파일의 <b>정적 상대경로 목록</b>(계약 §4.3). 값은 전부 컴파일 상수로,
     * {@code admin/common/header.jsp} 매니페스트가 참조하는 실제 파일을 미러링한다. 타겟 상대경로 =
     * classpath 상대경로와 동일하게 두어(둘 다 {@code static/...}·{@code external/...}) 규약 위치를 보존한다.
     * 확장자는 전부 화이트리스트({@code js}/{@code css}) 내이며 자유문자열은 하나도 없다.
     *
     * <p>매니페스트와의 대응: jquery 1 + jworks css 6 + jworks js 6 + admin/common css 7 +
     * admin/common js 12(constants/sendPost/common-ajax/commonUtils/commonSection/commonPopup/
     * commonList/commonListTableView/commonListCardView/commonListTreeView/commonListFormView/
     * commonAttributeHandler). js-singleton/js-constants는 JSP 인클루드(정적리소스 아님)라 제외.
     */
    static final List<String> RUNTIME_RELATIVE_PATHS = List.of(
            // jQuery (번들 3.7.1)
            "static/external/jquery/jquery-3.7.1.min.js",
            // jworks 6종 CSS
            "static/external/jworks/jworks-loadingspinner-1.0.0.css",
            "static/external/jworks/jworks-snackbar-1.0.0.css",
            "static/external/jworks/jworks-alert-1.0.0.css",
            "static/external/jworks/jworks-confirm-1.0.0.css",
            "static/external/jworks/jworks-tooltip-1.0.0.css",
            "static/external/jworks/jworks-empty-view-0.0.1.css",
            // jworks 6종 JS
            "static/external/jworks/jworks-loadingspinner-1.0.0.js",
            "static/external/jworks/jworks-snackbar-1.0.0.js",
            "static/external/jworks/jworks-alert-1.0.0.js",
            "static/external/jworks/jworks-confirm-1.0.0.js",
            "static/external/jworks/jworks-tooltip-1.0.0.js",
            "static/external/jworks/jworks-empty-view-0.0.1.js",
            // admin/common CSS
            "static/css/admin/common/init.css",
            "static/css/admin/common/commonSection.css",
            "static/css/admin/common/commonPopup.css",
            "static/css/admin/common/commonListTableView.css",
            "static/css/admin/common/commonListCardView.css",
            "static/css/admin/common/commonListTreeView.css",
            "static/css/admin/common/commonListFormView.css",
            // P6-2: 생성 화면 공통 레이아웃(뷰/상세/듀얼 고정 규칙 추출본). header.jsp 매니페스트가 링크.
            "static/css/admin/common/commonScreenLayout.css",
            // admin/common JS
            "static/js/admin/common/constants.js",
            "static/js/admin/common/sendPost.js",
            "static/js/admin/common/common-ajax.js",
            "static/js/admin/common/commonUtils.js",
            "static/js/admin/common/commonSection.js",
            "static/js/admin/common/commonPopup.js",
            "static/js/admin/common/commonList.js",
            "static/js/admin/common/commonListTableView.js",
            "static/js/admin/common/commonListCardView.js",
            "static/js/admin/common/commonListTreeView.js",
            "static/js/admin/common/commonListFormView.js",
            "static/js/admin/common/commonAttributeHandler.js");

    /**
     * 🔒 번들 런타임 <b>세트 버전</b>(P6-1, 계약 §11). 런타임 파일 집합 전체의 단일 버전(스코프 결정 (a)).
     * 번들 런타임(commonSection.js 등)을 갱신하면 이 상수를 올린다 → 기존 타겟이 구버전 마커면 갱신된다.
     * 값은 컴파일 상수(자유문자열 0).
     */
    // P6-2: commonScreenLayout.css 번들 추가로 런타임 세트 변경 → 1.0.0 → 1.1.0(기존 타겟은 갱신됨).
    static final String RUNTIME_SET_VERSION = "1.1.0";

    /**
     * 🔒 타겟에 기록하는 런타임 버전 마커의 <b>정적 상대경로</b>(계약 §11). 확장자 {@code xml}은
     * {@link PathSafetyService#DEFAULT_ALLOWED_EXTENSIONS} 내(숨김파일·비허용 확장자 불가라 dotfile 아님).
     * sync 시 이 마커의 버전과 {@link #RUNTIME_SET_VERSION}을 비교해 갱신/보존/차단을 결정한다.
     */
    static final String RUNTIME_VERSION_MARKER = "static/forge-runtime.xml";

    /** 마커 파일에서 버전 추출용 패턴({@code version="..."}). */
    private static final java.util.regex.Pattern MARKER_VERSION_PATTERN =
            java.util.regex.Pattern.compile("version=\"([^\"]+)\"");

    private final PathSafetyService pathSafetyService;
    private final AtomicFileWriter fileWriter;

    public RuntimeSyncer(PathSafetyService pathSafetyService, AtomicFileWriter fileWriter) {
        this.pathSafetyService = pathSafetyService;
        this.fileWriter = fileWriter;
    }

    /**
     * 번들 런타임을 {@code targetRoot} 하위로 1회 동기화한다. 이미 존재하는 대상은 skip.
     *
     * @param targetRoot    프로젝트 TARGET_ROOT_PATH(반드시 존재하는 절대 디렉터리)
     * @param canonicalRoot {@code targetRoot.toRealPath()}(쓰기 직전 재확인 앵커, 계약 §5.1)
     * @return 파일 단위 결과(복사=성공 GenFile, skip은 결과에서 제외해 "재복사 0"을 표현)
     */
    public List<GenFile> sync(Path targetRoot, Path canonicalRoot) {
        // P6-1: 타겟 버전 마커를 읽어 갱신/보존/차단을 결정한다(계약 §11). 마커 없음(신규/레거시 타겟)은
        // 기존 "존재하면 skip" 동작(파일별 복사)으로 처리한 뒤 현재 버전으로 스탬프한다(하위호환).
        String targetVer = readMarkerVersion(targetRoot);
        final boolean forceOverwrite;   // 버전 갱신 시 기존 파일도 덮어쓰기(.bak 백업)
        boolean stampMarker;            // 이번 sync 후 마커를 현재 버전으로 기록할지

        if (targetVer == null) {
            // 마커 없음: 파일별 존재-skip(기존 동작) + 스탬프.
            forceOverwrite = false;
            stampMarker = true;
        } else {
            int cmp = compareVersions(targetVer, RUNTIME_SET_VERSION);
            if (cmp == 0) {
                // 동일 버전: 전량 skip(재복사 0). 마커 재기록 불필요.
                log.info("[RuntimeSync] 런타임 버전 동일({}) — 전량 skip", RUNTIME_SET_VERSION);
                return new ArrayList<>();
            }
            if (cmp > 0) {
                // 다운그레이드 금지: 타겟이 더 높은 버전이면 건드리지 않는다.
                log.warn("[RuntimeSync] 타겟 런타임({})이 번들({})보다 높음 — 다운그레이드 차단(skip)",
                        targetVer, RUNTIME_SET_VERSION);
                return new ArrayList<>();
            }
            // 타겟이 구버전: 갱신(백업 후 덮어쓰기) + 스탬프.
            log.info("[RuntimeSync] 런타임 갱신 {} → {}(백업 후 덮어쓰기)", targetVer, RUNTIME_SET_VERSION);
            forceOverwrite = true;
            stampMarker = true;
        }

        List<GenFile> results = new ArrayList<>();
        int copied = 0;
        int skipped = 0;
        for (String rel : RUNTIME_RELATIVE_PATHS) {
            SyncOutcome outcome = syncOne(rel, targetRoot, canonicalRoot, forceOverwrite);
            switch (outcome.kind()) {
                case COPIED -> {
                    copied++;
                    results.add(GenFile.ok("runtime:" + rel, rel));
                }
                case SKIPPED -> skipped++;   // 재복사 0 — 결과 목록에 추가하지 않는다.
                case FAILED -> results.add(GenFile.fail("runtime:" + rel, rel, outcome.reason()));
            }
        }

        // 🔒 버전 마커 기록(결과 목록에는 넣지 않는다 — 화면/런타임 산출 카운트 불변). 실패는 non-fatal.
        if (stampMarker) {
            writeMarker(targetRoot, canonicalRoot);
        }

        log.info("[RuntimeSync] 완료 — {} {} / skip(존재) {} / 총 {} (버전 {})",
                forceOverwrite ? "갱신" : "복사", copied, skipped,
                RUNTIME_RELATIVE_PATHS.size(), RUNTIME_SET_VERSION);
        return results;
    }

    /**
     * 런타임 1건: {@code forceOverwrite=false}면 대상 존재 시 skip(기존 동작), {@code true}면 존재해도
     * classpath 원본으로 덮어쓴다(AtomicFileWriter가 기존을 {@code .bak} 백업). 쓰기는 예외 없이
     * resolveSafeWritePath + 원자적 쓰기 경유(계약 §4.1).
     */
    private SyncOutcome syncOne(String rel, Path targetRoot, Path canonicalRoot, boolean forceOverwrite) {
        final Path safeAbs;
        try {
            // 🔒 정적 상수 rel도 예외 없이 경로안전 계층을 통과시킨다(계약 §4.1).
            safeAbs = pathSafetyService.resolveSafeWritePath(targetRoot, rel);
        } catch (PathSafetyException e) {
            log.error("[RuntimeSync] 경로안전 위반 — rel={} : {}", rel, e.getMessage());
            return SyncOutcome.failed("경로안전 위반: " + e.getMessage());
        }

        // 🔒 skip 판정: 갱신 모드가 아니고 대상이 이미 존재하면(NOFOLLOW) 재복사하지 않는다(백업·덮어쓰기 0).
        if (!forceOverwrite && Files.exists(safeAbs, LinkOption.NOFOLLOW_LINKS)) {
            log.debug("[RuntimeSync] 이미 존재 — skip: {}", rel);
            return SyncOutcome.skipped();
        }

        final String content;
        try {
            content = readClasspath(rel);
        } catch (IOException e) {
            log.error("[RuntimeSync] 원본 읽기 실패 — rel={} : {}", rel, e.getMessage());
            return SyncOutcome.failed("원본 읽기 실패: " + e.getMessage());
        }

        try {
            // 🔒 모든 타겟 쓰기는 resolveSafeWritePath 반환값 + AtomicFileWriter 경유.
            fileWriter.write(safeAbs, canonicalRoot, content);
            return SyncOutcome.copied();
        } catch (RuntimeException e) {
            log.error("[RuntimeSync] 쓰기 실패 — rel={} : {}", rel, e.getMessage());
            return SyncOutcome.failed(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** classpath({@code src/main/resources/static/...}) 소스 원본을 UTF-8 문자열로 읽는다. */
    private String readClasspath(String rel) throws IOException {
        // rel은 정적 상수라 "static/" 접두가 보장되나, classpath 리소스 경로로 그대로 사용한다.
        ClassPathResource resource = new ClassPathResource(rel);
        if (!resource.exists()) {
            throw new IOException("classpath 원본 없음: " + rel);
        }
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 타겟의 런타임 버전 마커를 읽어 버전 문자열을 돌려준다(계약 §11). 마커 없음·읽기 실패·형식 불명은
     * {@code null}(=마커 없음으로 취급 → 파일별 존재-skip + 재스탬프). 읽기이므로 쓰기 백업/변경 0.
     * 경로는 정적 상수지만 {@code resolveSafeWritePath}로 타겟 하위임을 재확인한다(심층방어).
     */
    private String readMarkerVersion(Path targetRoot) {
        try {
            Path marker = pathSafetyService.resolveSafeWritePath(targetRoot, RUNTIME_VERSION_MARKER);
            if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            String content = Files.readString(marker, StandardCharsets.UTF_8);
            var m = MARKER_VERSION_PATTERN.matcher(content);
            return m.find() ? m.group(1) : null;
        } catch (IOException | RuntimeException e) {
            // PathSafetyException은 RuntimeException 하위 — 읽기 실패는 마커 없음으로 취급(재스탬프).
            log.warn("[RuntimeSync] 버전 마커 읽기 실패 — 마커 없음으로 취급: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 타겟에 현재 런타임 세트 버전 마커를 기록한다(계약 §11). 내용은 정적 상수 버전만 삽입(자유문자열 0).
     * 실패는 non-fatal(런타임 파일 자체는 이미 동기화됨) — 경고만 남긴다.
     */
    private void writeMarker(Path targetRoot, Path canonicalRoot) {
        try {
            Path safeAbs = pathSafetyService.resolveSafeWritePath(targetRoot, RUNTIME_VERSION_MARKER);
            String content = "<forge-runtime version=\"" + RUNTIME_SET_VERSION + "\"/>\n";
            fileWriter.write(safeAbs, canonicalRoot, content);
        } catch (RuntimeException e) {
            // PathSafetyException·AtomicWriteException 모두 RuntimeException 하위. 마커 실패는 non-fatal.
            log.warn("[RuntimeSync] 버전 마커 기록 실패(non-fatal): {}", e.getMessage());
        }
    }

    /**
     * 점 구분 버전 비교(예: {@code 1.0.0} vs {@code 1.2.0}). 각 세그먼트를 정수로 비교(비정수·누락은 0).
     * @return {@code a<b} 음수 / {@code a==b} 0 / {@code a>b} 양수
     */
    static int compareVersions(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int x = parseSegment(i < pa.length ? pa[i] : "0");
            int y = parseSegment(i < pb.length ? pb[i] : "0");
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    private static int parseSegment(String s) {
        int val = 0;
        boolean any = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                break;
            }
            val = val * 10 + (c - '0');
            any = true;
        }
        return any ? val : 0;
    }

    /** 런타임 1건 동기화 결과(내부용). */
    private record SyncOutcome(Kind kind, String reason) {
        enum Kind { COPIED, SKIPPED, FAILED }

        static SyncOutcome copied() { return new SyncOutcome(Kind.COPIED, null); }
        static SyncOutcome skipped() { return new SyncOutcome(Kind.SKIPPED, null); }
        static SyncOutcome failed(String reason) { return new SyncOutcome(Kind.FAILED, reason); }
    }
}
