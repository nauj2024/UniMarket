package com.atenea.unimarket.negocio;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.atenea.unimarket.datos.modelo.Categoria;
import com.atenea.unimarket.datos.modelo.Conversacion;
import com.atenea.unimarket.datos.modelo.EstadoServicio;
import com.atenea.unimarket.datos.modelo.Mensaje;
import com.atenea.unimarket.datos.modelo.Servicio;
import com.atenea.unimarket.datos.modelo.Usuario;
import com.atenea.unimarket.datos.repositorio.ConversacionRepository;
import com.atenea.unimarket.datos.repositorio.MensajeRepository;
import com.atenea.unimarket.datos.repositorio.ServicioRepository;
import com.atenea.unimarket.datos.repositorio.UsuarioRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Reglas de negocio del chat, sin base de datos: los repositorios son dobles de Mockito.
 * Stub = doble con respuestas fijas (when(...).thenReturn(...), Clock.fixed).
 * Dummy = doble que se pasa solo porque el constructor lo exige y que el test nunca usa.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTests {

    private static final Instant AHORA = Instant.parse("2026-11-20T15:00:00Z");

    @Mock
    ConversacionRepository conversaciones;
    @Mock
    MensajeRepository mensajes;
    @Mock
    ServicioRepository servicios;
    @Mock
    UsuarioRepository usuarios;

    ChatService chat;
    Usuario ana;      // ofertadora, id 1
    Usuario beto;     // cliente, id 2
    Usuario carla;    // persona ajena a la conversación, id 3
    Servicio servicio;           // id 10, de Ana
    Conversacion conversacion;   // id 7, de Beto sobre el servicio de Ana

    @BeforeEach
    void preparar() {
        Clock relojFijo = Clock.fixed(AHORA, ZoneOffset.UTC); // stub del reloj: siempre marca AHORA
        chat = new ChatService(conversaciones, mensajes, servicios, usuarios, relojFijo);
        ana = usuario(1, "ana");
        beto = usuario(2, "beto");
        carla = usuario(3, "carla");
        servicio = new Servicio(ana, mock(Categoria.class), "Cálculo I", "Tutorías de cálculo.",
                25000, 4, null, null, List.of(), AHORA);
        ReflectionTestUtils.setField(servicio, "id", 10);
        conversacion = new Conversacion(servicio, beto, AHORA);
        ReflectionTestUtils.setField(conversacion, "id", 7);
    }

    // ---- conversacionDe: solo participan el cliente y el ofertador ----

    @Test
    void elClienteVeSuConversacion() {
        when(conversaciones.findById(7)).thenReturn(Optional.of(conversacion)); // stub
        // mensajes, servicios y usuarios son dummies aquí: no se tocan.

        assertThat(chat.conversacionDe(7, beto.getId())).isSameAs(conversacion);
        verifyNoInteractions(mensajes, servicios, usuarios);
    }

    @Test
    void elOfertadorVeLaConversacion() {
        when(conversaciones.findById(7)).thenReturn(Optional.of(conversacion));

        assertThat(chat.conversacionDe(7, ana.getId())).isSameAs(conversacion);
    }

    @Test
    void unAjenoRecibeLaMismaExcepcionQueSiNoExistiera() {
        when(conversaciones.findById(7)).thenReturn(Optional.of(conversacion));

        assertThatThrownBy(() -> chat.conversacionDe(7, carla.getId()))
                .isInstanceOf(ConversacionNoEncontradaException.class);
    }

    @Test
    void unaConversacionInexistenteLanzaLaMismaExcepcion() {
        when(conversaciones.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chat.conversacionDe(99, beto.getId()))
                .isInstanceOf(ConversacionNoEncontradaException.class)
                .hasMessage("La conversación no existe.");
    }

    // ---- servicioParaChat: quién puede abrir un chat nuevo ----

    @Test
    void elOfertadorNoPuedeChatearConsigoMismo() {
        when(servicios.findById(10)).thenReturn(Optional.of(servicio));

        assertThatThrownBy(() -> chat.servicioParaChat(10, ana.getId()))
                .isInstanceOf(ChatNoPermitidoException.class)
                .hasMessage("No puedes abrir un chat contigo mismo.");
    }

    @Test
    void noSePuedeAbrirChatSobreUnServicioNoDisponible() {
        ReflectionTestUtils.setField(servicio, "estado", EstadoServicio.ACTIVO);
        when(servicios.findById(10)).thenReturn(Optional.of(servicio));

        assertThatThrownBy(() -> chat.servicioParaChat(10, beto.getId()))
                .isInstanceOf(ChatNoPermitidoException.class)
                .hasMessage("Este servicio ya no está disponible.");
    }

    @Test
    void unServicioInexistenteLanzaServicioNoEncontrado() {
        when(servicios.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chat.servicioParaChat(99, beto.getId()))
                .isInstanceOf(ServicioNoEncontradoException.class);
    }

    @Test
    void unClienteConServicioDisponibleRecibeElServicio() {
        when(servicios.findById(10)).thenReturn(Optional.of(servicio));

        assertThat(chat.servicioParaChat(10, beto.getId())).isSameAs(servicio);
    }

    // ---- enviarAlOfertador: el primer mensaje crea la conversación ----

    @Test
    void elPrimerMensajeCreaLaConversacionYGuardaAutorYFecha() {
        when(usuarios.findById(2)).thenReturn(Optional.of(beto));
        when(servicios.findById(10)).thenReturn(Optional.of(servicio));
        when(conversaciones.findByServicioIdAndClienteId(10, 2)).thenReturn(Optional.empty());
        when(conversaciones.save(any(Conversacion.class))).thenAnswer(llamada -> llamada.getArgument(0));

        Conversacion creada = chat.enviarAlOfertador(2, 10, "  Hola, ¿sigue disponible?  ");

        assertThat(creada.getCliente()).isSameAs(beto);
        assertThat(creada.getOfertador()).isSameAs(ana);
        assertThat(creada.getFechaCreacion()).isEqualTo(AHORA);
        ArgumentCaptor<Mensaje> guardado = ArgumentCaptor.forClass(Mensaje.class);
        verify(mensajes).save(guardado.capture());
        assertThat(guardado.getValue().getAutor()).isSameAs(beto);
        assertThat(guardado.getValue().getContenido()).isEqualTo("Hola, ¿sigue disponible?");
        assertThat(guardado.getValue().getFechaEnvio()).isEqualTo(AHORA);
    }

    @Test
    void siLaConversacionYaExisteElMensajeSeAgregaSinCrearOtra() {
        when(usuarios.findById(2)).thenReturn(Optional.of(beto));
        when(conversaciones.findByServicioIdAndClienteId(10, 2)).thenReturn(Optional.of(conversacion));

        Conversacion resultado = chat.enviarAlOfertador(2, 10, "Otro mensaje");

        assertThat(resultado).isSameAs(conversacion);
        verify(conversaciones, never()).save(any());
        verify(mensajes).save(any(Mensaje.class));
        verifyNoInteractions(servicios); // dummy: no hace falta releer el servicio
    }

    @Test
    void unPrimerMensajeEnBlancoLanzaIllegalArgument() {
        when(usuarios.findById(2)).thenReturn(Optional.of(beto));
        when(servicios.findById(10)).thenReturn(Optional.of(servicio));
        when(conversaciones.save(any(Conversacion.class))).thenAnswer(llamada -> llamada.getArgument(0));

        assertThatThrownBy(() -> chat.enviarAlOfertador(2, 10, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        verify(mensajes, never()).save(any());
    }

    @Test
    void elAutochatNoCreaNiConversacionNiMensaje() {
        when(usuarios.findById(1)).thenReturn(Optional.of(ana));
        when(servicios.findById(10)).thenReturn(Optional.of(servicio));

        assertThatThrownBy(() -> chat.enviarAlOfertador(1, 10, "Hola a mí mismo"))
                .isInstanceOf(ChatNoPermitidoException.class);
        verify(conversaciones, never()).save(any());
        verify(mensajes, never()).save(any());
    }

    // ---- enviar: escribir en una conversación existente ----

    @Test
    void elOfertadorPuedeResponder() {
        when(conversaciones.findById(7)).thenReturn(Optional.of(conversacion));
        when(usuarios.findById(1)).thenReturn(Optional.of(ana));
        when(mensajes.save(any(Mensaje.class))).thenAnswer(llamada -> llamada.getArgument(0));

        Mensaje respuesta = chat.enviar(7, 1, "Sí, sigue disponible");

        assertThat(respuesta.getAutor()).isSameAs(ana);
        assertThat(respuesta.getConversacion()).isSameAs(conversacion);
        assertThat(respuesta.getFechaEnvio()).isEqualTo(AHORA);
    }

    @Test
    void unAjenoNoPuedeEscribirYNoSeGuardaNada() {
        when(conversaciones.findById(7)).thenReturn(Optional.of(conversacion));

        assertThatThrownBy(() -> chat.enviar(7, carla.getId(), "Intruso"))
                .isInstanceOf(ConversacionNoEncontradaException.class);
        verify(mensajes, never()).save(any());
    }

    // ---- lectura de mensajes (polling) ----

    @Test
    void mensajesNuevosPideSoloLosPosterioresAlIdIndicado() {
        Mensaje nuevo = new Mensaje(conversacion, ana, "Respuesta", AHORA);
        when(conversaciones.findById(7)).thenReturn(Optional.of(conversacion));
        when(mensajes.findByConversacionIdAndIdGreaterThanOrderByIdAsc(7, 5)).thenReturn(List.of(nuevo));

        assertThat(chat.mensajesNuevos(7, beto.getId(), 5)).containsExactly(nuevo);
    }

    @Test
    void unAjenoNoPuedeLeerMensajes() {
        when(conversaciones.findById(7)).thenReturn(Optional.of(conversacion));

        assertThatThrownBy(() -> chat.mensajesDe(7, carla.getId()))
                .isInstanceOf(ConversacionNoEncontradaException.class);
        verify(mensajes, never()).findByConversacionIdOrderByIdAsc(any());
    }

    private static Usuario usuario(int id, String nombre) {
        Usuario usuario = new Usuario("sub-" + nombre, nombre, nombre + "@unal.edu.co", AHORA);
        ReflectionTestUtils.setField(usuario, "id", id); // el id lo pone la base; aquí lo fijamos a mano
        return usuario;
    }
        @Test
    void unUsuarioInexistenteNoPuedeEscribir() {
        when(usuarios.findById(99)).thenReturn(Optional.empty()); // el repositorio no encuentra al usuario

        assertThatThrownBy(() -> chat.enviarAlOfertador(99, 10, "Hola"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El usuario no existe.");
        verify(conversaciones, never()).save(any());
        verify(mensajes, never()).save(any());
    }
        @Test
    void conversacionExistenteDevuelveLoQueEncuentraElRepositorio() {
        when(conversaciones.findByServicioIdAndClienteId(10, 2)).thenReturn(Optional.of(conversacion));

        assertThat(chat.conversacionExistente(10, 2)).containsSame(conversacion);
    }

    @Test
    void conversacionExistenteEstaVaciaSiElClienteNuncaHaEscrito() {
        when(conversaciones.findByServicioIdAndClienteId(10, 2)).thenReturn(Optional.empty());

        assertThat(chat.conversacionExistente(10, 2)).isEmpty();
    }

    @Test
    void mensajesDeDevuelveTodosLosMensajesDeLaConversacionEnOrden() {
        Mensaje primero = new Mensaje(conversacion, beto, "uno", AHORA);
        Mensaje segundo = new Mensaje(conversacion, ana, "dos", AHORA);
        when(conversaciones.findById(7)).thenReturn(Optional.of(conversacion));
        when(mensajes.findByConversacionIdOrderByIdAsc(7)).thenReturn(List.of(primero, segundo));

        assertThat(chat.mensajesDe(7, ana.getId())).containsExactly(primero, segundo);
    }
}