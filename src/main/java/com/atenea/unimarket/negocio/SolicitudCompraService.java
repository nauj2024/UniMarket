package com.atenea.unimarket.negocio;

import java.text.NumberFormat;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.atenea.unimarket.datos.modelo.EstadoServicio;
import com.atenea.unimarket.datos.modelo.EstadoSolicitud;
import com.atenea.unimarket.datos.modelo.Propuesta;
import com.atenea.unimarket.datos.modelo.Servicio;
import com.atenea.unimarket.datos.modelo.SolicitudCompra;
import com.atenea.unimarket.datos.modelo.TipoNotificacion;
import com.atenea.unimarket.datos.modelo.Usuario;
import com.atenea.unimarket.datos.repositorio.PropuestaRepository;
import com.atenea.unimarket.datos.repositorio.ServicioRepository;
import com.atenea.unimarket.datos.repositorio.SolicitudCompraRepository;
import com.atenea.unimarket.datos.repositorio.UsuarioRepository;

@Service
public class SolicitudCompraService {

    private static final Locale COLOMBIA = Locale.forLanguageTag("es-CO");

    private final SolicitudCompraRepository solicitudes;
    private final PropuestaRepository propuestas;
    private final ServicioRepository servicios;
    private final UsuarioRepository usuarios;
    private final Notificador notificador;
    private final Clock reloj;

    public SolicitudCompraService(SolicitudCompraRepository solicitudes, PropuestaRepository propuestas,
            ServicioRepository servicios, UsuarioRepository usuarios, Notificador notificador, Clock reloj) {
        this.solicitudes = solicitudes;
        this.propuestas = propuestas;
        this.servicios = servicios;
        this.usuarios = usuarios;
        this.notificador = notificador;
        this.reloj = reloj;
    }

    /**
     * COMPRA-CLI-H01 / HU-04: the client requests an available service. The request starts
     * NEGOCIANDO with a first-round proposal by the client, and the offerer is notified. The
     * weekly hours are always the published ones: only the offerer can change them. Without a
     * rate the client takes the published conditions as they are.
     */
    @Transactional
    public SolicitudCompra crear(Integer clienteId, Integer servicioId, Integer tarifaPorHora) {
        Usuario cliente = usuarios.findById(clienteId)
                .orElseThrow(() -> new IllegalArgumentException("El cliente no existe."));
        Servicio servicio = servicios.findById(servicioId).orElseThrow(ServicioNoEncontradoException::new);
        if (servicio.getEstado() != EstadoServicio.DISPONIBLE) {
            throw new SolicitudNoPermitidaException("Este servicio ya no está disponible.");
        }
        if (servicio.getOfertador().getId().equals(clienteId)) {
            throw new SolicitudNoPermitidaException("No puedes solicitar tu propio servicio.");
        }
        if (solicitudes.existsByClienteIdAndServicioIdAndEstado(clienteId, servicioId, EstadoSolicitud.NEGOCIANDO)) {
            throw new SolicitudNoPermitidaException("Ya tienes una solicitud en negociación para este servicio.");
        }
        int tarifa = tarifaPorHora != null ? tarifaPorHora : servicio.getTarifaPorHora();
        Instant ahora = Instant.now(reloj);
        SolicitudCompra solicitud = solicitudes.save(new SolicitudCompra(cliente, servicio, ahora));
        propuestas.save(new Propuesta(solicitud, cliente, tarifa, servicio.getIntensidadHoraria(), 1, ahora));
        notificador.notificar(servicio.getOfertador(), solicitud, TipoNotificacion.SOLICITUD_RECIBIDA,
                cliente.getNombre() + " quiere contratar «" + servicio.getTitulo() + "» a " + pesos(tarifa)
                        + " por hora (" + servicio.getIntensidadHoraria() + " horas por semana).");
        return solicitud;
    }

    /** COMPRA-CLI-H02: la contraparte responde con una nueva tarifa (y horas semanales si es ofertador). */
    @Transactional
    public SolicitudCompra contraproponer(Integer solicitudId, Integer usuarioId, Integer tarifaPorHora, Integer intensidadHoraria) {
        SolicitudCompra solicitud = solicitudes.findById(solicitudId)
            .orElseThrow(SolicitudNoEncontradaException::new);

        if (solicitud.getEstado() != EstadoSolicitud.NEGOCIANDO) {
            throw new SolicitudNoPermitidaException("La solicitud no se encuentra en negociación.");
        }

        // Obtener la propuesta vigente (la última ronda)
        Propuesta ultimaPropuesta = propuestas.findFirstBySolicitudIdOrderByNumeroRondaDesc(solicitudId)
                .orElseThrow(() -> new IllegalStateException("La solicitud no contiene propuestas previas."));

        // Regla de turnos: solo la contraparte puede responder
        if (ultimaPropuesta.getAutor().getId().equals(usuarioId)) {
            throw new SolicitudNoPermitidaException("Debes esperar la respuesta de la otra persona antes de contraproponer.");
        }

        Usuario autor = usuarios.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("El usuario no existe."));

        boolean esCliente = solicitud.getCliente().getId().equals(usuarioId);

