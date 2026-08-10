package com.jworks.forge.gen.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import freemarker.template.Configuration;

/**
 * P4-1 스모크: 코드생성 전용 Configuration + 렌더러가 실제로 렌더하고,
 * 미존재 템플릿/렌더 오류가 조용히 삼켜지지 않고 예외로 전파되는지 확인.
 * (Spring 컨텍스트 없이 순수 배선만 검증 — 빌더 JSP 뷰와 분리된 인스턴스.)
 */
class TemplateRendererTest {

    private final Configuration cfg = new CodeGenTemplateConfig().codeGenFreemarkerConfiguration();
    private final TemplateRenderer renderer = new TemplateRenderer(cfg);

    @Test
    void 스모크_hello_템플릿이_stem을_렌더한다() {
        String out = renderer.render("smoke/hello", Map.of("stem", "userMgmt"));
        assertEquals("userMgmt", out.trim());
    }

    @Test
    void 미존재_템플릿은_TemplateRenderException으로_전파된다() {
        assertThrows(TemplateRenderException.class,
                () -> renderer.render("smoke/does-not-exist", Map.of()));
    }

    @Test
    void 렌더오류는_RETHROW로_전파된다_모델_누락시() {
        // hello.ftl은 ${stem}을 요구 — 모델에 stem이 없으면 RETHROW_HANDLER가 예외를 던진다.
        assertThrows(TemplateRenderException.class,
                () -> renderer.render("smoke/hello", Map.of()));
    }
}
