package com.jworks.forge.gen.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🔒 문맥별 이스케이프 유틸 (P4-2, 계약 §3.2).
 *
 * <p>DEFINITION_JSON §5 신뢰경계: 서버(빌더)는 props 자유문자열을 이스케이프하지 않는다.
 * <b>소비자=템플릿</b>이 산출 문맥(HTML 텍스트/HTML 속성/JS 문자열/CSS 토큰)에 맞게
 * 이 클래스의 함수로 이스케이프한다. 따라서 이 4개 함수는 <b>순수 함수</b>이며
 * 템플릿에서 {@code ${htmlText(...)}} / {@code ${jsString(...)}} 형태로 호출된다
 * (FreeMarker 등록은 {@code CodeGenTemplateConfig}).
 *
 * <p>계약 §3.2 표의 처리내용을 <b>그대로</b> 구현한다(임의 축소·변경 금지). null 입력은
 * 모두 빈 문자열로 처리한다(NPE 방지, 계약 호출측 안전).
 *
 * <p>이 클래스는 P4 보안의 핵심이며 <b>reviewer 🔒 이스케이프 검수 대상</b>이다.
 */
public final class GenEscaper {

    private static final Logger log = LoggerFactory.getLogger(GenEscaper.class);

    /** CSS 토큰 화이트리스트: {@code [A-Za-z0-9_-]}만 허용(계약 §3.2 cssToken). */
    private static final java.util.regex.Pattern CSS_TOKEN_WHITELIST =
            java.util.regex.Pattern.compile("^[A-Za-z0-9_-]+$");

    private GenEscaper() {
        // 순수 함수 유틸 — 인스턴스화 금지.
    }

    /**
     * HTML 요소 텍스트 노드 이스케이프({@code <td>여기</td>}, {@code <button>여기</button>}).
     * {@code &}→{@code &amp;}, {@code <}→{@code &lt;}, {@code >}→{@code &gt;}
     * (+ {@code "}→{@code &quot;}, {@code '}→{@code &#39;} 포함, 무해).
     * <b>{@code $}→{@code &#36;}, {@code #}→{@code &#35;}</b> (계약 §18 — JSP EL 차단).
     *
     * @param s 자유문자열(null 허용)
     * @return HTML 텍스트 문맥에 안전한 문자열(null → 빈 문자열)
     */
    public static String htmlText(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                case '$': sb.append("&#36;"); break;   // §18 ${...} JSP EL 차단
                case '#': sb.append("&#35;"); break;   // §18 #{...} 지연 EL 차단
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * HTML 속성값 이스케이프({@code data-name="여기"}, {@code class="여기"}).
     * {@code &}/{@code <}/{@code >}/{@code "}/{@code '} 전부 엔티티화.
     * {@code "}→{@code &quot;}는 <b>항상 수행</b>(옵션 아님). 속성은 항상 쌍따옴표로 감싸는 전제.
     * <b>{@code $}→{@code &#36;}, {@code #}→{@code &#35;}</b> (계약 §18 — JSP EL 차단).
     *
     * @param s 자유문자열(null 허용)
     * @return HTML 속성 문맥에 안전한 문자열(null → 빈 문자열)
     */
    public static String htmlAttr(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                case '$': sb.append("&#36;"); break;   // §18 ${...} JSP EL 차단
                case '#': sb.append("&#35;"); break;   // §18 #{...} 지연 EL 차단
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * JS 문자열 리터럴 내부 이스케이프({@code var x = '여기';}). 결과는 <b>따옴표 안에 넣을
     * 리터럴 본문</b>이다.
     *
     * <p>계약 §3.2 처리내용(축소 금지):
     * <ul>
     *   <li>{@code \}→{@code \\}, {@code '}→{@code \'}, {@code "}→{@code \"}</li>
     *   <li>{@code /}→{@code \/} (특히 {@code </script>} 조기종료 방지)</li>
     *   <li>{@code <}→{@code \x3C} (인라인 {@code <script>} 조기종료 방지, 문맥무관 견고)</li>
     *   <li>{@code \n}→{@code \\n}, {@code \r}→{@code \\r}</li>
     *   <li>{@code U+2028}/{@code U+2029}(JS 라인종결자)→공백</li>
     * </ul>
     *
     * @param s 자유문자열(null 허용)
     * @return JS 문자열 리터럴 문맥에 안전한 본문(null → 빈 문자열)
     */
    public static String jsString(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\'': sb.append("\\'"); break;
                case '"': sb.append("\\\""); break;
                case '/': sb.append("\\/"); break;    // </script> 조기종료 차단
                case '<': sb.append("\\x3C"); break;   // 인라인 script 조기종료 방지(권장)
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case ' ': sb.append(' '); break;  // JS Line Separator
                case ' ': sb.append(' '); break;  // JS Paragraph Separator
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * CSS 식별자/클래스 토큰 화이트리스트 검증({@code class="{여기}"}).
     *
     * <p>허용 문자 {@code [A-Za-z0-9_-]}. 공백 구분 다중 클래스는 토큰별로 검증하여
     * <b>유효 토큰만</b> 공백조인한다. 위반 토큰(공백·{@code <}·{@code >}·{@code "}·{@code {}·{@code }} 등
     * 포함)은 <b>드롭 + 경고 로그</b>(계약 §3.4). 전량 위반이면 빈 문자열을 반환하며,
     * 호출측(템플릿)은 이때 {@code class} 속성 자체를 생략한다.
     *
     * @param s 자유문자열(null 허용)
     * @return 유효 CSS 토큰만 공백조인한 문자열(전량 위반·null → 빈 문자열)
     */
    public static String cssToken(String s) {
        if (s == null) {
            return "";
        }
        // 공백(스페이스/탭/개행 등) 기준으로 토큰 분리 → 토큰별 화이트리스트 검증.
        String[] tokens = s.trim().split("\\s+");
        StringBuilder sb = new StringBuilder(s.length());
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            if (CSS_TOKEN_WHITELIST.matcher(token).matches()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(token);
            } else {
                // 위반 토큰 드롭 + 경고(원문 값은 로그에 노출하지 않고 길이만 — 로그 인젝션 방지).
                log.warn("[GenEscaper] cssToken dropped invalid token (len={})", token.length());
            }
        }
        return sb.toString();
    }
}
