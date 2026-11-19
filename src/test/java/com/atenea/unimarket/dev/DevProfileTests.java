package com.atenea.unimarket.dev;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.atenea.unimarket.datos.repositorio.ServicioRepository;
import com.atenea.unimarket.datos.repositorio.UsuarioRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:target/dev-profile-test.db")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DevProfileTests {

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired ServicioRepository servicios;
    @Autowired DevData data;

    @Test
    void offersSimulatedAccountsWithoutGoogle() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ana Rodríguez")))
                .andExpect(content().string(containsString("/dev/login")))
                .andExpect(content().string(not(containsString("/oauth2/authorization/google"))));
    }

    @Test
    void fixturesAreLoadedOnce() {
        assertThat(usuarios.count()).isEqualTo(3);
        assertThat(servicios.count()).isEqualTo(8);
        data.run(new DefaultApplicationArguments());
        assertThat(usuarios.count()).isEqualTo(3);
        assertThat(servicios.count()).isEqualTo(8);
    }

    @Test
    void loginPersistsIdentityAndEnforcesOwnershipAfterSwitching() throws Exception {
        MockHttpSession session = login("demo-ana");
        mvc.perform(get("/").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Ana Rodríguez")));
        Integer anaId = usuarios.findByGoogleSub("demo-ana").orElseThrow().getId();
        Integer servicio = servicios.findAll().stream()
                .filter(s -> s.getOfertador().getId().equals(anaId))
                .findFirst().orElseThrow().getId();
        mvc.perform(get("/servicios/" + servicio + "/editar").session(session))
                .andExpect(status().isOk());
        mvc.perform(post("/dev/login").session(session).with(csrf()).param("subject", "demo-carlos"))
                .andExpect(redirectedUrl("/"));
        mvc.perform(get("/").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Carlos Gómez")));
        mvc.perform(get("/servicios/" + servicio + "/editar").session(session))
                .andExpect(status().isForbidden());
        mvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(redirectedUrl("/login?logout"));
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void navigationKeepsSignedInIdentityWhenViewingAnotherProfile() throws Exception {
        MockHttpSession session = login("demo-ana");
        Integer carlosId = usuarios.findByGoogleSub("demo-carlos").orElseThrow().getId();
        String html = mvc.perform(get("/perfiles/" + carlosId).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String cuenta = html.substring(html.indexOf("id=\"cuenta-menu\""), html.indexOf("</form>"));
        assertThat(html).contains(">AR</span>");
        assertThat(cuenta).contains("Ana Rodríguez", "action=\"/logout\"", "method=\"post\"", "name=\"_csrf\"");
        assertThat(cuenta).doesNotContain("Carlos Gómez");
    }

    @Test
    void newIdentityMustCompleteProfileWithoutCreatingAnAccountAtLogin() throws Exception {
        MockHttpSession session = login("dev-nuevo");
        mvc.perform(get("/").session(session)).andExpect(redirectedUrl("/perfil/nuevo"));
        mvc.perform(get("/perfil/nuevo").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("nuevo.dev@unal.edu.co")))
                .andExpect(content().string(not(containsString("aria-label=\"Principal\""))))
                .andExpect(content().string(containsString("action=\"/logout\"")));
        assertThat(usuarios.existsByGoogleSub("dev-nuevo")).isFalse();
        mvc.perform(post("/dev/login").session(session).with(csrf()).param("subject", "demo-laura"))
                .andExpect(redirectedUrl("/"));
        mvc.perform(get("/").session(session)).andExpect(status().isOk());
    }

    @Test
    void rejectsUnknownIdentitiesAndMissingCsrf() throws Exception {
        mvc.perform(post("/dev/login").param("subject", "demo-ana"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/dev/login").with(csrf()).param("subject", "arbitrary-sub"))
                .andExpect(status().isBadRequest());
    }

    private MockHttpSession login(String subject) throws Exception {
        return (MockHttpSession) mvc.perform(post("/dev/login").with(csrf()).param("subject", subject))
                .andExpect(redirectedUrl("/"))
                .andReturn().getRequest().getSession(false);
    }
}
