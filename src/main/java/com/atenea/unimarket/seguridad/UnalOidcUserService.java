package com.atenea.unimarket.seguridad;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public class UnalOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;

    public UnalOidcUserService(OAuth2UserService<OidcUserRequest, OidcUser> delegate) {
        this.delegate = delegate;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        OidcUser user = delegate.loadUser(request);
        // Spring Security validates the ID token before calling this service.
        OidcIdToken token = user.getIdToken();
        String email = token.getEmail();
        boolean unalEmail = email != null && email.matches("(?i)[^@\\s]+@unal\\.edu\\.co");

        if (!Boolean.TRUE.equals(token.getEmailVerified())
                || !unalEmail
                || !"unal.edu.co".equals(token.getClaimAsString("hd"))) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("unal_account_required"),
                    "Debes ingresar con una cuenta institucional de la UNAL.");
        }

        return user;
    }
}
