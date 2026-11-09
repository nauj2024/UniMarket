package com.atenea.unimarket.presentacion.controller;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.RequestParam;

import com.atenea.unimarket.datos.modelo.EstadoServicio;
import com.atenea.unimarket.datos.modelo.EstadoSolicitud;
import com.atenea.unimarket.datos.modelo.Propuesta;
import com.atenea.unimarket.datos.modelo.Servicio;
import com.atenea.unimarket.datos.modelo.SolicitudCompra;
import com.atenea.unimarket.datos.modelo.Usuario;
import com.atenea.unimarket.negocio.NotificacionService;
import com.atenea.unimarket.negocio.ServicioService;
import com.atenea.unimarket.negocio.SolicitudCompraService;
import com.atenea.unimarket.negocio.SolicitudNoEncontradaException;
import com.atenea.unimarket.negocio.SolicitudNoPermitidaException;
import com.atenea.unimarket.negocio.UsuarioService;
import com.atenea.unimarket.presentacion.formulario.SolicitudFormulario;

/** COMPRA-CLI-H01 / HU-04: request a service, follow the request, and let the offerer reject it. */
@Controller
public class SolicitudCompraController {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter
            .ofPattern("d 'de' MMMM 'de' yyyy, HH:mm", Locale.forLanguageTag("es-CO"))
            .withZone(PerfilController.ZONA);

    private final SolicitudCompraService solicitudes;
    private final ServicioService servicios;
    private final UsuarioService usuarios;
    private final NotificacionService notificaciones;

    public SolicitudCompraController(SolicitudCompraService solicitudes, ServicioService servicios,
            UsuarioService usuarios, NotificacionService notificaciones) {
        this.solicitudes = solicitudes;
        this.servicios = servicios;
        this.usuarios = usuarios;
        this.notificaciones = notificaciones;
    }

    /** Shows the published rate and weekly hours before the client sends the request. */
    @GetMapping("/servicios/{id}/solicitar")
    public String formulario(@AuthenticationPrincipal OidcUser oidc, @PathVariable Integer id, Model model,
            RedirectAttributes redireccion) {
        Servicio servicio = servicioSolicitable(id, oidc);
        if (servicio.getEstado() != EstadoServicio.DISPONIBLE) {
            redireccion.addFlashAttribute("aviso", "Este servicio ya no está disponible.");
            return "redirect:/servicios";
        }
        model.addAttribute("solicitud", new SolicitudFormulario(servicio.getTarifaPorHora()));
        return vistaFormulario(model, servicio);
    }

    @PostMapping("/servicios/{id}/solicitar")
    public String enviar(@AuthenticationPrincipal OidcUser oidc, @PathVariable Integer id,
            @Validated @ModelAttribute("solicitud") SolicitudFormulario formulario, BindingResult errores,
            RedirectAttributes redireccion, Model model) {
        Servicio servicio = servicioSolicitable(id, oidc);
        if (errores.hasErrors()) {
            return vistaFormulario(model, servicio);
        }
        try {
            SolicitudCompra solicitud = solicitudes.crear(usuarioActual(oidc).getId(), id,
                    formulario.getTarifaPorHora());
            redireccion.addFlashAttribute("mensaje", "Tu solicitud fue enviada. El ofertador ya fue notificado.");
            return "redirect:/solicitudes/" + solicitud.getId();
        } catch (SolicitudNoPermitidaException noPermitida) {
            errores.reject("solicitud.no.permitida", noPermitida.getMessage());
            return vistaFormulario(model, servicio);
        }
    }

