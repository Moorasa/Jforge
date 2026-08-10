package com.jworks.forge.gen.pipeline;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🔒 보호구역(custom region) 병합 (P12, 계약 §16). <b>순수 함수 · 파일 접근 0.</b>
 *
 * <p><b>문제</b>: 생성된 Controller/Mapper에 사람이 로직을 채워 넣은 뒤 화면을 다시 생성하면
 * 그 손길이 통째로 덮여 사라진다(백업 {@code .bak}만 남는다). 코드 생성 도구가 실무에서
 * 버려지는 가장 흔한 이유다.
 *
 * <p><b>해법</b>: 생성물에 <b>보호구역 마커</b>를 심어 두고, 재생성 때 <b>기존 파일의 구역 내용을
 * 새 내용으로 옮겨 담는다</b>. 마커 밖(생성기 소관)은 항상 최신으로 갱신되고, 마커 안(사람 소관)은
 * 보존된다.
 *
 * <pre>
 * // &lt;j-forge:custom id="methods"&gt;
 * ... 사람이 쓴 코드(보존됨) ...
 * // &lt;/j-forge:custom&gt;
 * </pre>
 *
 * <p><b>안전 규약</b>:
 * <ul>
 *   <li>보존 대상은 <b>새 내용에도 같은 id의 구역이 있는 경우만</b>이다. 새 템플릿에서 사라진
 *       구역의 옛 내용은 옮기지 않는다(유령 코드 삽입 방지 — 대신 백업에 남는다).</li>
 *   <li>id는 <b>정적 화이트리스트 형태</b>({@code [a-zA-Z][a-zA-Z0-9_-]{0,31}})만 인식한다.</li>
 *   <li>중첩·미종료 구역은 병합하지 않고 무시한다(경고 로그). 실패는 항상 "새 내용 그대로"로
 *       수렴한다 — 병합 실패가 생성 실패로 번지지 않는다.</li>
 *   <li>이 클래스는 <b>내용만</b> 다룬다. 파일 읽기/쓰기·경로 판단은 호출측(StubGenerator) 소관이다.</li>
 * </ul>
 */
public final class CustomRegionMerger {

    private static final Logger log = LoggerFactory.getLogger(CustomRegionMerger.class);

    /** 마커 id 화이트리스트. */
    private static final String ID = "[a-zA-Z][a-zA-Z0-9_-]{0,31}";

    /**
     * 여는/닫는 마커. 주석 문법(자바 {@code //}, XML {@code <!-- -->})에 독립적으로 매칭하도록
     * 마커 토큰만 보고 줄 단위로 인식한다.
     */
    private static final Pattern OPEN = Pattern.compile("<j-forge:custom\\s+id=\"(" + ID + ")\">");
    private static final Pattern CLOSE = Pattern.compile("</j-forge:custom>");

    private CustomRegionMerger() {
    }

    /**
     * {@code existing}의 보호구역 내용을 {@code generated}의 같은 id 구역에 옮겨 담는다.
     *
     * @param generated 이번에 생성한 내용(마커 포함, 구역은 보통 비어 있음)
     * @param existing  타겟에 이미 있던 파일 내용(없으면 null)
     * @return 병합 결과. 보존할 것이 없으면 {@code generated} 그대로.
     */
    public static String merge(String generated, String existing) {
        if (generated == null || existing == null || existing.isEmpty()) {
            return generated;
        }
        Map<String, String> preserved = extractRegions(existing);
        if (preserved.isEmpty()) {
            return generated;
        }
        return replaceRegions(generated, preserved);
    }

    /** 내용에서 {@code id → 구역 본문} 맵을 뽑는다(줄 단위). 미종료/중첩은 버린다. */
    static Map<String, String> extractRegions(String content) {
        Map<String, String> regions = new LinkedHashMap<>();
        String[] lines = content.split("\n", -1);
        String openId = null;
        StringBuilder buffer = null;

        for (String line : lines) {
            Matcher open = OPEN.matcher(line);
            if (open.find()) {
                if (openId != null) {
                    log.warn("[CustomRegion] 중첩 구역 발견 — 병합 생략(id={})", openId);
                    return Map.of();
                }
                openId = open.group(1);
                buffer = new StringBuilder();
                continue;
            }
            if (openId != null && CLOSE.matcher(line).find()) {
                regions.put(openId, buffer.toString());
                openId = null;
                buffer = null;
                continue;
            }
            if (buffer != null) {
                if (buffer.length() > 0) {
                    buffer.append('\n');
                }
                buffer.append(line);
            }
        }
        if (openId != null) {
            log.warn("[CustomRegion] 종료 마커 없는 구역 — 병합 생략(id={})", openId);
            return Map.of();
        }
        return regions;
    }

    /** 생성 내용의 각 구역 본문을 보존분으로 교체한다(같은 id가 있을 때만). */
    private static String replaceRegions(String generated, Map<String, String> preserved) {
        String[] lines = generated.split("\n", -1);
        StringBuilder out = new StringBuilder(generated.length() + 256);
        String openId = null;
        boolean replaced = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher open = OPEN.matcher(line);
            if (openId == null && open.find()) {
                openId = open.group(1);
                appendLine(out, line, i, lines.length);
                String body = preserved.get(openId);
                if (body != null && !body.isEmpty()) {
                    // 보존 본문을 그대로 삽입(줄 구조 유지).
                    for (String bodyLine : body.split("\n", -1)) {
                        out.append(bodyLine).append('\n');
                    }
                    replaced = true;
                }
                continue;
            }
            if (openId != null) {
                if (CLOSE.matcher(line).find()) {
                    openId = null;
                    appendLine(out, line, i, lines.length);
                }
                // 구역 안의 기존(생성) 본문은 버린다 — 보존분으로 대체되었거나 비어 있어야 한다.
                continue;
            }
            appendLine(out, line, i, lines.length);
        }
        if (openId != null) {
            log.warn("[CustomRegion] 생성 내용의 구역이 닫히지 않음 — 병합 생략");
            return generated;
        }
        return replaced ? out.toString() : generated;
    }

    /** 마지막 줄에는 개행을 덧붙이지 않는다(원본 줄 구조 보존). */
    private static void appendLine(StringBuilder out, String line, int index, int total) {
        out.append(line);
        if (index < total - 1) {
            out.append('\n');
        }
    }
}
