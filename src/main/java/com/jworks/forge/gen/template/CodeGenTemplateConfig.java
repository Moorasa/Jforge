package com.jworks.forge.gen.template;

import java.util.List;
import java.util.function.UnaryOperator;

import org.springframework.context.annotation.Bean;

import com.jworks.forge.gen.context.GenEscaper;

import freemarker.core.TemplateClassResolver;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;

/**
 * 코드생성 <b>전용</b> FreeMarker {@link Configuration} 빈 (P4-1).
 *
 * <p>빌더 자신의 뷰는 JSP다(도그푸딩). 이 Configuration은 <b>생성 엔진이 타겟 3종
 * (JSP/JS/CSS)을 렌더</b>하기 위한 별도 인스턴스로, Spring MVC 뷰리졸버와는 완전히
 * 분리된다(spring-boot-starter-freemarker 미도입). 템플릿은 클래스패스
 * {@code /templates/gen/} 하위의 신뢰된 내부 {@code .ftl}만 로딩한다.
 *
 * <p>보안(계약 §2.3 필수): {@code ?new}(임의 클래스 인스턴스화)·{@code ?api} 등
 * 문자열→코드 승격 빌트인을 엔진 차원에서 차단한다.
 */
@org.springframework.context.annotation.Configuration
public class CodeGenTemplateConfig {

    /** 템플릿 루트(클래스패스). TEMPLATE_KEY는 이 하위 상대경로로 해석된다. */
    static final String TEMPLATE_BASE_PATH = "/templates/gen";

    /**
     * 코드생성 전용 FreeMarker Configuration.
     *
     * @return 빌더 JSP 뷰와 분리된 별도 Configuration 인스턴스
     */
    @Bean("codeGenFreemarkerConfiguration")
    public Configuration codeGenFreemarkerConfiguration() {
        // 동작을 고정하기 위해 버전을 명시한다(FreeMarker incompatible_improvements).
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_34);

        // 템플릿 로딩: 클래스패스 /templates/gen/ 하위. 빌더 JSP 뷰 경로와 분리.
        cfg.setClassLoaderForTemplateLoading(getClass().getClassLoader(),
                TEMPLATE_BASE_PATH.substring(1)); // 선행 슬래시 제거(classpath 상대)
        cfg.setDefaultEncoding("UTF-8");

        // 생성 실패를 조용히 삼키지 않는다 — 렌더 오류는 그대로 전파.
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false); // RETHROW와 병행(중복 로깅 억제)
        cfg.setWrapUncheckedExceptions(true);

        // 천단위 콤마 오염 방지 — 숫자는 컴퓨터 포맷(예: 1000, 1000.5)으로 출력.
        cfg.setNumberFormat("computer");

        // 🔒 계약 §2.3 위험 빌트인 차단(필수).
        //  - ?new(임의 클래스 인스턴스화) 차단.
        cfg.setNewBuiltinClassResolver(TemplateClassResolver.ALLOWS_NOTHING_RESOLVER);
        //  - ?api(내부 API 노출) 차단.
        cfg.setAPIBuiltinEnabled(false);

        // 출력포맷/자동이스케이프는 여기서 강제하지 않는다.
        // 계약 §3은 문맥별 명시 이스케이프(P4-2 GenEscaper: htmlText/htmlAttr/jsString/cssToken)로
        // 처리하므로 plain(PLAIN_TEXT_OUTPUT_FORMAT)으로 둔다. HTML 자동이스케이프 도입 여부는
        // P4-2/P4-3에서 명시 이스케이프 전제와 함께 결정한다.

        // 🔒 P4-2: 문맥별 이스케이프 함수를 공유변수로 등록해 템플릿에서 ${jsString(...)} 등으로 호출.
        //  위험 빌트인 차단 설정(위)은 그대로 두고 여기서는 안전 함수만 '추가'한다.
        registerEscapers(cfg);

        return cfg;
    }

    /**
     * 🔒 {@link GenEscaper}의 4개 순수 함수를 FreeMarker 공유변수로 등록한다(계약 §3.2).
     * 각 함수는 단일 문자열 인자를 받아 이스케이프 결과를 돌려주는 {@link TemplateMethodModelEx}
     * 어댑터로 노출된다 — 템플릿에서 {@code ${htmlText(x)}} / {@code ${htmlAttr(x)}} /
     * {@code ${jsString(x)}} / {@code ${cssToken(x)}} 로 호출한다.
     */
    private static void registerEscapers(Configuration cfg) {
        cfg.setSharedVariable("htmlText", new EscapeMethod(GenEscaper::htmlText));
        cfg.setSharedVariable("htmlAttr", new EscapeMethod(GenEscaper::htmlAttr));
        cfg.setSharedVariable("jsString", new EscapeMethod(GenEscaper::jsString));
        cfg.setSharedVariable("cssToken", new EscapeMethod(GenEscaper::cssToken));
    }

    /**
     * 단일 문자열 인자를 받아 {@link GenEscaper} 함수를 적용하는 FreeMarker 메서드 어댑터.
     * null 인자는 GenEscaper가 빈 문자열로 처리하므로 여기서도 null 전달을 허용한다.
     */
    private static final class EscapeMethod implements TemplateMethodModelEx {
        private final UnaryOperator<String> fn;

        EscapeMethod(UnaryOperator<String> fn) {
            this.fn = fn;
        }

        @Override
        public Object exec(@SuppressWarnings("rawtypes") List args) throws TemplateModelException {
            if (args == null || args.size() != 1) {
                throw new TemplateModelException("이스케이프 함수는 인자 1개를 받는다(전달="
                        + (args == null ? 0 : args.size()) + ")");
            }
            Object arg = args.get(0);
            // TemplateModel(SimpleScalar 등)/원시 문자열/null 모두 문자열로 정규화.
            String s = (arg == null) ? null : arg.toString();
            return fn.apply(s);
        }
    }
}
