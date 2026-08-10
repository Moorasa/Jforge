package com.jworks.forge.gen.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;

import org.junit.jupiter.api.Test;

import freemarker.template.Configuration;

/**
 * 🔒 P4-2 스모크: GenEscaper 4함수가 코드생성 Configuration에 공유변수로 등록되어
 * 템플릿에서 {@code ${jsString(...)}} 등으로 호출 가능하고, 결과가 이스케이프됨을 확인.
 * P4-1의 위험 빌트인 차단 설정을 훼손하지 않고 '추가만' 됐음도 간접 확인(렌더 정상).
 */
class GenEscaperFreemarkerTest {

    private final Configuration cfg = new CodeGenTemplateConfig().codeGenFreemarkerConfiguration();
    private final TemplateRenderer renderer = new TemplateRenderer(cfg);

    @Test
    void jsString이_템플릿에서_호출되어_스크립트종료를_차단한다() {
        String out = renderer.render("smoke/escape", Map.of("val", "</script>")).trim();
        assertEquals("\\x3C\\/script>", out);
        assertFalse(out.contains("</"), "렌더 결과에 원문 </ 가 남으면 안 된다");
    }
}
