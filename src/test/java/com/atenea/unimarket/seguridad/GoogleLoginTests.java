package com.atenea.unimarket.seguridad;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/google-login-test.db",
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-secret",
        "spring.security.oauth2.client.registration.google.scope=openid,email,profile"
})
@AutoConfigureMockMvc
class GoogleLoginTests {

    @Autowired
    MockMvc mvc;

    @Test
    void loginPageOffersGoogleWithoutPasswordForm() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/oauth2/authorization/google")));
    }

    @Test
    void anonymousUsersCannotAccessHome() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void googleLoginRedirectsWithExpectedCallbackAndIdentityScopes() throws Exception {
        mvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("https://accounts.google.com/")))
                .andExpect(header().string("Location", containsString("client_id=test-client")))
                .andExpect(header().string("Location", containsString("redirect_uri=http://localhost/login/oauth2/code/google")))
                .andExpect(header().string("Location", containsString("openid")));
    }

    @Test
    void authenticatedHomeEscapesProfileValues() throws Exception {
        mvc.perform(get("/").with(oidcLogin().idToken(token -> token
                        .claim("name", "<script>alert(1)</script>")
                        .claim("email", "estudiante@unal.edu.co"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(containsString("estudiante@unal.edu.co")));
    }

    @Test
    void logoutRequiresCsrfToken() throws Exception {
        mvc.perform(post("/logout").with(oidcLogin()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/logout").with(oidcLogin()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));
    }
}
