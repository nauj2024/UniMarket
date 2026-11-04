package com.atenea.unimarket.negocio;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.atenea.unimarket.datos.modelo.EstadoServicio;
import com.atenea.unimarket.datos.modelo.EstadoSolicitud;
import com.atenea.unimarket.datos.modelo.Notificacion;
import com.atenea.unimarket.datos.modelo.Propuesta;
import com.atenea.unimarket.datos.modelo.SolicitudCompra;
import com.atenea.unimarket.datos.modelo.TipoNotificacion;
import com.atenea.unimarket.datos.repositorio.NotificacionRepository;
import com.atenea.unimarket.datos.repositorio.PerfilRepository;
import com.atenea.unimarket.datos.repositorio.PropuestaRepository;
import com.atenea.unimarket.datos.repositorio.ServicioRepository;
import com.atenea.unimarket.datos.repositorio.SolicitudCompraRepository;
import com.atenea.unimarket.datos.repositorio.UsuarioRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:target/solicitud-service-test.db")
class SolicitudCompraServiceTests {

    @Autowired
    SolicitudCompraService solicitudes;

    @Autowired
    NotificacionService notificaciones;

    @Autowired
    PerfilService perfiles;

    @Autowired
    ServicioService catalogo;

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
    JdbcTemplate jdbc;

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
    void contraproponerNotificaSoloALaContraparteConLasCondicionesVigentes() {
        Integer solicitudId = solicitudes.crear(betoId, servicioId, null).getId();

        solicitudes.contraproponer(solicitudId, anaId, 30000, 6);

        List<Notificacion> delCliente = notificaciones.deUsuario(betoId);
        assertThat(delCliente).hasSize(1);
        assertThat(delCliente.getFirst().getTipo()).isEqualTo(TipoNotificacion.CONTRAPROPUESTA_RECIBIDA);
        assertThat(delCliente.getFirst().getSolicitud().getId()).isEqualTo(solicitudId);
        assertThat(delCliente.getFirst().getMensaje()).contains("Ana", "Cálculo I", "$30.000 COP", "6 horas por semana");
        assertThat(notificaciones.deUsuario(anaId)).hasSize(1);

        solicitudes.contraproponer(solicitudId, betoId, null, 12);

        List<Notificacion> delOfertador = notificaciones.deUsuario(anaId);
        assertThat(delOfertador).hasSize(2);
        assertThat(delOfertador.getFirst().getTipo()).isEqualTo(TipoNotificacion.CONTRAPROPUESTA_RECIBIDA);
        assertThat(delOfertador.getFirst().getSolicitud().getId()).isEqualTo(solicitudId);
        assertThat(delOfertador.getFirst().getMensaje()).contains("Beto", "Cálculo I", "$30.000 COP", "6 horas por semana");
        assertThat(notificaciones.deUsuario(betoId)).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aceptarNotificaElAcuerdoAAmbasPartesSinDuplicarlo(boolean aceptaCliente) {
        Integer solicitudId = solicitudes.crear(betoId, servicioId, 20000).getId();
        if (aceptaCliente) {
            solicitudes.contraproponer(solicitudId, anaId, 30000, 6);
        }
        Integer aceptanteId = aceptaCliente ? betoId : anaId;

        solicitudes.aceptar(solicitudId, aceptanteId);

        for (Integer destinatarioId : List.of(anaId, betoId)) {
            List<Notificacion> acuerdos = notificaciones.deUsuario(destinatarioId).stream()
                    .filter(n -> n.getTipo() == TipoNotificacion.ACUERDO_CONFIRMADO).toList();
            assertThat(acuerdos).hasSize(1);
            assertThat(acuerdos.getFirst().getSolicitud().getId()).isEqualTo(solicitudId);
            assertThat(acuerdos.getFirst().getMensaje()).contains("Cálculo I", "Ana", "Beto",
                    aceptaCliente ? "$30.000 COP" : "$20.000 COP",
                    aceptaCliente ? "6 horas por semana" : "4 horas por semana");
        }
        assertThat(solicitudRepository.findById(solicitudId).orElseThrow().getEstado()).isEqualTo(EstadoSolicitud.ACTIVA);
        assertThat(servicios.findById(servicioId).orElseThrow().getEstado()).isEqualTo(EstadoServicio.ACTIVO);

        long cantidad = notificacionRepository.count();
        assertThatThrownBy(() -> solicitudes.aceptar(solicitudId, aceptanteId))
                .isInstanceOf(SolicitudNoPermitidaException.class);
        assertThat(notificacionRepository.count()).isEqualTo(cantidad);
    }

    @Test
    void operacionesInvalidasNoNotifican() {
        Integer solicitudId = solicitudes.crear(betoId, servicioId, null).getId();

        assertThatThrownBy(() -> solicitudes.contraproponer(solicitudId, betoId, 30000, null))
                .isInstanceOf(SolicitudNoPermitidaException.class);
        assertThatThrownBy(() -> solicitudes.aceptar(solicitudId, betoId))
                .isInstanceOf(SolicitudNoPermitidaException.class);
        assertThatThrownBy(() -> solicitudes.contraproponer(solicitudId, anaId, 0, 6))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(notificacionRepository.count()).isEqualTo(1);
    }

    @Test
    void crearConLasCondicionesPublicadasRegistraLaSolicitudYSuPrimeraPropuesta() {
        SolicitudCompra creada = solicitudes.crear(betoId, servicioId, null);

        SolicitudCompra guardada = solicitudRepository.findById(creada.getId()).orElseThrow();
        assertThat(guardada.getEstado()).isEqualTo(EstadoSolicitud.NEGOCIANDO);
        assertThat(guardada.getCliente().getId()).isEqualTo(betoId);
        assertThat(guardada.getServicio().getId()).isEqualTo(servicioId);
        assertThat(guardada.getFechaCreacion()).isNotNull();

        List<Propuesta> propuestas = solicitudes.propuestasDe(creada.getId());
        assertThat(propuestas).hasSize(1);
        Propuesta primera = propuestas.getFirst();
        assertThat(primera.getNumeroRonda()).isEqualTo(1);
        assertThat(primera.getAutor().getId()).isEqualTo(betoId);
        assertThat(primera.getTarifaPorHora()).isEqualTo(25000);
        assertThat(primera.getIntensidadHoraria()).isEqualTo(4);
        assertThat(primera.getFechaEnvio()).isNotNull();
    }

    @Test
    void crearConOtraTarifaLaRegistraEnLaPrimeraPropuestaYConservaLaIntensidadPublicada() {
        SolicitudCompra creada = solicitudes.crear(betoId, servicioId, 20000);

        Propuesta primera = solicitudes.propuestasDe(creada.getId()).getFirst();
        assertThat(primera.getTarifaPorHora()).isEqualTo(20000);
        assertThat(primera.getIntensidadHoraria()).isEqualTo(4);
        assertThat(servicios.findById(servicioId).orElseThrow().getTarifaPorHora()).isEqualTo(25000);
    }

    @Test
    void crearNotificaAlOfertadorYElServicioSigueDisponible() {
        SolicitudCompra creada = solicitudes.crear(betoId, servicioId, null);

        List<Notificacion> delOfertador = notificaciones.deUsuario(anaId);
        assertThat(delOfertador).hasSize(1);
        assertThat(delOfertador.getFirst().getTipo()).isEqualTo(TipoNotificacion.SOLICITUD_RECIBIDA);
        assertThat(delOfertador.getFirst().getSolicitud().getId()).isEqualTo(creada.getId());
        assertThat(delOfertador.getFirst().getMensaje()).contains("Beto", "Cálculo I", "25.000");
        assertThat(notificaciones.deUsuario(betoId)).isEmpty();
        assertThat(servicios.findById(servicioId).orElseThrow().getEstado()).isEqualTo(EstadoServicio.DISPONIBLE);
    }

    @Test
    void noSePuedeSolicitarElPropioServicio() {
        assertThatThrownBy(() -> solicitudes.crear(anaId, servicioId, null))
                .isInstanceOf(SolicitudNoPermitidaException.class)
                .hasMessage("No puedes solicitar tu propio servicio.");
        assertThat(solicitudRepository.count()).isZero();
        assertThat(notificacionRepository.count()).isZero();
    }

    @Test
    void noSePuedeSolicitarUnServicioQueYaNoEstaDisponible() {
        jdbc.update("UPDATE servicio SET estado = 'CERRADO' WHERE id = ?", servicioId);

        assertThatThrownBy(() -> solicitudes.crear(betoId, servicioId, null))
                .isInstanceOf(SolicitudNoPermitidaException.class)
                .hasMessage("Este servicio ya no está disponible.");
        assertThat(solicitudRepository.count()).isZero();
    }

    @Test
    void noSePuedeSolicitarUnServicioInexistente() {
        assertThatThrownBy(() -> solicitudes.crear(betoId, 99999, null))
                .isInstanceOf(ServicioNoEncontradoException.class);
    }

    @Test
    void noSePuedeAbrirUnaSegundaSolicitudEnNegociacionParaElMismoServicio() {
        solicitudes.crear(betoId, servicioId, null);

        assertThatThrownBy(() -> solicitudes.crear(betoId, servicioId, 20000))
                .isInstanceOf(SolicitudNoPermitidaException.class)
                .hasMessage("Ya tienes una solicitud en negociación para este servicio.");
        assertThat(solicitudRepository.count()).isEqualTo(1);
        assertThat(notificacionRepository.count()).isEqualTo(1);
    }

    @Test
    void unaTarifaInvalidaNoDejaNadaGuardado() {
        for (int tarifa : new int[] {0, -5000}) {
            assertThatThrownBy(() -> solicitudes.crear(betoId, servicioId, tarifa))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(solicitudRepository.count()).isZero();
        assertThat(propuestaRepository.count()).isZero();
        assertThat(notificacionRepository.count()).isZero();
    }

    @Test
    void rechazarCancelaLaSolicitudNotificaAlClienteYElServicioSigueDisponible() {
        Integer solicitudId = solicitudes.crear(betoId, servicioId, null).getId();

        solicitudes.rechazar(solicitudId, anaId);

        assertThat(solicitudRepository.findById(solicitudId).orElseThrow().getEstado())
                .isEqualTo(EstadoSolicitud.CANCELADA);
        assertThat(servicios.findById(servicioId).orElseThrow().getEstado()).isEqualTo(EstadoServicio.DISPONIBLE);
        List<Notificacion> delCliente = notificaciones.deUsuario(betoId);
        assertThat(delCliente).hasSize(1);
        assertThat(delCliente.getFirst().getTipo()).isEqualTo(TipoNotificacion.SOLICITUD_RECHAZADA);
        assertThat(delCliente.getFirst().getMensaje()).contains("Ana", "Cálculo I");
    }

    @Test
    void despuesDeUnRechazoElClientePuedeSolicitarDeNuevo() {
        Integer solicitudId = solicitudes.crear(betoId, servicioId, null).getId();
        solicitudes.rechazar(solicitudId, anaId);

        SolicitudCompra otra = solicitudes.crear(betoId, servicioId, 22000);

        assertThat(otra.getId()).isNotEqualTo(solicitudId);
        assertThat(otra.getEstado()).isEqualTo(EstadoSolicitud.NEGOCIANDO);
    }

    @Test
    void soloElOfertadorPuedeRechazar() {
        Integer solicitudId = solicitudes.crear(betoId, servicioId, null).getId();

        assertThatThrownBy(() -> solicitudes.rechazar(solicitudId, betoId))
                .isInstanceOf(SolicitudNoPermitidaException.class);
        assertThat(solicitudRepository.findById(solicitudId).orElseThrow().getEstado())
                .isEqualTo(EstadoSolicitud.NEGOCIANDO);
        assertThat(notificaciones.deUsuario(betoId)).isEmpty();
    }

    @Test
    void unaSolicitudYaRechazadaNoSePuedeRechazarDeNuevo() {
        Integer solicitudId = solicitudes.crear(betoId, servicioId, null).getId();
        solicitudes.rechazar(solicitudId, anaId);

        assertThatThrownBy(() -> solicitudes.rechazar(solicitudId, anaId))
                .isInstanceOf(IllegalStateException.class);
        assertThat(notificaciones.deUsuario(betoId)).hasSize(1);
    }

    @Test
    void rechazarUnaSolicitudInexistenteFalla() {
        assertThatThrownBy(() -> solicitudes.rechazar(99999, anaId))
                .isInstanceOf(SolicitudNoEncontradaException.class);
    }
}
