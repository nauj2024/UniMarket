package com.atenea.unimarket.presentacion.controller;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    private final ObjectProvider<ClientRegistrationRepository> registrations;

    public LoginController(ObjectProvider<ClientRegistrationRepository> registrations) {
        this.registrations = registrations;
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("googleConfigured", registrations.getIfAvailable() != null);
        return "login";
    }

    @GetMapping("/")
    public String inicio(@AuthenticationPrincipal OidcUser user, Model model) {
        model.addAttribute("nombre", user.getFullName());
        model.addAttribute("correo", user.getEmail());
        return "inicio";
    }
}
