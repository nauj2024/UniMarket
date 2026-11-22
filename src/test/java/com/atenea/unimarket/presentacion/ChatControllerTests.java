package com.atenea.unimarket.presentacion;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.atenea.unimarket.datos.repositorio.ConversacionRepository;
import com.atenea.unimarket.datos.repositorio.MensajeRepository;
import com.atenea.unimarket.datos.repositorio.NotificacionRepository;
import com.atenea.unimarket.datos.repositorio.PerfilRepository;
import com.atenea.unimarket.datos.repositorio.PropuestaRepository;
import com.atenea.unimarket.datos.repositorio.ServicioRepository;
import com.atenea.unimarket.datos.repositorio.SolicitudCompraRepository;
import com.atenea.unimarket.datos.repositorio.UsuarioRepository;
import com.atenea.unimarket.negocio.ChatService;
import com.atenea.unimarket.negocio.DatosServicio;
import com.atenea.unimarket.negocio.PerfilService;
import com.atenea.unimarket.negocio.ServicioService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** CHAT-USR-H01 de punta a punta en la capa web: rutas, seguridad, vistas y JSON del polling. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:target/chat-controller-test.db")
@AutoConfigureMockMvc
class ChatControllerTests {

    @Autowired
    MockMvc mvc;
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

    Integer anaId;
    Integer betoId;
    Integer servicioId;

    @BeforeEach
    void preparar() {
        mensajes.deleteAll();
        conversaciones.deleteAll();
        notificaciones.deleteAll();
        propuestas.deleteAll();
        solicitudes.deleteAll();
        servicios.deleteAll();
        perfilRepository.deleteAll();
        usuarios.deleteAll();
        anaId = perfiles.registrar("sub-ana", "ana@unal.edu.co", "Ana", "3001234567", List.of()).getUsuario().getId();
        betoId = perfiles.registrar("sub-beto", "beto@unal.edu.co", "Beto", "3109876543", List.of()).getUsuario().getId();
        perfiles.registrar("sub-carla", "carla@unal.edu.co", "Carla", "3201112233", List.of());
        Integer tutorias = catalogo.categorias().stream().filter(c -> c.getNombre().equals("Tutorías"))
                .findFirst().orElseThrow().getId();
        servicioId = catalogo.publicar(anaId, new DatosServicio(tutorias, "Cálculo I",
                "Tutorías de cálculo diferencial.", 25000, 4, null, null, List.of())).getId();
    }

    // ---- punto de entrada desde el detalle del servicio ----

    @Test
    void elDetalleOfreceChatearSoloAQuienNoEsElOfertador() throws Exception {
        mvc.perform(get("/servicios/" + servicioId).with(beto()))
                .andExpect(content().string(containsString("/servicios/" + servicioId + "/chat")))
                .andExpect(content().string(containsString("Chatear con el ofertador")));
        mvc.perform(get("/servicios/" + servicioId).with(ana()))
                .andExpect(content().string(not(containsString("/servicios/" + servicioId + "/chat"))));
    }

    @Test
    void sinConversacionPreviaSeMuestraElFormularioDelPrimerMensaje() throws Exception {
        mvc.perform(get("/servicios/" + servicioId + "/chat").with(beto()))
                .andExpect(status().isOk())
                .andExpect(view().name("conversacion/ver"))
                .andExpect(content().string(containsString("Chat con Ana")))
                .andExpect(content().string(containsString("action=\"/servicios/" + servicioId + "/chat\"")));
        assertThat(conversaciones.count()).isZero(); // abrir el formulario NO crea la conversación
    }

    @Test
    void conConversacionPreviaLaEntradaRedirigeAEsaConversacion() throws Exception {
        chat.enviarAlOfertador(betoId, servicioId, "Hola");

        mvc.perform(get("/servicios/" + servicioId + "/chat").with(beto()))
                .andExpect(redirectedUrl("/conversaciones/" + conversacionId()));
    }

    @Test
    void elOfertadorNoPuedeChatearConsigoMismo() throws Exception {
        mvc.perform(get("/servicios/" + servicioId + "/chat").with(ana()))
                .andExpect(redirectedUrl("/servicios"))
                .andExpect(flash().attribute("aviso", "No puedes abrir un chat contigo mismo."));
        mvc.perform(post("/servicios/" + servicioId + "/chat").with(ana()).with(csrf())
                        .param("contenido", "Hola a mí mismo"))
                .andExpect(redirectedUrl("/servicios"))
                .andExpect(flash().attribute("aviso", "No puedes abrir un chat contigo mismo."));
        assertThat(conversaciones.count()).isZero();
    }

