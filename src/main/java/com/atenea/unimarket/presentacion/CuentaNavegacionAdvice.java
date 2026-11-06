package com.atenea.unimarket.presentacion;

import java.util.Locale;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.atenea.unimarket.datos.modelo.Usuario;
import com.atenea.unimarket.negocio.UsuarioService;

@ControllerAdvice
class CuentaNavegacionAdvice {

    private final UsuarioService usuarios;

    CuentaNavegacionAdvice(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    record Cuenta(String nombre, String correo, String iniciales) {}

    @ModelAttribute("cuentaNavegacion")
    Cuenta cuenta(@AuthenticationPrincipal OidcUser oidc) {
        if (oidc == null) {
            return null;
        }
        String nombre = usuarios.buscarPorGoogleSub(oidc.getSubject())
                .map(Usuario::getNombre).orElse("Mi cuenta");
        String[] partes = nombre.strip().split("\\s+");
        String iniciales = inicial(partes[0]);
        if (partes.length > 1) {
            iniciales += inicial(partes[partes.length - 1]);
        }
        return new Cuenta(nombre, oidc.getEmail(), iniciales.toUpperCase(Locale.ROOT));
    }

    private static String inicial(String palabra) {
        return palabra.isEmpty() ? "U" : new String(Character.toChars(palabra.codePointAt(0)));
    }
}
