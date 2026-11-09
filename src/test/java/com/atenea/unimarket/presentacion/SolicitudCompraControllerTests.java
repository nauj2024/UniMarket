package com.atenea.unimarket.presentacion;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.atenea.unimarket.datos.modelo.EstadoServicio;
import com.atenea.unimarket.datos.modelo.EstadoSolicitud;
import com.atenea.unimarket.datos.modelo.SolicitudCompra;
import com.atenea.unimarket.datos.repositorio.NotificacionRepository;
import com.atenea.unimarket.datos.repositorio.PerfilRepository;
import com.atenea.unimarket.datos.repositorio.PropuestaRepository;
import com.atenea.unimarket.datos.repositorio.ServicioRepository;
import com.atenea.unimarket.datos.repositorio.SolicitudCompraRepository;
import com.atenea.unimarket.datos.repositorio.UsuarioRepository;
import com.atenea.unimarket.negocio.DatosServicio;
import com.atenea.unimarket.negocio.PerfilService;
import com.atenea.unimarket.negocio.ServicioService;
import com.atenea.unimarket.negocio.SolicitudCompraService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:target/solicitud-controller-test.db")
@AutoConfigureMockMvc
class SolicitudCompraControllerTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    PerfilService perfiles;

    @Autowired
    ServicioService catalogo;

    @Autowired
    SolicitudCompraService solicitudes;

    @Autowired
    SolicitudCompraRepository solicitudRepository;

    @Autowired
    PropuestaRepository propuestaRepository;

    @Autowired
    NotificacionRepository notificacionRepository;

    @Autowired
    ServicioRepository servicios;

    @Autowired
    PerfilRepository perfilRepository;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    com.atenea.unimarket.negocio.NotificacionService bandeja;

    @Autowired
    org.springframework.transaction.PlatformTransactionManager transactionManager;

    Integer anaId;
    Integer betoId;
    Integer servicioId;

    @BeforeEach
    void preparar() {
        notificacionRepository.deleteAll();
        propuestaRepository.deleteAll();
        solicitudRepository.deleteAll();
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
    void bandejaLimitaElDesplegablePeroConservaElHistorialYElEstado() throws Exception {
        for (int i = 0; i < 4; i++) {
            Integer solicitudId = solicitudes.crear(betoId, servicioId, null).getId();
            solicitudes.rechazar(solicitudId, anaId);
        }
        mvc.perform(get("/api/notificaciones").with(ana()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificaciones.length()").value(3))
                .andExpect(jsonPath("$.sinLeer").value(4))
                .andExpect(jsonPath("$.notificaciones[0].tipo").value("SOLICITUD_RECIBIDA"));
        Integer id = bandeja.bandeja(anaId, false).notificaciones().getFirst().id();
        mvc.perform(post("/api/notificaciones/" + id + "/leer").with(ana()).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/notificaciones/" + id + "/descartar").with(ana()).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(bandeja.bandeja(anaId, false).sinLeer()).isEqualTo(3);
        assertThat(bandeja.bandeja(anaId, false).notificaciones()).noneMatch(n -> n.id().equals(id));
        mvc.perform(get("/api/notificaciones?historial=true").with(ana()))
                .andExpect(jsonPath("$.notificaciones.length()").value(4))
                .andExpect(jsonPath("$.notificaciones[0].leida").value(true))
                .andExpect(jsonPath("$.notificaciones[0].descartada").value(true));
        mvc.perform(post("/api/notificaciones/" + id + "/descartar?descartada=false").with(ana()).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(bandeja.bandeja(anaId, false).notificaciones().getFirst().id()).isEqualTo(id);
        assertThat(bandeja.bandeja(anaId, false).sinLeer()).isEqualTo(3);
    }

    @Test
    void noPermiteLeerNiDescartarNotificacionesAjenasYExigeCsrf() throws Exception {
        solicitudes.crear(betoId, servicioId, null);
        Integer id = bandeja.bandeja(anaId, false).notificaciones().getFirst().id();
        for (String action : List.of("leer", "descartar")) {
            mvc.perform(post("/api/notificaciones/" + id + "/" + action).with(beto()).with(csrf()))
                    .andExpect(status().isNotFound());
            mvc.perform(post("/api/notificaciones/" + id + "/" + action).with(ana()))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/notificaciones").with(beto()))
                .andExpect(jsonPath("$.notificaciones.length()").value(0))
                .andExpect(jsonPath("$.sinLeer").value(0));
        mvc.perform(get("/api/notificaciones/eventos")).andExpect(status().is3xxRedirection());
        assertThat(bandeja.bandeja(anaId, false).sinLeer()).isEqualTo(1);
    }

    @Test
    void emiteSoloAlDestinatarioDespuesDelCommitYNoEmiteAlRevertir() throws Exception {
        var anaStream = mvc.perform(get("/api/notificaciones/eventos").with(ana()))
                .andExpect(request().asyncStarted()).andReturn();
        var betoStream = mvc.perform(get("/api/notificaciones/eventos").with(beto()))
                .andExpect(request().asyncStarted()).andReturn();
        try {
            var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            transaction.executeWithoutResult(status -> {
                solicitudes.crear(betoId, servicioId, null);
                assertThat(new String(anaStream.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8))
                        .doesNotContain("event:cambio");
                status.setRollbackOnly();
            });
            assertThat(anaStream.getResponse().getContentAsString()).doesNotContain("event:cambio");
            assertThat(bandeja.bandeja(anaId, true).notificaciones()).isEmpty();
            solicitudes.crear(betoId, servicioId, null);
            assertThat(anaStream.getResponse().getContentAsString()).contains("event:cambio");
            assertThat(betoStream.getResponse().getContentAsString()).doesNotContain("event:cambio");
            assertThat(bandeja.bandeja(anaId, true).notificaciones()).hasSize(1);
        } finally {
            anaStream.getRequest().getAsyncContext().complete();
            betoStream.getRequest().getAsyncContext().complete();
        }
    }

    @Test
    void contrapropuestaYAceptacionAparecenEnNotificacionesConEnlaceALaSolicitud() throws Exception {
        Integer solicitudId = solicitudes.crear(betoId, servicioId, null).getId();

        mvc.perform(post("/solicitudes/" + solicitudId + "/contraproponer").with(ana()).with(csrf())
                        .param("tarifaPorHora", "30000").param("intensidadHoraria", "6"))
                .andExpect(redirectedUrl("/solicitudes/" + solicitudId))
                .andExpect(flash().attributeExists("mensaje"));
        mvc.perform(get("/notificaciones").with(beto()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("envió una contrapropuesta")))
                .andExpect(content().string(containsString("$30.000 COP")))
                .andExpect(content().string(containsString("/solicitudes/" + solicitudId)));

        mvc.perform(post("/solicitudes/" + solicitudId + "/aceptar").with(beto()).with(csrf()))
                .andExpect(redirectedUrl("/solicitudes/" + solicitudId))
                .andExpect(flash().attributeExists("mensaje"));
        for (RequestPostProcessor usuario : List.of(ana(), beto())) {
            mvc.perform(get("/notificaciones").with(usuario))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Acuerdo confirmado")))
                    .andExpect(content().string(containsString("6 horas por semana")))
                    .andExpect(content().string(containsString("/solicitudes/" + solicitudId)));
        }
    }

    @Test
    void elDetalleOfreceSolicitarSoloAQuienNoEsElOfertador() throws Exception {
        mvc.perform(get("/servicios/" + servicioId).with(beto()))
                .andExpect(content().string(containsString("/servicios/" + servicioId + "/solicitar")));
        mvc.perform(get("/servicios/" + servicioId).with(ana()))
                .andExpect(content().string(not(containsString("/servicios/" + servicioId + "/solicitar"))));
    }

    @Test
    void elFormularioMuestraLaTarifaYLaIntensidadPublicadasAntesDeEnviar() throws Exception {
        mvc.perform(get("/servicios/" + servicioId + "/solicitar").with(beto()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("$25.000 COP por hora")))
                .andExpect(content().string(containsString("4 horas por semana")))
                .andExpect(content().string(containsString("<label for=\"tarifaPorHora\">")))
                .andExpect(content().string(containsString("value=\"25000\"")));
    }

    @Test
    void enviarCreaLaSolicitudNegociandoYNotificaAlOfertador() throws Exception {
        mvc.perform(post("/servicios/" + servicioId + "/solicitar").with(beto()).with(csrf())
                        .param("tarifaPorHora", "25000"))
                .andExpect(status().is3xxRedirection());

        SolicitudCompra solicitud = solicitudRepository.findAll().getFirst();
        assertThat(solicitud.getEstado()).isEqualTo(EstadoSolicitud.NEGOCIANDO);
        assertThat(solicitud.getCliente().getId()).isEqualTo(betoId);
        assertThat(propuestaRepository.count()).isEqualTo(1);
        assertThat(notificacionRepository.count()).isEqualTo(1);

        mvc.perform(get("/notificaciones").with(ana()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Beto")))
                .andExpect(content().string(containsString("/solicitudes/" + solicitud.getId())));
    }

    @Test
    void despuesDeEnviarElClienteVeLaSolicitudConSuPrimeraPropuesta() throws Exception {
        mvc.perform(post("/servicios/" + servicioId + "/solicitar").with(beto()).with(csrf())
                        .param("tarifaPorHora", "20000"))
                .andExpect(status().is3xxRedirection());
        Integer solicitudId = solicitudRepository.findAll().getFirst().getId();

        mvc.perform(get("/solicitudes/" + solicitudId).with(beto()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("NEGOCIANDO")))
                .andExpect(content().string(containsString("$20.000 COP")))
                .andExpect(content().string(not(containsString("Rechazar solicitud"))));
    }

    @Test
    void unaTarifaInvalidaMuestraElErrorYNoCreaNada() throws Exception {
        for (String tarifa : List.of("0", "-5000")) {
            mvc.perform(post("/servicios/" + servicioId + "/solicitar").with(beto()).with(csrf())
                            .param("tarifaPorHora", tarifa))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("La tarifa por hora debe ser mayor que cero.")));
        }
        mvc.perform(post("/servicios/" + servicioId + "/solicitar").with(beto()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ingresa la tarifa por hora.")));
        assertThat(solicitudRepository.count()).isZero();
        assertThat(notificacionRepository.count()).isZero();
    }

    @Test
    void elOfertadorNoPuedeSolicitarSuPropioServicio() throws Exception {
        mvc.perform(get("/servicios/" + servicioId + "/solicitar").with(ana()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/servicios/" + servicioId + "/solicitar").with(ana()).with(csrf())
                        .param("tarifaPorHora", "25000"))
                .andExpect(status().isForbidden());
        assertThat(solicitudRepository.count()).isZero();
    }

    @Test
    void unaSegundaSolicitudEnNegociacionSeInformaEnElFormulario() throws Exception {
        solicitudes.crear(betoId, servicioId, null);

        mvc.perform(post("/servicios/" + servicioId + "/solicitar").with(beto()).with(csrf())
                        .param("tarifaPorHora", "25000"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ya tienes una solicitud en negociación para este servicio.")));
        assertThat(solicitudRepository.count()).isEqualTo(1);
    }

    @Test
    void soloElClienteYElOfertadorVenLaSolicitud() throws Exception {
        Integer solicitudId = solicitudes.crear(betoId, servicioId, null).getId();
        perfiles.registrar("sub-carlos", "carlos@unal.edu.co", "Carlos", "3201112233", List.of());

        mvc.perform(get("/solicitudes/" + solicitudId).with(beto())).andExpect(status().isOk());
        mvc.perform(get("/solicitudes/" + solicitudId).with(ana())).andExpect(status().isOk());
        mvc.perform(get("/solicitudes/" + solicitudId).with(como("sub-carlos"))).andExpect(status().isForbidden());
        mvc.perform(get("/solicitudes/99999").with(ana())).andExpect(status().isNotFound());
    }

    @Test
    void elOfertadorRechazaYElServicioSigueDisponible() throws Exception {
        Integer solicitudId = solicitudes.crear(betoId, servicioId, null).getId();

        mvc.perform(get("/solicitudes/" + solicitudId).with(ana()))
                .andExpect(content().string(containsString("Rechazar solicitud")));
        mvc.perform(post("/solicitudes/" + solicitudId + "/rechazar").with(ana()).with(csrf()))
                .andExpect(redirectedUrl("/solicitudes/" + solicitudId))
                .andExpect(flash().attributeExists("mensaje"));

        assertThat(solicitudRepository.findById(solicitudId).orElseThrow().getEstado())
                .isEqualTo(EstadoSolicitud.CANCELADA);
        assertThat(servicios.findById(servicioId).orElseThrow().getEstado()).isEqualTo(EstadoServicio.DISPONIBLE);
        mvc.perform(get("/notificaciones").with(beto()))
                .andExpect(content().string(containsString("rechazó tu solicitud")));
        mvc.perform(get("/solicitudes/" + solicitudId).with(ana()))
                .andExpect(content().string(not(containsString("Rechazar solicitud"))));
    }

    @Test
    void elClienteNoPuedeRechazarSuPropiaSolicitud() throws Exception {
        Integer solicitudId = solicitudes.crear(betoId, servicioId, null).getId();

        mvc.perform(post("/solicitudes/" + solicitudId + "/rechazar").with(beto()).with(csrf()))
                .andExpect(redirectedUrl("/solicitudes/" + solicitudId))
                .andExpect(flash().attributeExists("aviso"));

        assertThat(solicitudRepository.findById(solicitudId).orElseThrow().getEstado())
                .isEqualTo(EstadoSolicitud.NEGOCIANDO);
    }

    @Test
    void sinPerfilNoPuedeSolicitar() throws Exception {
        mvc.perform(get("/servicios/" + servicioId + "/solicitar").with(como("sub-nuevo")))
                .andExpect(redirectedUrl("/perfil/nuevo"));
    }

    private RequestPostProcessor ana() {
        return como("sub-ana");
    }

    private RequestPostProcessor beto() {
        return como("sub-beto");
    }

    private RequestPostProcessor como(String sub) {
        return oidcLogin().idToken(token -> token.subject(sub).claim("email", sub + "@unal.edu.co"));
    }
}
