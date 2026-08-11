package com.jworks.forge.gen.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jworks.forge.gen.context.TemplateContextBuilder;
import com.jworks.forge.gen.safety.PathSafetyService;
import com.jworks.forge.gen.template.CodeGenTemplateConfig;
import com.jworks.forge.gen.template.TemplateRenderer;
import com.jworks.forge.project.domain.ForgeProject;
import com.jworks.forge.project.service.ForgeProjectService;
import com.jworks.forge.screen.domain.ForgeScreen;
import com.jworks.forge.screen.service.ForgeScreenService;

class PopupFormGenerationTest {

    @TempDir Path targetRoot;

    private static final String JSON = """
            {"schemaVersion":1,"archetype":"POPUP","stem":"userAdd","role":"admin","slots":{
              "popupBody":[{"instanceId":"popup_1","moduleTypeCode":"POPUP_FORM","props":{
                "popupTitle":"사용자 추가","bodyTitle":"기본 정보","size":"small",
                "confirmText":"저장","cancelYn":true,
                "fields":[{"name":"userName","label":"사용자명","type":"text","requiredYn":true}]
              }}]}}
            """;

    @Test
    void POPUP은_JSP_JS_CSS_3종을_생성한다() throws Exception {
        GenResult result = generator().generate(10L);
        assertEquals(GenResult.SUCCESS, result.resultCode(), result.failReason());
        String jsp = read("jsp/admin/userAdd/userAdd.jsp");
        String js = read("js/admin/userAdd/userAdd.js");
        assertTrue(jsp.contains("class=\"overlay-popup popup-size-small\""));
        assertTrue(jsp.contains("사용자 추가"));
        assertTrue(jsp.contains("name=\"userName\""));
        assertTrue(js.contains("window.JWorks_JSUserAddPopup"));
        assertTrue(Files.exists(targetRoot.resolve("css/admin/userAdd/userAdd.css")));
        assertFalse(jsp.contains("<script>alert"));
    }

    private ScreenGenerator generator() {
        ForgeScreenService screens = mock(ForgeScreenService.class);
        ForgeProjectService projects = mock(ForgeProjectService.class);
        ForgeScreen screen = new ForgeScreen();
        screen.setScreenId(10L); screen.setProjectId(1L); screen.setStem("userAdd");
        screen.setRoleCode("admin"); screen.setArchetypeCode("POPUP"); screen.setDefinitionJson(JSON);
        ForgeProject project = new ForgeProject();
        project.setProjectId(1L); project.setTargetRootPath(targetRoot.toString());
        project.setPackageBase("com.jworks.forge"); project.setJspBasePath("jsp");
        project.setJsBasePath("js"); project.setCssBasePath("css");
        when(screens.get(10L)).thenReturn(screen); when(projects.get(1L)).thenReturn(project);
        PathSafetyService safety = new PathSafetyService();
        AtomicFileWriter writer = new AtomicFileWriter();
        return new ScreenGenerator(screens, projects,
                new TemplateContextBuilder(new ObjectMapper()),
                new TemplateRenderer(new CodeGenTemplateConfig().codeGenFreemarkerConfiguration()),
                safety, writer, new RuntimeSyncer(safety, writer), new StubGenerator(safety, writer));
    }

    private String read(String relative) throws Exception {
        return Files.readString(targetRoot.resolve(relative), StandardCharsets.UTF_8);
    }
}
