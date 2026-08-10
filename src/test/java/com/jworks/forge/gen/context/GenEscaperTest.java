package com.jworks.forge.gen.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 🔒 P4-2 이스케이프 단위테스트(핵심 AC). 계약 §3.2 처리내용 검증 +
 * "원문 위험문자가 그대로 새어나가는 케이스 0" 확인.
 */
class GenEscaperTest {

    // ---- htmlText ----------------------------------------------------------

    @Test
    void htmlText_스크립트태그를_엔티티화한다() {
        assertEquals("&lt;script&gt;", GenEscaper.htmlText("<script>"));
    }

    @Test
    void htmlText_앰퍼샌드_따옴표도_이스케이프한다() {
        assertEquals("a&amp;b&quot;c&#39;d", GenEscaper.htmlText("a&b\"c'd"));
    }

    @Test
    void htmlText_null은_빈문자열() {
        assertEquals("", GenEscaper.htmlText(null));
    }

    // ---- htmlAttr ----------------------------------------------------------

    @Test
    void htmlAttr_쌍따옴표는_항상_엔티티화() {
        assertEquals("&quot;x", GenEscaper.htmlAttr("\"x"));
    }

    @Test
    void htmlAttr_전체_특수문자_엔티티화() {
        assertEquals("&amp;&lt;&gt;&quot;&#39;", GenEscaper.htmlAttr("&<>\"'"));
    }

    @Test
    void htmlAttr_null은_빈문자열() {
        assertEquals("", GenEscaper.htmlAttr(null));
    }

    // ---- jsString ----------------------------------------------------------

    @Test
    void jsString_스크립트종료를_조기종료_차단한다() {
        // </script> → 슬래시는 \/, < 는 \x3C 로 조기종료 불가.
        String out = GenEscaper.jsString("</script>");
        assertEquals("\\x3C\\/script>", out);
        // 원문 시퀀스 "</" 가 그대로 새어나가지 않는다.
        assertFalse(out.contains("</"), "jsString 결과에 원문 </ 가 남으면 안 된다");
    }

    @Test
    void jsString_따옴표_역슬래시를_이스케이프한다() {
        assertEquals("\\\\a\\'b\\\"c", GenEscaper.jsString("\\a'b\"c"));
    }

    @Test
    void jsString_개행과_라인종결자를_처리한다() {
        // \n → \\n, \r → \\r, U+2028/U+2029 → 공백
        assertEquals("a\\nb\\rc d e", GenEscaper.jsString("a\nb\rc d e"));
    }

    @Test
    void jsString_템플릿표현식문자열은_평문으로_보존된다() {
        // ${x} 자체는 JS 문자열 리터럴에서 특수문자가 아니므로 그대로(원문 삽입 위험 아님).
        // 단 위험문자 < 는 이스케이프되어 스크립트 문맥에서도 안전.
        assertEquals("${x}", GenEscaper.jsString("${x}"));
    }

    @Test
    void jsString_null은_빈문자열() {
        assertEquals("", GenEscaper.jsString(null));
    }

    // ---- cssToken ----------------------------------------------------------

    @Test
    void cssToken_유효_다중클래스는_공백조인() {
        assertEquals("a b", GenEscaper.cssToken("a b"));
        assertEquals("btn-primary btn_lg", GenEscaper.cssToken("btn-primary btn_lg"));
    }

    @Test
    void cssToken_위반토큰은_드롭하고_유효토큰만_남긴다() {
        // "a<b" 는 하나의 토큰(공백 없음)이며 화이트리스트 위반 → 전량 드롭 → 빈 문자열.
        assertEquals("", GenEscaper.cssToken("a<b"));
        // "a b<c" → a 는 유효, "b<c" 는 위반 → a 만.
        assertEquals("a", GenEscaper.cssToken("a b<c"));
    }

    @Test
    void cssToken_위험문자_포함시_원문이_새어나가지_않는다() {
        String out = GenEscaper.cssToken("ok\"; content:'x");
        // "ok\";" 도, "content:'x" 도 위반 토큰 → 전량 드롭.
        assertEquals("", out);
        assertFalse(out.contains("\""));
        assertFalse(out.contains("'"));
        assertFalse(out.contains(";"));
    }

    @Test
    void cssToken_전량위반이면_빈문자열_호출측이_class생략() {
        assertTrue(GenEscaper.cssToken("<> {}").isEmpty());
    }

    @Test
    void cssToken_null은_빈문자열() {
        assertEquals("", GenEscaper.cssToken(null));
    }

    // ---------- 계약 §18: JSP EL 차단 ----------

    /**
     * 🔒 생성물은 JSP다. 자유문자열의 {@code ${...}} 가 원시로 실리면 <b>타겟 톰캣이 렌더할 때</b>
     * EL 이 평가한다(생성 시점 FreeMarker 평가와는 다른 층위). {@code $}/{@code #} 를 엔티티화해
     * EL 시작 시퀀스를 원천 차단한다.
     */
    @Test
    void htmlText_EL_시작시퀀스를_차단한다() {
        assertEquals("&#36;{7*7}", GenEscaper.htmlText("${7*7}"));
        assertEquals("&#35;{2+2}", GenEscaper.htmlText("#{2+2}"));
        assertEquals("&#36;{pageContext.request}", GenEscaper.htmlText("${pageContext.request}"));
    }

    @Test
    void htmlAttr_EL_시작시퀀스를_차단한다() {
        assertEquals("&#36;{7*7}", GenEscaper.htmlAttr("${7*7}"));
        assertEquals("&#35;{2+2}", GenEscaper.htmlAttr("#{2+2}"));
    }

    /**
     * 🔒 인접 결합 우회 차단 — {@code $} 만 담은 값과 {@code {7*7}} 만 담은 값이 템플릿에서
     * 나란히 찍히면 붙어서 {@code ${7*7}} 이 된다. 그래서 조건부가 아니라 <b>무조건</b> 막는다.
     */
    @Test
    void 인접_결합으로도_EL이_만들어지지_않는다() {
        String joined = GenEscaper.htmlText("$") + GenEscaper.htmlText("{7*7}");
        assertFalse(joined.contains("${"), "인접 결합으로 EL 시퀀스가 생겼다: " + joined);
        assertEquals("&#36;{7*7}", joined);
    }

    /** 엔티티라서 브라우저에는 원래 문자로 보인다(표시 손실 없음). */
    @Test
    void 달러와_샵은_엔티티로만_바뀌고_소실되지_않는다() {
        assertEquals("&#36;100", GenEscaper.htmlText("$100"));
        assertEquals("&#35;3", GenEscaper.htmlText("#3"));
    }

    /** JS 문맥은 JSP 가 아니므로(.js 산출) EL 대상이 아니다 — 종전 동작 유지. */
    @Test
    void jsString은_EL차단_대상이_아니다() {
        assertTrue(GenEscaper.jsString("${7*7}").contains("${7*7}"));
    }
}
