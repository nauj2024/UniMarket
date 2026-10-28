package com.atenea.unimarket.seguridad;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnalOidcUserServiceTests {

    @Test
    void acceptsVerifiedUnalAccount() {
        OidcUser user = user("estudiante@unal.edu.co", true, "unal.edu.co");
        assertThat(load(user)).isSameAs(user);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "estudiante@gmail.com,true,NULL",
            "estudiante@unal.edu.co.evil.com,true,unal.edu.co",
            "estudiante@sub.unal.edu.co,true,unal.edu.co",
            "estudiante@@unal.edu.co,true,unal.edu.co",
            "estudiante@unal.edu.co,false,unal.edu.co",
            "estudiante@unal.edu.co,NULL,unal.edu.co",
            "estudiante@unal.edu.co,true,NULL",
            "estudiante@unal.edu.co,true,otra.edu.co",
            "NULL,true,unal.edu.co"
    }, nullValues = "NULL")
    void rejectsAccountsThatDoNotMeetInstitutionalRequirements(String email, Boolean verified, String domain) {
        OidcUser user = user(email, verified, domain);
        assertThatThrownBy(() -> load(user))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(exception -> assertThat(((OAuth2AuthenticationException) exception)
                        .getError().getErrorCode()).isEqualTo("unal_account_required"));
    }

    private OidcUser load(OidcUser user) {
        @SuppressWarnings("unchecked")
        OAuth2UserService<OidcUserRequest, OidcUser> delegate = mock(OAuth2UserService.class);
        OidcUserRequest request = mock(OidcUserRequest.class);
        when(delegate.loadUser(request)).thenReturn(user);
        return new UnalOidcUserService(delegate).loadUser(request);
    }

    private OidcUser user(String email, Boolean verified, String domain) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "google-user-123");
        if (email != null) claims.put("email", email);
        if (verified != null) claims.put("email_verified", verified);
        if (domain != null) claims.put("hd", domain);
        Instant now = Instant.now();
        OidcIdToken token = new OidcIdToken("test-token", now, now.plusSeconds(300), claims);
        return new DefaultOidcUser(List.of(), token);
    }
}
