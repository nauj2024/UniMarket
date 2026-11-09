package com.atenea.unimarket.presentacion;

import java.util.List;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.atenea.unimarket.datos.modelo.Servicio;
import com.atenea.unimarket.datos.repositorio.PerfilRepository;
import com.atenea.unimarket.datos.repositorio.ServicioRepository;
import com.atenea.unimarket.datos.repositorio.UsuarioRepository;
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

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:target/servicio-controller-test.db")
@AutoConfigureMockMvc
class ServicioControllerTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    PerfilService perfiles;

    @Autowired
    ServicioService catalogo;

    @Autowired
    ServicioRepository servicios;

    @Autowired
    PerfilRepository perfilRepository;

    @Autowired
    UsuarioRepository usuarios;

    Integer anaId;
    Integer tutoriasId;

    @BeforeEach
    void preparar() {
        servicios.deleteAll();
        perfilRepository.deleteAll();
        usuarios.deleteAll();
        anaId = perfiles.registrar("sub-ana", "ana@unal.edu.co", "Ana", "3001234567", List.of()).getUsuario().getId();
        tutoriasId = catalogo.categorias().stream().filter(c -> c.getNombre().equals("Tutorías")).findFirst().orElseThrow().getId();
    }

    @Test
    void sinPerfilNoPuedePublicar() throws Exception {
        mvc.perform(get("/servicios/nuevo").with(como("sub-nuevo")))
                .andExpect(redirectedUrl("/perfil/nuevo"));
        mvc.perform(valido(post("/servicios/nuevo").with(como("sub-nuevo")).with(csrf())))
                .andExpect(redirectedUrl("/perfil/nuevo"));
        assertThat(servicios.count()).isZero();
    }

    @Test
    void elFormularioMuestraLasCategoriasYLosCamposObligatorios() throws Exception {
        mvc.perform(get("/servicios/nuevo").with(ana()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<label for=\"categoriaId\">")))
                .andExpect(content().string(containsString(">Tutorías</option>")))
                .andExpect(content().string(containsString("<label for=\"titulo\">")))
                .andExpect(content().string(containsString("<label for=\"tarifaPorHora\">")))
                .andExpect(content().string(containsString("<label for=\"intensidadHoraria\">")))
                .andExpect(content().string(containsString("<legend>Horarios de disponibilidad")));
    }

    @Test
    void publicarConDatosValidosConfirmaYQuedaVisible() throws Exception {
        mvc.perform(valido(post("/servicios/nuevo").with(ana()).with(csrf()))
                        .param("especialidad", "Matemáticas")
                        .param("franjas[0].dia", "SABADO")
                        .param("franjas[0].inicio", "09:00")
                        .param("franjas[0].fin", "12:00"))
                .andExpect(flash().attribute("mensaje", "Tu servicio fue publicado."));

        Servicio servicio = servicios.findAll().getFirst();
        assertThat(servicio.getOfertador().getId()).isEqualTo(anaId);

        mvc.perform(get("/servicios/" + servicio.getId()).with(ana()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>Cálculo I</h1>")))
                .andExpect(content().string(containsString("$25.000 COP")))
                .andExpect(content().string(containsString("4 horas por semana")))
                .andExpect(content().string(containsString("Sábado")))
                .andExpect(content().string(containsString("/perfiles/" + anaId)));
        mvc.perform(get("/categorias/" + tutoriasId).with(ana()))
                .andExpect(content().string(containsString("Cálculo I")));
        mvc.perform(get("/perfiles/" + anaId).with(ana()))
                .andExpect(content().string(containsString("Cálculo I")));
    }

    @Test
    void camposObligatoriosVaciosSeResaltanYNoPublica() throws Exception {
        mvc.perform(post("/servicios/nuevo").with(ana()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("role=\"alert\"")))
                .andExpect(content().string(containsString("Selecciona una categoría.")))
                .andExpect(content().string(containsString("Ingresa un título.")))
                .andExpect(content().string(containsString("Describe el servicio.")))
                .andExpect(content().string(containsString("Ingresa la tarifa por hora.")))
                .andExpect(content().string(containsString("Ingresa la intensidad horaria.")))
                .andExpect(content().string(containsString("aria-invalid=\"true\"")));
        assertThat(servicios.count()).isZero();
    }

    @Test
    void tarifaMenorOIgualACeroInformaElErrorYConservaLosDatos() throws Exception {
        for (String tarifa : List.of("0", "-5000")) {
            mvc.perform(valido(post("/servicios/nuevo").with(ana()).with(csrf()), "tarifaPorHora", tarifa))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("La tarifa por hora debe ser mayor que cero.")))
                    .andExpect(content().string(containsString("value=\"C&aacute;lculo I\"")));
        }
        assertThat(servicios.count()).isZero();
    }

    @Test
    void valoresNoNumericosOFueraDeRangoNoPublican() throws Exception {
        mvc.perform(valido(post("/servicios/nuevo").with(ana()).with(csrf()), "tarifaPorHora", "25.000,50"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ingresa un número entero.")));
        mvc.perform(valido(post("/servicios/nuevo").with(ana()).with(csrf()), "intensidadHoraria", "200"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("no puede superar 168 horas por semana")));
        mvc.perform(valido(post("/servicios/nuevo").with(ana()).with(csrf()), "categoriaId", "9999"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Selecciona una categoría de la lista.")));
        assertThat(servicios.count()).isZero();
    }

    @Test
    void agregarFranjaReMuestraSinValidar() throws Exception {
        mvc.perform(post("/servicios/nuevo").with(ana()).with(csrf()).param("accion", "agregarFranja"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Franja 1")))
                .andExpect(content().string(not(containsString("role=\"alert\""))));
        assertThat(servicios.count()).isZero();
    }

    @Test
    void accionesDeFranjaConservanErroresDeConversionEnVezDeFallar() throws Exception {
        for (String accion : List.of("agregarFranja", "quitarFranja:0")) {
            mvc.perform(valido(post("/servicios/nuevo").with(ana()).with(csrf()), "tarifaPorHora", "99999999999")
                            .param("franjas[0].dia", "LUNES")
                            .param("accion", accion))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Ingresa un número entero.")))
                    .andExpect(content().string(containsString("value=\"C&aacute;lculo I\"")));
        }
        assertThat(servicios.count()).isZero();
    }

    @Test
    void quitarUnaFranjaConservaLasDemasYLosCampos() throws Exception {
        mvc.perform(valido(post("/servicios/nuevo").with(ana()).with(csrf()))
                        .param("franjas[0].dia", "LUNES").param("franjas[0].inicio", "08:00").param("franjas[0].fin", "10:00")
                        .param("franjas[1].dia", "MARTES").param("franjas[1].inicio", "14:00").param("franjas[1].fin", "16:00")
                        .param("accion", "quitarFranja:0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Franja 1")))
                .andExpect(content().string(not(containsString("Franja 2"))))
                .andExpect(content().string(containsString("value=\"14:00\"")))
                .andExpect(content().string(not(containsString("value=\"08:00\""))))
                .andExpect(content().string(containsString("value=\"25000\"")));
    }

    @Test
    void franjaInvalidaEnElServicioNoPublica() throws Exception {
        mvc.perform(valido(post("/servicios/nuevo").with(ana()).with(csrf()))
                        .param("franjas[0].dia", "LUNES").param("franjas[0].inicio", "10:00").param("franjas[0].fin", "08:00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("La hora de inicio debe ser anterior a la hora de fin.")));
        assertThat(servicios.count()).isZero();
    }

    @Test
    void noSePuedeElegirOfertadorNiEstadoDesdeLaPeticion() throws Exception {
        Integer betoId = perfiles.registrar("sub-beto", "beto@unal.edu.co", "Beto", "3109876543", List.of()).getUsuario().getId();
        mvc.perform(valido(post("/servicios/nuevo").with(ana()).with(csrf()))
                .param("ofertadorId", betoId.toString())
                .param("ofertador.id", betoId.toString())
                .param("estado", "CERRADO"));

        Servicio servicio = servicios.findAll().getFirst();
        assertThat(servicio.getOfertador().getId()).isEqualTo(anaId);
        assertThat(servicio.getEstado()).isEqualTo(com.atenea.unimarket.datos.modelo.EstadoServicio.DISPONIBLE);
    }

    @Test
    void publicarSinTokenCsrfEsRechazado() throws Exception {
        mvc.perform(valido(post("/servicios/nuevo").with(ana())))
                .andExpect(status().isForbidden());
        assertThat(servicios.count()).isZero();
    }

    @Test
    void catalogoEntregaDatosInicialesYOpcionesSinDuplicarLaInterfaz() throws Exception {
        publicarDeAna();
        String html = mvc.perform(get("/servicios").param("categoria", tutoriasId.toString())
                        .param("q", "no coincide").with(ana()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode inicial = datosIniciales(html);
        assertThat(inicial.get("resultado").get("total").asInt()).isZero();
        assertThat(inicial.get("resultado").get("servicios").isEmpty()).isTrue();
        assertThat(inicial.get("categorias").size()).isEqualTo(5);
        assertThat(inicial.get("categorias").get(4).get("value").asText()).isEqualTo(tutoriasId.toString());
        assertThat(inicial.get("categorias").get(4).get("label").asText()).isEqualTo("Tutorías");
        assertThat(inicial.get("ordenes").get(0).get("value").asText()).isEqualTo("ANTIGUOS");
        assertThat(inicial.get("ordenes").get(0).get("label").asText()).isEqualTo("Más antiguos");
        assertThat(html).contains("catalogo.js", "<noscript>")
                .doesNotContain("id=\"filtros\"", "class=\"service-cards\"");
    }

    @Test
    void busquedaEnVivoUsaLosMismosFiltrosYNoExponeEntidades() throws Exception {
        Servicio publicado = servicios.findById(publicarDeAna()).orElseThrow();
        mvc.perform(get("/api/servicios").with(ana()).param("q", "álculo")
                        .param("categoria", tutoriasId.toString()).param("orden", "TARIFA_ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.servicios[0].id").value(publicado.getId()))
                .andExpect(jsonPath("$.servicios[0].titulo").value("Cálculo I"))
                .andExpect(jsonPath("$.servicios[0].ofertador").value("Ana"))
                .andExpect(jsonPath("$.servicios[0].tarifaPorHora").value(25000))
                .andExpect(jsonPath("$.servicios[0].descripcion").doesNotExist());
        mvc.perform(get("/api/servicios").with(ana()).param("q", "%"))
                .andExpect(jsonPath("$.total").value(0));
        publicado.activar();
        servicios.save(publicado);
        mvc.perform(get("/api/servicios").with(ana()))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void busquedaEnVivoPaginaEnLaBaseYEntregaLaPaginaInicialAPreact() throws Exception {
        for (int i = 0; i < 21; i++) {
            publicarDeAna();
        }
        mvc.perform(get("/api/servicios").with(ana()))
                .andExpect(jsonPath("$.total").value(21))
                .andExpect(jsonPath("$.servicios.length()").value(20))
                .andExpect(jsonPath("$.hayMas").value(true));
        mvc.perform(get("/api/servicios").with(ana()).param("pagina", "1"))
                .andExpect(jsonPath("$.servicios.length()").value(1))
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.hayMas").value(false));
        String html = mvc.perform(get("/servicios").with(ana()).param("pagina", "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String api = mvc.perform(get("/api/servicios").with(ana()).param("pagina", "1"))
                .andReturn().getResponse().getContentAsString();
        assertThat(datosIniciales(html).get("resultado")).isEqualTo(new JsonMapper().readTree(api));
    }

    @Test
    void busquedaEnVivoValidaParametrosYRequiereCuenta() throws Exception {
        mvc.perform(get("/api/servicios")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/servicios").with(como("sin-perfil")))
                .andExpect(redirectedUrl("/perfil/nuevo"));
        mvc.perform(get("/api/servicios").with(ana()).param("q", "a".repeat(101)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/servicios").with(ana()).param("pagina", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void categoriasYServicioInexistente() throws Exception {
        mvc.perform(get("/categorias").with(ana()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Asesoría de tesis y trabajos")));
        mvc.perform(get("/categorias/" + tutoriasId).with(ana()))
                .andExpect(content().string(containsString("Aún no hay servicios disponibles en esta categoría.")));
        mvc.perform(get("/servicios/999999").with(ana())).andExpect(status().isNotFound());
        mvc.perform(get("/categorias/999999").with(ana())).andExpect(status().isNotFound());
    }

    @Test
    void elContenidoDelServicioSeEscapa() throws Exception {
        mvc.perform(valido(post("/servicios/nuevo").with(ana()).with(csrf()), "titulo", "<script>x()</script>"));
        Servicio servicio = servicios.findAll().getFirst();
        mvc.perform(get("/servicios/" + servicio.getId()).with(ana()))
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>x()"))));
        String html = mvc.perform(get("/servicios").with(ana()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("<script>x()</script>");
        assertThat(datosIniciales(html).get("resultado").get("servicios").get(0).get("titulo").asText())
                .isEqualTo("<script>x()</script>");
    }

    // ---- Editar servicio (OFERTA-OFE-H02) ----

    @Test
    void elFormularioDeEdicionVieneConLosDatosActuales() throws Exception {
        Integer id = publicarDeAna();

        mvc.perform(get("/servicios/" + id + "/editar").with(ana()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"C&aacute;lculo I\"")))
                .andExpect(content().string(containsString("value=\"25000\"")))
                .andExpect(content().string(containsString("Guardar cambios")));
    }

    @Test
    void editarConDatosValidosActualizaYConfirma() throws Exception {
        Integer id = publicarDeAna();

        mvc.perform(valido(post("/servicios/" + id + "/editar").with(ana()).with(csrf()),
                        "titulo", "Cálculo II", "tarifaPorHora", "30000"))
                .andExpect(redirectedUrl("/servicios/" + id))
                .andExpect(flash().attribute("mensaje", "Tu servicio fue actualizado."));

        Servicio servicio = servicios.findById(id).orElseThrow();
        assertThat(servicio.getTitulo()).isEqualTo("Cálculo II");
        assertThat(servicio.getTarifaPorHora()).isEqualTo(30000);
    }

    @Test
    void editarConDatosInvalidosNoGuardaYMuestraElError() throws Exception {
        Integer id = publicarDeAna();

        mvc.perform(valido(post("/servicios/" + id + "/editar").with(ana()).with(csrf()), "tarifaPorHora", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("La tarifa por hora debe ser mayor que cero.")))
                .andExpect(content().string(containsString("Guardar cambios")));

        assertThat(servicios.findById(id).orElseThrow().getTarifaPorHora()).isEqualTo(25000);
    }

    @Test
    void otroUsuarioNoPuedeEditarElServicio() throws Exception {
        Integer id = publicarDeAna();
        perfiles.registrar("sub-beto", "beto@unal.edu.co", "Beto", "3109876543", List.of());

        mvc.perform(get("/servicios/" + id + "/editar").with(como("sub-beto")))
                .andExpect(status().isForbidden());
        mvc.perform(valido(post("/servicios/" + id + "/editar").with(como("sub-beto")).with(csrf()),
                        "titulo", "Robado"))
                .andExpect(status().isForbidden());

        assertThat(servicios.findById(id).orElseThrow().getTitulo()).isEqualTo("Cálculo I");
    }

    @Test
    void editarUnServicioInexistenteDaNoEncontrado() throws Exception {
        mvc.perform(get("/servicios/999999/editar").with(ana())).andExpect(status().isNotFound());
        mvc.perform(valido(post("/servicios/999999/editar").with(ana()).with(csrf())))
                .andExpect(status().isNotFound());
    }

    @Test
    void editarSinTokenCsrfEsRechazado() throws Exception {
        Integer id = publicarDeAna();

        mvc.perform(valido(post("/servicios/" + id + "/editar").with(ana())))
                .andExpect(status().isForbidden());
    }

    @Test
    void elBotonEditarSoloLoVeElOfertador() throws Exception {
        Integer id = publicarDeAna();
        perfiles.registrar("sub-beto", "beto@unal.edu.co", "Beto", "3109876543", List.of());

        mvc.perform(get("/servicios/" + id).with(ana()))
                .andExpect(content().string(containsString("/servicios/" + id + "/editar")));
        mvc.perform(get("/servicios/" + id).with(como("sub-beto")))
                .andExpect(content().string(not(containsString("/servicios/" + id + "/editar"))));
    }

    private JsonNode datosIniciales(String html) {
        int script = html.indexOf("id=\"catalogo-inicial\"");
        assertThat(script).isGreaterThanOrEqualTo(0);
        int inicio = html.indexOf('>', script) + 1;
        return new JsonMapper().readTree(html.substring(inicio, html.indexOf("</script>", inicio)));
    }

    private Integer publicarDeAna() {
        return catalogo.publicar(anaId, new DatosServicio(tutoriasId, "Cálculo I",
                "Tutorías de cálculo diferencial.", 25000, 4, null, null, List.of())).getId();
    }

    /** A valid publish request; each pair in {@code cambios} replaces one field, e.g. "tarifaPorHora", "0". */
    private MockHttpServletRequestBuilder valido(MockHttpServletRequestBuilder peticion, String... cambios) {
        java.util.Map<String, String> campos = new java.util.LinkedHashMap<>();
        campos.put("categoriaId", tutoriasId.toString());
        campos.put("titulo", "Cálculo I");
        campos.put("descripcion", "Tutorías de cálculo diferencial.");
        campos.put("tarifaPorHora", "25000");
        campos.put("intensidadHoraria", "4");
        for (int i = 0; i < cambios.length; i += 2) {
            campos.put(cambios[i], cambios[i + 1]);
        }
        campos.forEach(peticion::param);
        return peticion;
    }

    private RequestPostProcessor ana() {
        return como("sub-ana");
    }

    private RequestPostProcessor como(String sub) {
        return oidcLogin().idToken(token -> token.subject(sub).claim("email", sub + "@unal.edu.co"));
    }
}
