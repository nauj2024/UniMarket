package com.atenea.unimarket.presentacion;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.atenea.unimarket.negocio.NotificacionesActualizadas;

@Component
@EnableScheduling
public class NotificacionesEnVivo {

    private record Conexion(Integer usuarioId, SseEmitter emisor) {}

    private final Set<Conexion> conexiones = ConcurrentHashMap.newKeySet();

    public SseEmitter conectar(Integer usuarioId) {
        // Reauthenticate on reconnect, rather than keeping an old session open indefinitely.
        SseEmitter emisor = new SseEmitter(60_000L);
        Conexion conexion = new Conexion(usuarioId, emisor);
        conexiones.add(conexion);
        emisor.onCompletion(() -> conexiones.remove(conexion));
        emisor.onTimeout(() -> { conexiones.remove(conexion); emisor.complete(); });
        emisor.onError(error -> conexiones.remove(conexion));
        enviar(conexion, SseEmitter.event().name("conectado").data("notificaciones"));
        return emisor;
    }

    @TransactionalEventListener
    public void actualizar(NotificacionesActualizadas evento) {
        conexiones.stream().filter(c -> c.usuarioId().equals(evento.usuarioId()))
                .forEach(c -> enviar(c, SseEmitter.event().name("cambio").data("notificaciones")));
    }

    @Scheduled(fixedDelay = 15_000)
    public void mantenerConexion() {
        conexiones.forEach(c -> enviar(c, SseEmitter.event().comment("activo")));
    }

    private void enviar(Conexion conexion, SseEmitter.SseEventBuilder evento) {
        try {
            conexion.emisor().send(evento);
        } catch (IOException | IllegalStateException desconectado) {
            conexiones.remove(conexion);
            conexion.emisor().complete();
        }
    }
}
