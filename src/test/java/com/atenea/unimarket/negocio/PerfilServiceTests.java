package com.atenea.unimarket.negocio;

import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.atenea.unimarket.datos.modelo.DiaSemana;
import com.atenea.unimarket.datos.modelo.FranjaHorario;
import com.atenea.unimarket.datos.modelo.Perfil;
import com.atenea.unimarket.datos.modelo.Usuario;
import com.atenea.unimarket.datos.repositorio.PerfilRepository;
import com.atenea.unimarket.datos.repositorio.ServicioRepository;
import com.atenea.unimarket.datos.repositorio.UsuarioRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:target/perfil-service-test.db")
class PerfilServiceTests {

    @Autowired
    PerfilService servicio;

    @Autowired
    PerfilRepository perfiles;

    @Autowired
    ServicioRepository servicios;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        servicios.deleteAll();
        perfiles.deleteAll();
        usuarios.deleteAll();
    }

    @Test
    void registrarCreaUsuarioYPerfilJuntos() {
        List<FranjaHorario> horarios = List.of(
                new FranjaHorario(DiaSemana.MARTES, LocalTime.of(9, 0), LocalTime.of(11, 0)));

        Perfil perfil = servicio.registrar("sub-ana", "Ana@unal.edu.co", "  Ana   María ", "+57 300 123 4567", horarios);

        Usuario usuario = usuarios.findByGoogleSub("sub-ana").orElseThrow();
        assertThat(usuario.getNombre()).isEqualTo("Ana María");
        assertThat(usuario.getCorreoInstitucional()).isEqualTo("ana@unal.edu.co");
        Perfil guardado = perfiles.findByUsuarioId(usuario.getId()).orElseThrow();
        assertThat(guardado.getId()).isEqualTo(perfil.getId());
        assertThat(guardado.getTelefonoContacto()).isEqualTo("3001234567");
        assertThat(guardado.getHorariosDisponibilidad()).isEqualTo(horarios);
        assertThat(guardado.getFechaCreacion()).isEqualTo(usuario.getFechaCreacion());
        assertThat(guardado.getFechaActualizacion()).isEqualTo(guardado.getFechaCreacion());
        assertThat(usuario.getFechaAceptacionPolitica()).isEqualTo(usuario.getFechaCreacion());
    }

    @Test
    void datosInvalidosNoCreanNiUsuarioNiPerfil() {
        assertThatThrownBy(() -> servicio.registrar("sub-ana", "ana@unal.edu.co", "Ana", "123", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.registrar("sub-ana", "ana@unal.edu.co", " ", "3001234567", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(usuarios.count()).isZero();
        assertThat(perfiles.count()).isZero();
    }

    @Test
    void unaIdentidadSoloSeRegistraUnaVez() {
        servicio.registrar("sub-ana", "ana@unal.edu.co", "Ana", "3001234567", List.of());

        assertThatThrownBy(() -> servicio.registrar("sub-ana", "ana@unal.edu.co", "Otro", "3109876543", List.of()))
                .isInstanceOf(PerfilYaExisteException.class);
        assertThat(usuarios.count()).isEqualTo(1);
        assertThat(perfiles.count()).isEqualTo(1);
        assertThat(usuarios.findByGoogleSub("sub-ana").orElseThrow().getNombre()).isEqualTo("Ana");
    }

    @Test
    void registrosConcurrentesDeLaMismaIdentidadCreanUnaSolaCuenta() throws Exception {
        int intentos = 4;
        ExecutorService hilos = Executors.newFixedThreadPool(intentos);
        CountDownLatch salida = new CountDownLatch(1);
        Callable<String> registrar = () -> {
            salida.await();
            try {
                servicio.registrar("sub-ana", "ana@unal.edu.co", "Ana", "3001234567", List.of());
                return "creado";
            } catch (PerfilYaExisteException yaExiste) {
                return "duplicado";
            }
        };
        try {
            List<Future<String>> resultados = new java.util.ArrayList<>();
            for (int i = 0; i < intentos; i++) {
                resultados.add(hilos.submit(registrar));
            }
            salida.countDown();
            List<String> obtenidos = new java.util.ArrayList<>();
            for (Future<String> resultado : resultados) {
                obtenidos.add(resultado.get());
            }
            assertThat(obtenidos).containsOnlyOnce("creado").containsOnly("creado", "duplicado");
        } finally {
            hilos.shutdownNow();
        }
        assertThat(usuarios.count()).isEqualTo(1);
        assertThat(perfiles.count()).isEqualTo(1);
    }

    @Test
    void siFallaElPerfilNoQuedaUsuario() {
        jdbc.execute("CREATE TRIGGER fallar_perfil BEFORE INSERT ON perfil BEGIN SELECT RAISE(ABORT, 'falla'); END");
        try {
            assertThatThrownBy(() -> servicio.registrar("sub-ana", "ana@unal.edu.co", "Ana", "3001234567", List.of()))
                    .isNotInstanceOf(PerfilYaExisteException.class);
        } finally {
            jdbc.execute("DROP TRIGGER fallar_perfil");
        }
        assertThat(usuarios.count()).isZero();
    }

    @Test
    void laMigracionV2EliminaUsuariosSinPerfil() {
        servicio.registrar("sub-ana", "ana@unal.edu.co", "Ana", "3001234567", List.of());
        jdbc.update("INSERT INTO usuario (google_sub, nombre, correo_institucional, fecha_creacion) VALUES (?, ?, ?, ?)",
                "sub-viejo", "Viejo", "viejo@unal.edu.co", 0L);

        jdbc.execute(new String(leer("db/migration/V2__eliminar_usuarios_sin_perfil.sql")));

        assertThat(usuarios.findByGoogleSub("sub-viejo")).isEmpty();
        assertThat(usuarios.findByGoogleSub("sub-ana")).isPresent();
    }

    private static byte[] leer(String recurso) {
        try (var entrada = PerfilServiceTests.class.getClassLoader().getResourceAsStream(recurso)) {
            return entrada.readAllBytes();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Test
    void elCorreoSeGuardaEnMinusculasSinImportarElIdiomaDelServidor() {
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            servicio.registrar("sub-isabel", "ISABEL@unal.edu.co", "Isabel", "3001234567", List.of());
        } finally {
            Locale.setDefault(original);
        }
        assertThat(usuarios.findByGoogleSub("sub-isabel").orElseThrow().getCorreoInstitucional())
                .isEqualTo("isabel@unal.edu.co");
    }

    @ParameterizedTest
    @ValueSource(strings = {"3001234567", "300 123 4567", "300-123-4567", "+573001234567", "57 3001234567"})
    void aceptaCelularesColombianos(String telefono) {
        assertThat(PerfilService.normalizarTelefono(telefono)).isEqualTo("3001234567");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "123", "6011234567", "30012345678", "300123456a", "+1 3001234567"})
    void rechazaTelefonosInvalidos(String telefono) {
        assertThatThrownBy(() -> PerfilService.normalizarTelefono(telefono))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
