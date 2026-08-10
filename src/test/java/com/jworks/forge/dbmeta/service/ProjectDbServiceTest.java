package com.jworks.forge.dbmeta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.jworks.forge.dbmeta.SecretCipher;
import com.jworks.forge.dbmeta.domain.ProjectDbConnection;
import com.jworks.forge.dbmeta.dto.DbMetaDtos.ConnectionRequest;
import com.jworks.forge.dbmeta.dto.DbMetaDtos.ConnectionView;
import com.jworks.forge.dbmeta.mapper.ProjectDbConnectionMapper;
import com.jworks.forge.project.domain.ForgeProject;
import com.jworks.forge.project.service.ForgeProjectService;

/** 🔒 P11: 접속정보가 암호화되어 저장되고, 비밀번호가 어떤 응답에도 실리지 않는지 고정한다. */
class ProjectDbServiceTest {

    private ProjectDbConnectionMapper mapper;
    private ForgeProjectService projects;
    private DbIntrospectionService introspection;
    private SecretCipher cipher;
    private ProjectDbService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ProjectDbConnectionMapper.class);
        projects = mock(ForgeProjectService.class);
        introspection = mock(DbIntrospectionService.class);
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        cipher = new SecretCipher(Base64.getEncoder().encodeToString(key));
        service = new ProjectDbService(mapper, projects, cipher, introspection);
        when(projects.get(anyLong())).thenReturn(new ForgeProject());
    }

    @Test
    void 저장_시_비밀번호는_암호화되고_응답에는_실리지_않는다() {
        ConnectionView view = service.save(1L,
                new ConnectionRequest("db.internal", 5432, "app_db", "public", "app_reader", "p@ssw0rd"));

        ArgumentCaptor<ProjectDbConnection> captor = ArgumentCaptor.forClass(ProjectDbConnection.class);
        verify(mapper).upsert(captor.capture());
        ProjectDbConnection saved = captor.getValue();

        assertNotEquals("p@ssw0rd", saved.getDbPasswordEnc(), "평문 저장 금지");
        assertFalse(saved.getDbPasswordEnc().contains("p@ssw0rd"));
        assertEquals("p@ssw0rd", cipher.decrypt(saved.getDbPasswordEnc()), "복호하면 원문");
        assertEquals("app_reader", saved.getDbUsername());

        assertTrue(view.configured());
        assertEquals("db.internal", view.host());
        // ConnectionView에는 비밀번호 필드 자체가 없다(구조적 보장).
    }

    @Test
    void 비밀번호를_비우고_저장하면_기존_값을_유지한다() {
        ProjectDbConnection existing = new ProjectDbConnection();
        existing.setDbPasswordEnc(cipher.encrypt("old-secret"));
        when(mapper.selectByProject(1L)).thenReturn(existing);

        service.save(1L, new ConnectionRequest("db", 5432, "app", "public", "reader", ""));

        ArgumentCaptor<ProjectDbConnection> captor = ArgumentCaptor.forClass(ProjectDbConnection.class);
        verify(mapper).upsert(captor.capture());
        assertEquals("old-secret", cipher.decrypt(captor.getValue().getDbPasswordEnc()));
    }

    @Test
    void 저장된_접속정보가_없으면_스키마_조회를_거부한다() {
        when(mapper.selectByProject(1L)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service.tables(1L, null));
        assertThrows(IllegalArgumentException.class, () -> service.columns(1L, "TB_USER"));
    }

    @Test
    void 미설정_프로젝트는_configured_false로_응답한다() {
        when(mapper.selectByProject(1L)).thenReturn(null);

        ConnectionView view = service.get(1L);

        assertFalse(view.configured());
        assertTrue(view.secretAvailable());
    }

    @Test
    void 악성_접속_좌표는_저장_단계에서_거부된다() {
        assertThrows(IllegalArgumentException.class, () -> service.save(1L,
                new ConnectionRequest("db?socketFactory=evil", 5432, "app", "public", "reader", "pw")));
        assertThrows(IllegalArgumentException.class, () -> service.save(1L,
                new ConnectionRequest("db", 5432, "app", "public; --", "reader", "pw")));
        verify(mapper, org.mockito.Mockito.never()).upsert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 암호화_키가_없으면_기능이_비활성된다() {
        SecretCipher unavailable = mock(SecretCipher.class);
        when(unavailable.isAvailable()).thenReturn(false);
        var disabled = new ProjectDbService(mapper, projects, unavailable, introspection);

        assertThrows(IllegalArgumentException.class, () -> disabled.save(1L,
                new ConnectionRequest("db", 5432, "app", "public", "reader", "pw")));
        assertThrows(IllegalArgumentException.class, () -> disabled.tables(1L, null));
    }
}
