package com.atenea.unimarket.presentacion.controller;

import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.atenea.unimarket.negocio.NotificacionService;
import com.atenea.unimarket.negocio.UsuarioService;
import com.atenea.unimarket.presentacion.NotificacionesEnVivo;

@RestController
public class NotificacionController {

    private final NotificacionService notificaciones;
    private final UsuarioService usuarios;
    private final NotificacionesEnVivo enVivo;

    public NotificacionController(NotificacionService notificaciones, UsuarioService usuarios,
            NotificacionesEnVivo enVivo) {
        this.notificaciones = notificaciones;
        this.usuarios = usuarios;
        this.enVivo = enVivo;
    }

    @GetMapping("/api/notificaciones")
    public NotificacionService.Bandeja bandeja(@AuthenticationPrincipal OidcUser oidc,
            @RequestParam(defaultValue = "false") boolean historial) {
        return notificaciones.bandeja(usuarioId(oidc), historial);
    }

    @GetMapping(value = "/api/notificaciones/eventos", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter eventos(@AuthenticationPrincipal OidcUser oidc) {
        return enVivo.conectar(usuarioId(oidc));
    }

    @PostMapping("/api/notificaciones/{id}/leer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leer(@AuthenticationPrincipal OidcUser oidc, @PathVariable Integer id) {
        try {
            notificaciones.leer(usuarioId(oidc), id);
        } catch (NoSuchElementException noExiste) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping("/api/notificaciones/{id}/descartar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void descartar(@AuthenticationPrincipal OidcUser oidc, @PathVariable Integer id,
            @RequestParam(defaultValue = "true") boolean descartada) {
        try {
            notificaciones.descartar(usuarioId(oidc), id, descartada);
        } catch (NoSuchElementException noExiste) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private Integer usuarioId(OidcUser oidc) {
        return usuarios.buscarPorGoogleSub(oidc.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN)).getId();
    }
}
