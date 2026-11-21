package com.atenea.unimarket.negocio;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.atenea.unimarket.datos.modelo.Conversacion;
import com.atenea.unimarket.datos.modelo.Mensaje;
import com.atenea.unimarket.datos.repositorio.ConversacionRepository;
import com.atenea.unimarket.datos.repositorio.MensajeRepository;
import com.atenea.unimarket.datos.repositorio.NotificacionRepository;
import com.atenea.unimarket.datos.repositorio.PerfilRepository;
import com.atenea.unimarket.datos.repositorio.PropuestaRepository;
import com.atenea.unimarket.datos.repositorio.ServicioRepository;
import com.atenea.unimarket.datos.repositorio.SolicitudCompraRepository;
import com.atenea.unimarket.datos.repositorio.UsuarioRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integración del chat: ChatService + JPA + SQLite + migración V9, todo real.
 * Esta clase NO es @Transactional a propósito: así cada llamada al servicio abre y cierra su
 * propia transacción, igual que en la aplicación, y el rollback se puede observar de verdad.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:target/chat-service-test.db")
class ChatServiceIntegrationTests {

    @Autowired
    ChatService chat;
    @Autowired
    PerfilService perfiles;
    @Autowired
    ServicioService catalogo;
    @Autowired
    ConversacionRepository conversaciones;
    @Autowired
    MensajeRepository mensajes;
    @Autowired
    NotificacionRepository notificaciones;
    @Autowired
    PropuestaRepository propuestas;
    @Autowired
    SolicitudCompraRepository solicitudes;
    @Autowired
    ServicioRepository servicios;
    @Autowired
    PerfilRepository perfilRepository;
    @Autowired
    UsuarioRepository usuarios;
    @Autowired
    JdbcTemplate jdbc;

    Integer anaId;
    Integer betoId;
    Integer servicioId;

    @BeforeEach
    void preparar() {
        // Se borra en orden de dependencia: primero lo que apunta a otras tablas.
        jdbc.update("delete from mensaje");
        jdbc.update("delete from conversacion");
        notificaciones.deleteAll();
        propuestas.deleteAll();
        solicitudes.deleteAll();
        servicios.deleteAll();
        perfilRepository.deleteAll();
        usuarios.deleteAll();
        anaId = perfiles.registrar("sub-ana", "ana@unal.edu.co", "Ana", "3001234567", List.of()).getUsuario().getId();
        betoId = perfiles.registrar("sub-beto", "beto@unal.edu.co", "Beto", "3109876543", List.of()).getUsuario().getId();
        Integer tutorias = catalogo.categorias().stream().filter(c -> c.getNombre().equals("Tutorías"))
                .findFirst().orElseThrow().getId();
        servicioId = catalogo.publicar(anaId, new DatosServicio(tutorias, "Cálculo I",
                "Tutorías de cálculo diferencial.", 25000, 4, null, null, List.of())).getId();
    }

    @Test
    void elPrimerMensajeCreaLaConversacionConAutorYFecha() {
        Instant antes = Instant.now();

        Conversacion conversacion = chat.enviarAlOfertador(betoId, servicioId, "Hola, ¿sigue disponible?");

        assertThat(conversaciones.count()).isEqualTo(1);
        List<Mensaje> guardados = chat.mensajesDe(conversacion.getId(), betoId);
        assertThat(guardados).hasSize(1);
        assertThat(guardados.getFirst().getContenido()).isEqualTo("Hola, ¿sigue disponible?");
        assertThat(guardados.getFirst().getAutor().getId()).isEqualTo(betoId);
        assertThat(guardados.getFirst().getFechaEnvio())
                .isBetween(antes.minusSeconds(1), Instant.now().plusSeconds(1));
    }

    @Test
    void unPrimerMensajeInvalidoDeshaceLaConversacionRecienCreada() {
        // El servicio crea la conversación y después falla al construir el mensaje en blanco.
        // Como todo va en una sola transacción, no debe quedar ninguna fila.
        assertThatThrownBy(() -> chat.enviarAlOfertador(betoId, servicioId, "   "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(conversaciones.count()).isZero();
        assertThat(mensajes.count()).isZero();
    }

    @Test
    void escribirDosVecesAlMismoServicioReutilizaLaConversacion() {
        Conversacion primera = chat.enviarAlOfertador(betoId, servicioId, "Primero");
        Conversacion segunda = chat.enviarAlOfertador(betoId, servicioId, "Segundo");

        assertThat(segunda.getId()).isEqualTo(primera.getId());
        assertThat(conversaciones.count()).isEqualTo(1);
        assertThat(mensajes.count()).isEqualTo(2);
    }

    @Test
    void laBaseDeDatosNoPermiteDosConversacionesParaElMismoServicioYCliente() {
        Conversacion existente = chat.enviarAlOfertador(betoId, servicioId, "Hola");

        // Se salta el servicio y va directo al repositorio: la restricción UNIQUE debe saltar igual.
        assertThatThrownBy(() -> conversaciones.saveAndFlush(
                new Conversacion(existente.getServicio(), existente.getCliente(), Instant.now())))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void laBaseDeDatosRechazaMensajesVaciosOMuyLargosAunqueSeSalteLaAplicacion() {
        Integer conversacionId = chat.enviarAlOfertador(betoId, servicioId, "Hola").getId();
        String sql = "insert into mensaje (conversacion_id, autor_id, contenido, fecha_envio) values (?, ?, ?, ?)";

        assertThatThrownBy(() -> jdbc.update(sql, conversacionId, betoId, " \t\n ", Instant.now().toString()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(sql, conversacionId, betoId, "a".repeat(1001), Instant.now().toString()))
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.update(sql, conversacionId, betoId, "a".repeat(1000), Instant.now().toString()))
                .isEqualTo(1); // justo en el límite sí entra
        jdbc.update("delete from mensaje where contenido = ?", "a".repeat(1000));
    }

    @Test
    void mensajesNuevosDevuelveSoloLosPosterioresYEnOrden() {
        Integer conversacionId = chat.enviarAlOfertador(betoId, servicioId, "uno").getId();
        Integer idDelPrimero = chat.mensajesDe(conversacionId, betoId).getFirst().getId();
        chat.enviar(conversacionId, anaId, "dos");
        chat.enviar(conversacionId, betoId, "tres");

        List<Mensaje> nuevos = chat.mensajesNuevos(conversacionId, anaId, idDelPrimero);

        assertThat(nuevos).extracting(Mensaje::getContenido).containsExactly("dos", "tres");
        assertThat(chat.mensajesNuevos(conversacionId, anaId, nuevos.getLast().getId())).isEmpty();
        assertThat(chat.mensajesDe(conversacionId, betoId)).extracting(Mensaje::getContenido)
                .containsExactly("uno", "dos", "tres");
    }

    @Test
    void elOfertadorNoPuedeAbrirUnChatConsigoMismoYNoQuedaNada() {
        assertThatThrownBy(() -> chat.enviarAlOfertador(anaId, servicioId, "Hola a mí mismo"))
                .isInstanceOf(ChatNoPermitidoException.class);

        assertThat(conversaciones.count()).isZero();
        assertThat(mensajes.count()).isZero();
    }
}