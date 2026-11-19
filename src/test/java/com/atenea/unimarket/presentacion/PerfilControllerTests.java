package com.atenea.unimarket.presentacion;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.atenea.unimarket.datos.modelo.Perfil;
import com.atenea.unimarket.datos.modelo.Usuario;
import com.atenea.unimarket.datos.repositorio.PerfilRepository;
import com.atenea.unimarket.datos.repositorio.ServicioRepository;
import com.atenea.unimarket.datos.repositorio.UsuarioRepository;
import com.atenea.unimarket.negocio.PerfilService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:target/perfil-controller-test.db")
@AutoConfigureMockMvc
class PerfilControllerTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    PerfilService servicio;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    PerfilRepository perfiles;

    @Autowired
    ServicioRepository servicios;

    @BeforeEach
    void limpiar() {
        servicios.deleteAll();
        perfiles.deleteAll();
        usuarios.deleteAll();
    }

    @Test
    void sinPerfilCualquierPaginaRedirigeAlFormulario() throws Exception {
        for (String ruta : List.of("/", "/perfil", "/perfiles/1", "/cualquier/otra")) {
            mvc.perform(get(ruta).with(ana()))
                    .andExpect(redirectedUrl("/perfil/nuevo"));
        }
        assertThat(usuarios.count()).isZero();
    }

    @Test
    void elFormularioPrecargaElNombreYMuestraElCorreoSoloLectura() throws Exception {
        mvc.perform(get("/perfil/nuevo").with(ana()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<label for=\"nombre\">")))
                .andExpect(content().string(containsString("value=\"Ana Google\"")))
                .andExpect(content().string(containsString("value=\"ana@unal.edu.co\" readonly")))
                .andExpect(content().string(containsString("<label for=\"telefonoContacto\">")))
                .andExpect(content().string(containsString("<legend>Horarios de disponibilidad")));
        assertThat(usuarios.count()).isZero();
    }

    @Test
    void guardarDatosValidosCreaUsuarioYPerfilYDaAcceso() throws Exception {
        mvc.perform(post("/perfil/nuevo").with(ana()).with(csrf())
                        .param("nombre", "Ana María")
                        .param("correo", "otra@unal.edu.co")
                        .param("correoInstitucional", "otra@unal.edu.co")
                        .param("telefonoContacto", "300 123 4567")
                        .param("franjas[0].dia", "LUNES")
                        .param("franjas[0].inicio", "08:00")
                        .param("franjas[0].fin", "10:00")
                        .param("aceptaPolitica", "true"))
                .andExpect(flash().attribute("mensaje", "Tu perfil fue creado."));

        Usuario usuario = usuarios.findByGoogleSub("sub-ana").orElseThrow();
        assertThat(usuario.getNombre()).isEqualTo("Ana María");
        assertThat(usuario.getFechaAceptacionPolitica()).isEqualTo(usuario.getFechaCreacion());
        assertThat(usuario.getCorreoInstitucional()).isEqualTo("ana@unal.edu.co");
        Perfil perfil = perfiles.findByUsuarioId(usuario.getId()).orElseThrow();
        assertThat(perfil.getTelefonoContacto()).isEqualTo("3001234567");
        assertThat(perfil.getHorariosDisponibilidad()).hasSize(1);

        mvc.perform(get("/").with(ana()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ana María")));
        mvc.perform(get("/perfiles/" + usuario.getId()).with(ana()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("3001234567")))
                .andExpect(content().string(containsString("Lunes")))
                .andExpect(content().string(containsString("<time datetime=\"")));
    }

    @Test
    void otrosUsuariosPuedenVerElPerfil() throws Exception {
        Perfil perfilAna = servicio.registrar("sub-ana", "ana@unal.edu.co", "Ana", "3001234567", List.of());
        servicio.registrar("sub-beto", "beto@unal.edu.co", "Beto", "3109876543", List.of());

        mvc.perform(get("/perfiles/" + perfilAna.getUsuario().getId()).with(como("sub-beto", "beto@unal.edu.co")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ana")))
                .andExpect(content().string(containsString("Sin horarios registrados.")))
                .andExpect(content().string(not(containsString("3001234567"))));
    }

    @Test
    void campoObligatorioVacioSeResaltaYNoCreaNada() throws Exception {
        mvc.perform(post("/perfil/nuevo").with(ana()).with(csrf())
                        .param("nombre", " ")
                        .param("telefonoContacto", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("role=\"alert\"")))
                .andExpect(content().string(containsString("aria-invalid=\"true\"")))
                .andExpect(content().string(containsString("Ingresa tu nombre.")))
                .andExpect(content().string(containsString("Ingresa un teléfono de contacto.")))
                .andExpect(content().string(containsString("value=\"ana@unal.edu.co\" readonly")));
        assertThat(usuarios.count()).isZero();
        assertThat(perfiles.count()).isZero();
    }

    @Test
    void telefonoInvalidoInformaElErrorYConservaLoIngresado() throws Exception {
        mvc.perform(post("/perfil/nuevo").with(ana()).with(csrf())
                        .param("nombre", "Ana")
                        .param("telefonoContacto", "12345"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("celular colombiano de 10 dígitos")))
                .andExpect(content().string(containsString("value=\"12345\"")));
        assertThat(usuarios.count()).isZero();
    }

    @Test
    void franjaConRangoInvalidoNoCreaNada() throws Exception {
        mvc.perform(post("/perfil/nuevo").with(ana()).with(csrf())
                        .param("nombre", "Ana")
                        .param("telefonoContacto", "3001234567")
                        .param("franjas[0].dia", "LUNES")
                        .param("franjas[0].inicio", "10:00")
                        .param("franjas[0].fin", "08:00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("La hora de inicio debe ser anterior a la hora de fin.")));
        assertThat(usuarios.count()).isZero();
    }

    @Test
    void agregarFranjaReMuestraElFormularioSinValidar() throws Exception {
        mvc.perform(post("/perfil/nuevo").with(ana()).with(csrf())
                        .param("nombre", "")
                        .param("telefonoContacto", "")
                        .param("accion", "agregarFranja"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Franja 1")))
                .andExpect(content().string(not(containsString("role=\"alert\""))));
        assertThat(usuarios.count()).isZero();
    }

    @Test
    void elFormularioInicialSeGuardaSinFranjas() throws Exception {
        mvc.perform(get("/perfil/nuevo").with(ana()))
                .andExpect(content().string(not(containsString("Franja 1"))));
        mvc.perform(post("/perfil/nuevo").with(ana()).with(csrf())
                        .param("nombre", "Ana")
                        .param("telefonoContacto", "3001234567")
                        .param("aceptaPolitica", "true"))
                .andExpect(flash().attribute("mensaje", "Tu perfil fue creado."));
        assertThat(perfiles.count()).isEqualTo(1);
    }

    @Test
    void elFormularioPideAceptarLaPoliticaYEnlazaSuTexto() throws Exception {
        mvc.perform(get("/perfil/nuevo").with(ana()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"aceptaPolitica\"")))
                .andExpect(content().string(containsString("href=\"/politica-datos\"")));
    }

    @Test
    void sinAceptarLaPoliticaNoCreaNadaYConservaLoIngresado() throws Exception {
        mvc.perform(post("/perfil/nuevo").with(ana()).with(csrf())
                        .param("nombre", "Ana")
                        .param("telefonoContacto", "3001234567"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Debes aceptar la política de tratamiento de datos personales.")))
                .andExpect(content().string(containsString("value=\"3001234567\"")));
        mvc.perform(post("/perfil/nuevo").with(ana()).with(csrf())
                        .param("nombre", "Ana")
                        .param("telefonoContacto", "3001234567")
                        .param("aceptaPolitica", "false"))
                .andExpect(content().string(containsString("Debes aceptar la política de tratamiento de datos personales.")));
        assertThat(usuarios.count()).isZero();
    }

    @Test
    void laPoliticaSePuedeLeerAntesDeCrearLaCuenta() throws Exception {
        mvc.perform(get("/politica-datos").with(ana()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Política de tratamiento de datos personales")))
                .andExpect(content().string(containsString("1581 de 2012")))
                .andExpect(content().string(containsString("unal.edu.co")))
                .andExpect(content().string(not(containsString("href=\"/servicios\""))));
        assertThat(usuarios.count()).isZero();
    }

    @Test
    void usuarioRegistradoVeLaPoliticaConElMenuYEnlaceEnElEncabezado() throws Exception {
        servicio.registrar("sub-ana", "ana@unal.edu.co", "Ana", "3001234567", List.of());
        mvc.perform(get("/politica-datos").with(ana()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/servicios\"")))
                .andExpect(content().string(containsString("aria-current=\"page\">Política de datos</a>")));
        mvc.perform(get("/").with(ana()))
                .andExpect(content().string(containsString("href=\"/politica-datos\"")));
    }

    @Test
    void quitarFranjaIgnoraIndicesInvalidos() throws Exception {
        for (String indice : List.of("abc", "", "-1", "99999999999", "5", "1")) {
            mvc.perform(post("/perfil/nuevo").with(ana()).with(csrf())
                            .param("franjas[0].dia", "LUNES")
                            .param("accion", "quitarFranja:" + indice))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Franja 1")));
        }
        mvc.perform(post("/perfil/nuevo").with(ana()).with(csrf())
                        .param("franjas[0].dia", "LUNES")
                        .param("accion", "quitarFranja:0"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Franja 1"))));
    }

    @Test
    void accionDeFranjaConHoraIlegibleReMuestraElFormulario() throws Exception {
        mvc.perform(post("/perfil/nuevo").with(ana()).with(csrf())
                        .param("nombre", "Ana")
                        .param("franjas[0].inicio", "abc")
                        .param("accion", "agregarFranja"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ingresa una hora válida.")))
                .andExpect(content().string(containsString("Franja 2")));
        assertThat(usuarios.count()).isZero();
    }

    @Test
    void registradoNoPuedeVolverARegistrarse() throws Exception {
        servicio.registrar("sub-ana", "ana@unal.edu.co", "Ana", "3001234567", List.of());

        mvc.perform(get("/perfil/nuevo").with(ana()))
                .andExpect(redirectedUrl("/perfil"));
        mvc.perform(post("/perfil/nuevo").with(ana()).with(csrf())
                        .param("nombre", "Otro")
                        .param("telefonoContacto", "3109876543"))
                .andExpect(redirectedUrl("/perfil"));
        assertThat(usuarios.count()).isEqualTo(1);
        assertThat(usuarios.findByGoogleSub("sub-ana").orElseThrow().getNombre()).isEqualTo("Ana");
    }

    @Test
    void perfilInexistenteResponde404() throws Exception {
        servicio.registrar("sub-ana", "ana@unal.edu.co", "Ana", "3001234567", List.of());
        mvc.perform(get("/perfiles/999999").with(ana()))
                .andExpect(status().isNotFound());
    }

    private RequestPostProcessor ana() {
        return como("sub-ana", "ana@unal.edu.co");
    }

    private RequestPostProcessor como(String sub, String correo) {
        return oidcLogin().idToken(token -> token.subject(sub).claim("email", correo).claim("name", "Ana Google"));
    }
}
