package com.atenea.unimarket.negocio;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.atenea.unimarket.datos.modelo.Notificacion;
import com.atenea.unimarket.datos.modelo.TipoNotificacion;
import com.atenea.unimarket.datos.repositorio.NotificacionRepository;

@Service
public class NotificacionService {

    private final NotificacionRepository notificaciones;
    private final ApplicationEventPublisher eventos;

    public NotificacionService(NotificacionRepository notificaciones, ApplicationEventPublisher eventos) {
        this.notificaciones = notificaciones;
        this.eventos = eventos;
    }

    public record Aviso(Integer id, Integer solicitudId, String mensaje, TipoNotificacion tipo,
            Instant fechaCreacion, boolean leida, boolean descartada) {
        static Aviso de(Notificacion n) {
            return new Aviso(n.getId(), n.getSolicitud().getId(), n.getMensaje(), n.getTipo(),
                    n.getFechaCreacion(), n.isLeida(), n.isDescartada());
        }
    }

    public record Bandeja(List<Aviso> notificaciones, long sinLeer) {}

    /** The user's full history, including notifications dismissed from the dropdown. */
    @Transactional(readOnly = true)
    public List<Notificacion> deUsuario(Integer usuarioId) {
        return notificaciones.findByUsuarioIdOrderByFechaCreacionDescIdDesc(usuarioId);
    }

    @Transactional(readOnly = true)
    public Bandeja bandeja(Integer usuarioId, boolean historial) {
        List<Notificacion> lista = historial
                ? notificaciones.findByUsuarioIdOrderByFechaCreacionDescIdDesc(usuarioId)
                : notificaciones.findTop3ByUsuarioIdAndDescartadaFalseOrderByFechaCreacionDescIdDesc(usuarioId);
        return new Bandeja(lista.stream().map(Aviso::de).toList(),
                notificaciones.countByUsuarioIdAndLeidaFalseAndDescartadaFalse(usuarioId));
    }

    @Transactional
    public void leer(Integer usuarioId, Integer id) {
        comprobar(notificaciones.marcarLeida(id, usuarioId));
        eventos.publishEvent(new NotificacionesActualizadas(usuarioId));
    }

    @Transactional
    public void descartar(Integer usuarioId, Integer id, boolean descartada) {
        comprobar(notificaciones.descartar(id, usuarioId, descartada));
        eventos.publishEvent(new NotificacionesActualizadas(usuarioId));
    }

    private void comprobar(int actualizadas) {
        if (actualizadas == 0) {
            throw new NoSuchElementException("Notificación no encontrada.");
        }
    }
}
