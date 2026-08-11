package com.jworks.forge.common.web;

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
 *
 * <p>JSP 는 {@code ?v=${assetVer}} 로 참조한다. 값은 <b>JVM 시작 시각(ms)</b>이라 앱을 재시작할
 * 때마다 바뀌어 브라우저 캐시가 확실히 무효화된다(개발 중 재시작이 잦으므로 충분하다).
 * 프로덕션 빌드 버전으로 바꾸려면 이 상수만 build-info 값으로 교체하면 된다.
 *
 * <p>{@code annotations = Controller.class} 로 <b>뷰 컨트롤러에만</b> 붙는다 — @RestController
 * (API)는 모델을 안 쓰므로 대상에서 제외한다.
 */
@ControllerAdvice(annotations = Controller.class)
public class AssetVersionAdvice {

    /** JVM 인스턴스마다 유일한 캐시버스팅 토큰. 재시작 시 갱신된다. */
    private static final long ASSET_VERSION = System.currentTimeMillis();

    @ModelAttribute("assetVer")
    public long assetVer() {
        return ASSET_VERSION;
    }
}
