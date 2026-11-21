package com.atenea.unimarket.negocio;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.atenea.unimarket.datos.modelo.Conversacion;
import com.atenea.unimarket.datos.modelo.EstadoServicio;
import com.atenea.unimarket.datos.modelo.Mensaje;
import com.atenea.unimarket.datos.modelo.Servicio;
import com.atenea.unimarket.datos.modelo.Usuario;
import com.atenea.unimarket.datos.repositorio.ConversacionRepository;
import com.atenea.unimarket.datos.repositorio.MensajeRepository;
import com.atenea.unimarket.datos.repositorio.ServicioRepository;
import com.atenea.unimarket.datos.repositorio.UsuarioRepository;

/** CHAT-USR-H01: chat privado entre un cliente y el ofertador de un servicio. */
@Service
public class ChatService {

    private final ConversacionRepository conversaciones;
    private final MensajeRepository mensajes;
    private final ServicioRepository servicios;
    private final UsuarioRepository usuarios;
    private final Clock reloj;

    public ChatService(ConversacionRepository conversaciones, MensajeRepository mensajes,
            ServicioRepository servicios, UsuarioRepository usuarios, Clock reloj) {
        this.conversaciones = conversaciones;
        this.mensajes = mensajes;
        this.servicios = servicios;
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    /** La conversación que el cliente ya tiene sobre un servicio, si existe. */
    @Transactional(readOnly = true)
    public Optional<Conversacion> conversacionExistente(Integer servicioId, Integer clienteId) {
        return conversaciones.findByServicioIdAndClienteId(servicioId, clienteId);
    }

    /**
     * El servicio sobre el que el cliente puede abrir un chat nuevo. Si no existe, lanza
     * ServicioNoEncontradoException. Si es su propio servicio o ya no está disponible,
     * lanza ChatNoPermitidoException.
     */
    @Transactional(readOnly = true)
    public Servicio servicioParaChat(Integer servicioId, Integer clienteId) {
        Servicio servicio = servicios.findById(servicioId).orElseThrow(ServicioNoEncontradoException::new);
        if (servicio.getOfertador().getId().equals(clienteId)) {
            throw new ChatNoPermitidoException("No puedes abrir un chat contigo mismo.");
        }
        if (servicio.getEstado() != EstadoServicio.DISPONIBLE) {
            throw new ChatNoPermitidoException("Este servicio ya no está disponible.");
        }
        return servicio;
    }

    /**
     * El cliente le escribe al ofertador de un servicio. Si es su primer mensaje se crea la
     * conversación; si ya existía (por ejemplo desde otra pestaña), el mensaje se agrega a ella.
     * Si el mensaje no es válido se lanza IllegalArgumentException y, como todo ocurre en una
     * sola transacción, la conversación recién creada se deshace: no queda un chat vacío.
     */
    @Transactional
    public Conversacion enviarAlOfertador(Integer clienteId, Integer servicioId, String contenido) {
        Instant ahora = Instant.now(reloj);
        Usuario cliente = usuario(clienteId);
        Optional<Conversacion> existente = conversaciones.findByServicioIdAndClienteId(servicioId, clienteId);
        Conversacion conversacion;
        if (existente.isPresent()) {
            conversacion = existente.get();
        } else {
            Servicio servicio = servicioParaChat(servicioId, clienteId);
            conversacion = conversaciones.save(new Conversacion(servicio, cliente, ahora));
        }
        mensajes.save(new Mensaje(conversacion, cliente, contenido, ahora));
        return conversacion;
    }

    /** Cualquiera de los dos participantes escribe en una conversación que ya existe. */
    @Transactional
    public Mensaje enviar(Integer conversacionId, Integer usuarioId, String contenido) {
        Conversacion conversacion = conversacionDe(conversacionId, usuarioId);
        return mensajes.save(new Mensaje(conversacion, usuario(usuarioId), contenido, Instant.now(reloj)));
    }

    /**
     * La conversación, solo si el usuario participa en ella. Si no existe o el usuario no es
     * participante lanza la misma excepción, para no revelar que existe.
     */
    @Transactional(readOnly = true)
    public Conversacion conversacionDe(Integer conversacionId, Integer usuarioId) {
        Conversacion conversacion = conversaciones.findById(conversacionId)
                .orElseThrow(ConversacionNoEncontradaException::new);
        if (!conversacion.esParticipante(usuarioId)) {
            throw new ConversacionNoEncontradaException();
        }
        return conversacion;
    }

    /** Todos los mensajes de la conversación, del más antiguo al más reciente. */
    @Transactional(readOnly = true)
    public List<Mensaje> mensajesDe(Integer conversacionId, Integer usuarioId) {
        conversacionDe(conversacionId, usuarioId);
        return mensajes.findByConversacionIdOrderByIdAsc(conversacionId);
    }

    /** Solo los mensajes posteriores al último que la página ya muestra (para el polling). */
    @Transactional(readOnly = true)
    public List<Mensaje> mensajesNuevos(Integer conversacionId, Integer usuarioId, Integer despuesDeId) {
        conversacionDe(conversacionId, usuarioId);
        return mensajes.findByConversacionIdAndIdGreaterThanOrderByIdAsc(conversacionId, despuesDeId);
    }

    private Usuario usuario(Integer id) {
        return usuarios.findById(id).orElseThrow(() -> new IllegalArgumentException("El usuario no existe."));
    }
}