    /** The request and its proposals; only the client and the offerer can see it. */
    @GetMapping("/solicitudes/{id}")
    public String ver(@AuthenticationPrincipal OidcUser oidc, @PathVariable Integer id, Model model) {
        SolicitudCompra solicitud = solicitudes.buscarPorId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Usuario actual = usuarioActual(oidc);
        boolean esCliente = solicitud.getCliente().getId().equals(actual.getId());
        boolean esOfertador = solicitud.getServicio().getOfertador().getId().equals(actual.getId());
        if (!esCliente && !esOfertador) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        List<Propuesta> listaPropuestas = solicitudes.propuestasDe(id);
        Propuesta ultimaPropuesta = listaPropuestas.get(listaPropuestas.size() - 1);

        // Regla de turno: es mi turno si la solicitud está negociando y la última propuesta NO la escribí yo
        boolean esMiTurno = solicitud.getEstado() == EstadoSolicitud.NEGOCIANDO 
                && !ultimaPropuesta.getAutor().getId().equals(actual.getId());

        model.addAttribute("solicitud", solicitud);
        model.addAttribute("propuestas", listaPropuestas);
        model.addAttribute("esCliente", esCliente);
        model.addAttribute("esMiTurno", esMiTurno);
        model.addAttribute("puedeRechazar", esOfertador && solicitud.getEstado() == EstadoSolicitud.NEGOCIANDO);
        model.addAttribute("formato", new ServicioController.FormatoPesos());
        model.addAttribute("fecha", new FormatoFecha());
        return "solicitud/ver";
    }
    @PostMapping("/solicitudes/{id}/rechazar")
    public String rechazar(@AuthenticationPrincipal OidcUser oidc, @PathVariable Integer id,
            RedirectAttributes redireccion) {
        try {
            solicitudes.rechazar(id, usuarioActual(oidc).getId());
            redireccion.addFlashAttribute("mensaje", "Rechazaste la solicitud. El cliente fue notificado.");
        } catch (SolicitudNoEncontradaException noExiste) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (SolicitudNoPermitidaException | IllegalStateException noPermitida) {
            redireccion.addFlashAttribute("aviso", noPermitida.getMessage());
        }
        return "redirect:/solicitudes/" + id;
    }

    @PostMapping("/solicitudes/{id}/contraproponer")
    public String contraproponer(@AuthenticationPrincipal OidcUser oidc, @PathVariable Integer id,
            @RequestParam(required = false) Integer tarifaPorHora,
            @RequestParam(required = false) Integer intensidadHoraria,
            RedirectAttributes redireccion) {
        try {
            solicitudes.contraproponer(id, usuarioActual(oidc).getId(), tarifaPorHora, intensidadHoraria);
            redireccion.addFlashAttribute("mensaje", "Tu contrapropuesta ha sido enviada.");
        } catch (SolicitudNoEncontradaException noExiste) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (SolicitudNoPermitidaException | IllegalArgumentException | IllegalStateException noPermitida) {
            redireccion.addFlashAttribute("aviso", noPermitida.getMessage());
        }
        return "redirect:/solicitudes/" + id;
    }

    @PostMapping("/solicitudes/{id}/aceptar")
    public String aceptar(@AuthenticationPrincipal OidcUser oidc, @PathVariable Integer id,
            RedirectAttributes redireccion) {
        try {
            solicitudes.aceptar(id, usuarioActual(oidc).getId());
            redireccion.addFlashAttribute("mensaje", "Aceptaste la propuesta. La negociación ha finalizado y el servicio se encuentra activo.");
        } catch (SolicitudNoEncontradaException noExiste) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (SolicitudNoPermitidaException | IllegalStateException noPermitida) {
            redireccion.addFlashAttribute("aviso", noPermitida.getMessage());
        }
        return "redirect:/solicitudes/" + id;
    }

    @GetMapping("/notificaciones")
    public String notificaciones(@AuthenticationPrincipal OidcUser oidc, Model model) {
        Integer usuarioId = usuarioActual(oidc).getId();
        model.addAttribute("notificaciones", notificaciones.deUsuario(usuarioId));
        model.addAttribute("bandeja", notificaciones.bandeja(usuarioId, true));
        model.addAttribute("fecha", new FormatoFecha());
        return "notificacion/lista";
    }

    private String vistaFormulario(Model model, Servicio servicio) {
        model.addAttribute("servicio", servicio);
        model.addAttribute("tarifaPublicada", ServicioController.pesos(servicio.getTarifaPorHora()));
        return "solicitud/nueva";
    }

    /** The service if it exists and the signed-in user is not its offerer; 404 or 403 otherwise. */
    private Servicio servicioSolicitable(Integer id, OidcUser oidc) {
        Servicio servicio = servicios.buscarPorId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (servicio.getOfertador().getId().equals(usuarioActual(oidc).getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return servicio;
    }

    private Usuario usuarioActual(OidcUser oidc) {
        return usuarios.buscarPorGoogleSub(oidc.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    }

    /** Lets templates show a moment (date and time) in Colombia time. */
    public static final class FormatoFecha {
        public String texto(Instant momento) {
            return FECHA_HORA.format(momento);
        }
    }
}
