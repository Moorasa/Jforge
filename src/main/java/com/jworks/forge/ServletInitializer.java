package com.jworks.forge;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * 외부 서블릿 컨테이너(WAR 배포)로 기동할 때의 초기화 훅.
 * 내장 Tomcat 실행에는 영향 없음.
 */
public class ServletInitializer extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(ForgeApplication.class);
    }
}
