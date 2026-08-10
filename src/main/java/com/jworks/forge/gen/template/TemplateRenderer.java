package com.jworks.forge.gen.template;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateNotFoundException;

/**
 * 코드생성 전용 FreeMarker 렌더러 (P4-1).
 *
 * <p>{@code templateKey}(예: {@code module/tableView},
 * {@code archetype/mgmtListDetail/shell})에 {@code .ftl}만 접미하여
 * {@link CodeGenTemplateConfig} Configuration에서 템플릿을 해석·렌더한다.
 *
 * <p><b>이 클래스는 문자열을 조립·평가하지 않는다.</b> {@code templateKey}는
 * 호출자가 <b>맵 조회(TEMPLATE_KEY 화이트리스트, 계약 §4.4)</b>로 얻은 값이며,
 * 렌더러는 여기에 {@code .ftl} 접미사만 붙인다. 템플릿 미존재·렌더 오류는
 * {@link TemplateRenderException}으로 감싸 전파한다(조용히 삼키지 않음).
 */
@Component
public class TemplateRenderer {

    private static final String FTL_SUFFIX = ".ftl";

    private final Configuration configuration;

    public TemplateRenderer(
            @Qualifier("codeGenFreemarkerConfiguration") Configuration configuration) {
        this.configuration = configuration;
    }

    /**
     * {@code templateKey}에 해당하는 템플릿을 {@code model}로 렌더한다.
     *
     * @param templateKey 화이트리스트 통과값(예: {@code smoke/hello}). {@code .ftl}은 붙이지 않은 상태
     * @param model       FreeMarker 데이터 모델(데이터 맵만 — 코드 평가 없음)
     * @return 렌더 결과 문자열
     * @throws TemplateRenderException 템플릿 미존재·파싱·렌더 실패 시
     */
    public String render(String templateKey, Map<String, Object> model) {
        if (templateKey == null || templateKey.isBlank()) {
            throw new TemplateRenderException("templateKey가 비어 있다", null);
        }
        String templateName = templateKey + FTL_SUFFIX;
        try {
            Template template = configuration.getTemplate(templateName);
            StringWriter out = new StringWriter();
            template.process(model, out);
            return out.toString();
        } catch (TemplateNotFoundException e) {
            throw new TemplateRenderException("템플릿을 찾을 수 없다: " + templateName, e);
        } catch (TemplateException | IOException e) {
            throw new TemplateRenderException("템플릿 렌더 실패: " + templateName, e);
        }
    }
}
