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

        mvc.perform(post("/solicitudes/" + solicitudId + "/aceptar").with(beto()).with(csrf())
                .param("propuestaId", propuestaRepository.findFirstBySolicitudIdOrderByNumeroRondaDesc(solicitudId).orElseThrow().getId().toString()))
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

    @Test
    void unTerceroNoPuedeContraproponerNiAceptarPorPostDirecto() throws Exception {
        perfiles.registrar("sub-tercero", "tercero@unal.edu.co", "Tercero", "3201112233", List.of());
        Integer id = solicitudes.crear(betoId, servicioId, null).getId();
        String vigente = propuestaRepository.findFirstBySolicitudIdOrderByNumeroRondaDesc(id).orElseThrow().getId().toString();
        mvc.perform(post("/solicitudes/" + id + "/contraproponer").with(como("sub-tercero")).with(csrf())
                        .param("tarifaPorHora", "1").param("intensidadHoraria", "168"))
                .andExpect(flash().attributeExists("aviso"));
        mvc.perform(post("/solicitudes/" + id + "/aceptar").with(como("sub-tercero")).with(csrf())
                        .param("propuestaId", vigente))
                .andExpect(flash().attributeExists("aviso"));
        assertThat(solicitudes.propuestasDe(id)).hasSize(1);
        assertThat(solicitudRepository.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoSolicitud.NEGOCIANDO);
    }

    @Test
    void aceptarRequiereLaPropuestaMostradaYRechazaUnFormularioViejo() throws Exception {
        Integer id = solicitudes.crear(betoId, servicioId, null).getId();
        solicitudes.contraproponer(id, anaId, 30000, 6);
        String vieja = propuestaRepository.findFirstBySolicitudIdOrderByNumeroRondaDesc(id).orElseThrow().getId().toString();
        mvc.perform(get("/solicitudes/" + id).with(beto())).andExpect(content().string(containsString("name=\"propuestaId\"")));
        solicitudes.contraproponer(id, betoId, 26000, null);
        solicitudes.contraproponer(id, anaId, 90000, 100);
        mvc.perform(post("/solicitudes/" + id + "/aceptar").with(beto()).with(csrf()).param("propuestaId", vieja))
                .andExpect(flash().attributeExists("aviso"));
        mvc.perform(post("/solicitudes/" + id + "/aceptar").with(beto()).with(csrf()))
                .andExpect(status().isBadRequest());
        assertThat(solicitudRepository.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoSolicitud.NEGOCIANDO);
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