        // Si la tarifa enviada es nula, mantiene la de la última propuesta
        int tarifa = tarifaPorHora != null ? tarifaPorHora : ultimaPropuesta.getTarifaPorHora();

        // El cliente no modifica la intensidad horaria; solo el ofertador puede cambiarla
        int intensidad = esCliente 
                ? ultimaPropuesta.getIntensidadHoraria() 
                : (intensidadHoraria != null ? intensidadHoraria : ultimaPropuesta.getIntensidadHoraria());

        Instant ahora = Instant.now(reloj);

        // Crear la nueva propuesta incrementando el número de ronda
        Propuesta nuevaPropuesta = new Propuesta(
                solicitud,
                autor,
                tarifa,
                intensidad,
                ultimaPropuesta.getNumeroRonda() + 1,
                ahora
        );
        propuestas.save(nuevaPropuesta);

        // Actualizar la última fecha de movimiento en la solicitud
        solicitud.registrarActividad(ahora);
        solicitudes.save(solicitud);
        Usuario destinatario = esCliente ? solicitud.getServicio().getOfertador() : solicitud.getCliente();
        notificador.notificar(destinatario, solicitud, TipoNotificacion.CONTRAPROPUESTA_RECIBIDA,
                autor.getNombre() + " envió una contrapropuesta para «" + solicitud.getServicio().getTitulo()
                        + "» a " + pesos(tarifa) + " por hora (" + intensidad + " horas por semana).");
        return solicitud;
    }

    /** La contraparte acepta la propuesta vigente: la solicitud y el servicio pasan a estar ACTIVA/ACTIVO. */
    @Transactional
    public SolicitudCompra aceptar(Integer solicitudId, Integer usuarioId) {
        SolicitudCompra solicitud = solicitudes.findById(solicitudId)
                .orElseThrow(SolicitudNoEncontradaException::new);

        if (solicitud.getEstado() != EstadoSolicitud.NEGOCIANDO) {
            throw new SolicitudNoPermitidaException("La solicitud no se encuentra en negociación.");
        }

        Propuesta ultimaPropuesta = propuestas.findFirstBySolicitudIdOrderByNumeroRondaDesc(solicitudId)
                .orElseThrow(() -> new IllegalStateException("La solicitud no contiene propuestas previas."));

        // No puedes aceptar tu propia propuesta
        if (ultimaPropuesta.getAutor().getId().equals(usuarioId)) {
            throw new SolicitudNoPermitidaException("No puedes aceptar tu propia propuesta.");
        }

        Instant ahora = Instant.now(reloj);

        // Cambiar la solicitud a activa
        solicitud.activar(ahora);
        solicitudes.save(solicitud);

        // Cambiar el servicio a activo (ocupado/contratado)
        Servicio servicio = solicitud.getServicio();
        servicio.activar();
        servicios.save(servicio);

        String mensaje = "Acuerdo confirmado para «" + servicio.getTitulo() + "» entre "
                + solicitud.getCliente().getNombre() + " y " + servicio.getOfertador().getNombre()
                + " a " + pesos(ultimaPropuesta.getTarifaPorHora()) + " por hora ("
                + ultimaPropuesta.getIntensidadHoraria() + " horas por semana). El servicio está activo.";
        notificador.notificar(solicitud.getCliente(), solicitud, TipoNotificacion.ACUERDO_CONFIRMADO, mensaje);
        notificador.notificar(servicio.getOfertador(), solicitud, TipoNotificacion.ACUERDO_CONFIRMADO, mensaje);

        return solicitud;
    }

    /**
     * The offerer rejects without a counterproposal: the request becomes CANCELADA, the client is
     * notified and the service stays as it was (still available).
     */
    @Transactional
    public SolicitudCompra rechazar(Integer solicitudId, Integer usuarioId) {
        SolicitudCompra solicitud = solicitudes.findById(solicitudId)
                .orElseThrow(SolicitudNoEncontradaException::new);
        Servicio servicio = solicitud.getServicio();
        if (!servicio.getOfertador().getId().equals(usuarioId)) {
            throw new SolicitudNoPermitidaException("Solo el ofertador puede rechazar la solicitud.");
        }
        solicitud.cancelar(Instant.now(reloj));
        solicitudes.save(solicitud);
        notificador.notificar(solicitud.getCliente(), solicitud, TipoNotificacion.SOLICITUD_RECHAZADA,
                servicio.getOfertador().getNombre() + " rechazó tu solicitud de «" + servicio.getTitulo()
                        + "». El servicio sigue disponible.");
        return solicitud;
    }

    @Transactional(readOnly = true)
    public Optional<SolicitudCompra> buscarPorId(Integer id) {
        return solicitudes.findById(id);
    }

    /** The proposals of a request, oldest round first: the negotiation history. */
    @Transactional(readOnly = true)
    public List<Propuesta> propuestasDe(Integer solicitudId) {
        return propuestas.findBySolicitudIdOrderByNumeroRondaAsc(solicitudId);
    }

    private static String pesos(int valor) {
        return "$" + NumberFormat.getIntegerInstance(COLOMBIA).format(valor) + " COP";
    }
}