    @Test
    void unServicioInexistenteDa404() throws Exception {
        mvc.perform(get("/servicios/9999/chat").with(beto())).andExpect(status().isNotFound());
    }

    // ---- primer mensaje y conversación ----

    @Test
    void elPrimerMensajeCreaLaConversacionYLaMuestra() throws Exception {
        mvc.perform(post("/servicios/" + servicioId + "/chat").with(beto()).with(csrf())
                        .param("contenido", "Hola, ¿sigue disponible?"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/conversaciones/*"));

        assertThat(conversaciones.count()).isEqualTo(1);
        mvc.perform(get("/conversaciones/" + conversacionId()).with(beto()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hola, ¿sigue disponible?")))
                .andExpect(content().string(containsString("Tú")))
                .andExpect(content().string(containsString("Chat con Ana")));
    }

    @Test
    void ambosParticipantesSeEscribenYVenLosMensajesAlRecargar() throws Exception {
        chat.enviarAlOfertador(betoId, servicioId, "Hola Ana");
        Integer id = conversacionId();

        mvc.perform(get("/conversaciones/" + id).with(ana()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hola Ana")))
                .andExpect(content().string(containsString("Chat con Beto")));
        mvc.perform(post("/conversaciones/" + id + "/mensajes").with(ana()).with(csrf())
                        .param("contenido", "Hola Beto, sí está disponible"))
                .andExpect(redirectedUrl("/conversaciones/" + id));
        mvc.perform(get("/conversaciones/" + id).with(beto()))
                .andExpect(content().string(containsString("Hola Beto, sí está disponible")))
                .andExpect(content().string(containsString("Ana")));
    }

    // ---- privacidad: solo los dos participantes ----

    @Test
    void unExtranoNoPuedeVerEscribirNiConsultarLaConversacion() throws Exception {
        chat.enviarAlOfertador(betoId, servicioId, "Mensaje privado");
        Integer id = conversacionId();

        mvc.perform(get("/conversaciones/" + id).with(carla())).andExpect(status().isNotFound());
        mvc.perform(post("/conversaciones/" + id + "/mensajes").with(carla()).with(csrf())
                .param("contenido", "Intruso")).andExpect(status().isNotFound());
        mvc.perform(get("/api/conversaciones/" + id + "/mensajes").with(carla())).andExpect(status().isNotFound());
        assertThat(mensajes.count()).isEqualTo(1);
    }

    @Test
    void unaConversacionInexistenteDaElMismo404() throws Exception {
        mvc.perform(get("/conversaciones/9999").with(beto())).andExpect(status().isNotFound());
        mvc.perform(get("/api/conversaciones/9999/mensajes").with(beto())).andExpect(status().isNotFound());
    }

    @Test
    void sinSesionNoSePuedeVerNada() throws Exception {
        chat.enviarAlOfertador(betoId, servicioId, "Hola");

        mvc.perform(get("/conversaciones/" + conversacionId())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/conversaciones/" + conversacionId() + "/mensajes"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void sinTokenCsrfElEnvioSeRechaza() throws Exception {
        mvc.perform(post("/servicios/" + servicioId + "/chat").with(beto()).param("contenido", "Hola"))
                .andExpect(status().isForbidden());
        assertThat(conversaciones.count()).isZero();
    }

    // ---- validación y escape ----

    @Test
    void unPrimerMensajeEnBlancoMuestraElErrorYNoCreaLaConversacion() throws Exception {
        mvc.perform(post("/servicios/" + servicioId + "/chat").with(beto()).with(csrf())
                        .param("contenido", "   "))
                .andExpect(status().isOk())
                .andExpect(view().name("conversacion/ver"))
                .andExpect(content().string(containsString("Escribe un mensaje.")));
        assertThat(conversaciones.count()).isZero();
        assertThat(mensajes.count()).isZero();
    }

    @Test
    void unMensajeDeMasDeMilCaracteresSeRechazaConElError() throws Exception {
        mvc.perform(post("/servicios/" + servicioId + "/chat").with(beto()).with(csrf())
                        .param("contenido", "a".repeat(1001)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("no puede superar 1000 caracteres")));
        assertThat(conversaciones.count()).isZero();
    }

    @Test
    void unMensajeEnBlancoEnUnaConversacionExistenteNoSeGuarda() throws Exception {
        chat.enviarAlOfertador(betoId, servicioId, "Hola");
        Integer id = conversacionId();

        mvc.perform(post("/conversaciones/" + id + "/mensajes").with(ana()).with(csrf()).param("contenido", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Escribe un mensaje.")));
        assertThat(mensajes.count()).isEqualTo(1);
    }

    @Test
    void elHtmlDeUnMensajeSeMuestraEscapadoYNoSeEjecuta() throws Exception {
        chat.enviarAlOfertador(betoId, servicioId, "<script>alert(1)</script>");

        mvc.perform(get("/conversaciones/" + conversacionId()).with(ana()))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)"))));
    }

    // ---- JSON del polling ----

    @Test
    void elPollingDevuelveSoloLosMensajesPosterioresConAutorYBandera() throws Exception {
        chat.enviarAlOfertador(betoId, servicioId, "uno");
        Integer id = conversacionId();
        Integer idDelPrimero = chat.mensajesDe(id, betoId).getFirst().getId();
        chat.enviar(id, anaId, "dos <b>negrita</b>");

        mvc.perform(get("/api/conversaciones/" + id + "/mensajes").param("despues", idDelPrimero.toString())
                        .with(beto()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].autor").value("Ana"))
                .andExpect(jsonPath("$[0].propio").value(false))
                .andExpect(jsonPath("$[0].contenido").value("dos <b>negrita</b>")) // el JSON va sin escapar; el JS usa textContent
                .andExpect(jsonPath("$[0].fechaIso").isNotEmpty())
                .andExpect(jsonPath("$[0].fechaTexto").isNotEmpty());
        mvc.perform(get("/api/conversaciones/" + id + "/mensajes").with(ana()))   // sin "despues": todos
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].propio").value(false))                  // para Ana, "uno" es de Beto
                .andExpect(jsonPath("$[1].propio").value(true));
    }

    // ---- lista de conversaciones (CHAT-USR-H02) ----

    @Test
    void elMenuTieneLaOpcionDeConversaciones() throws Exception {
        mvc.perform(get("/servicios").with(beto()))
                .andExpect(content().string(containsString("href=\"/conversaciones\"")));
    }

    @Test
    void laListaMuestraConQuienEsElServicioYEnlazaALaConversacion() throws Exception {
        chat.enviarAlOfertador(betoId, servicioId, "Hola, ¿sigue disponible?");
        Integer id = conversacionId();

        mvc.perform(get("/conversaciones").with(beto()))
                .andExpect(status().isOk())
                .andExpect(view().name("conversacion/lista"))
                .andExpect(content().string(containsString("href=\"/conversaciones/" + id + "\"")))
                .andExpect(content().string(containsString(">Ana</a>")))
                .andExpect(content().string(containsString("Cálculo I")))
                .andExpect(content().string(containsString("Tú:")))
                .andExpect(content().string(containsString("Hola, ¿sigue disponible?")));
        mvc.perform(get("/conversaciones").with(ana()))
                .andExpect(content().string(containsString("href=\"/conversaciones/" + id + "\"")))
                .andExpect(content().string(containsString(">Beto</a>")))
                .andExpect(content().string(not(containsString("Tú:"))));
    }

    @Test
    void laListaSoloIncluyeLasConversacionesDelUsuario() throws Exception {
        chat.enviarAlOfertador(betoId, servicioId, "Mensaje privado");

        mvc.perform(get("/conversaciones").with(carla()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/conversaciones/" + conversacionId()))))
                .andExpect(content().string(not(containsString("Mensaje privado"))))
                .andExpect(content().string(containsString("Todavía no tienes conversaciones.")));
    }

    @Test
    void elUltimoMensajeSeMuestraEscapado() throws Exception {
        chat.enviarAlOfertador(betoId, servicioId, "<script>alert(1)</script>");

        mvc.perform(get("/conversaciones").with(ana()))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)"))));
    }

    @Test
    void sinSesionNoSeVeLaLista() throws Exception {
        mvc.perform(get("/conversaciones")).andExpect(status().is3xxRedirection());
    }

    @Test
    void sinPerfilLaListaLlevaAlFormularioDelPerfil() throws Exception {
        mvc.perform(get("/conversaciones").with(como("sub-sin-perfil")))
                .andExpect(redirectedUrl("/perfil/nuevo"));
    }

    private Integer conversacionId() {
        return conversaciones.findByServicioIdAndClienteId(servicioId, betoId).orElseThrow().getId();
    }

    private RequestPostProcessor ana() {
        return como("sub-ana");
    }

    private RequestPostProcessor beto() {
        return como("sub-beto");
    }

    private RequestPostProcessor carla() {
        return como("sub-carla");
    }

    private RequestPostProcessor como(String sub) {
        return oidcLogin().idToken(token -> token.subject(sub).claim("email", sub + "@unal.edu.co"));
    }
}