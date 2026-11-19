package com.atenea.unimarket.presentacion.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.atenea.unimarket.negocio.UsuarioService;

/**
 * Shows the data treatment policy. It is excluded from the mandatory-profile redirect so that a
 * new user can read it before accepting it in the profile form.
 */
@Controller
public class PoliticaDatosController {

    public static final String RUTA = "/politica-datos";

    private final UsuarioService usuarios;

    public PoliticaDatosController(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    @GetMapping(RUTA)
    public String ver(@AuthenticationPrincipal OidcUser oidc, Model model) {
        boolean registrado = oidc != null && usuarios.estaRegistrado(oidc.getSubject());
        model.addAttribute("seccion", registrado ? "politica" : "registro");
        return "politica-datos";
    }
}
