package com.atenea.unimarket.negocio;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationEventPublisher;

import com.atenea.unimarket.datos.modelo.Notificacion;
import com.atenea.unimarket.datos.modelo.SolicitudCompra;
import com.atenea.unimarket.datos.modelo.TipoNotificacion;
import com.atenea.unimarket.datos.modelo.Usuario;
import com.atenea.unimarket.datos.repositorio.NotificacionRepository;

/**
 * Stores each notification so the user finds it in the inbox. It joins the caller's
 * transaction, so a notification exists only if the change it reports was saved.
 */
@Component
class NotificadorPersistente implements Notificador {

    private final NotificacionRepository notificaciones;
    private final Clock reloj;
    private final ApplicationEventPublisher eventos;

    NotificadorPersistente(NotificacionRepository notificaciones, Clock reloj, ApplicationEventPublisher eventos) {
        this.notificaciones = notificaciones;
        this.reloj = reloj;
        this.eventos = eventos;
    }

    @Override
    public void notificar(Usuario destinatario, SolicitudCompra solicitud, TipoNotificacion tipo, String mensaje) {
        notificaciones.save(new Notificacion(destinatario, solicitud, tipo, mensaje, Instant.now(reloj)));
        eventos.publishEvent(new NotificacionesActualizadas(destinatario.getId()));
    }
}
