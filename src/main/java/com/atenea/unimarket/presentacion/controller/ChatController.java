package com.atenea.unimarket.presentacion.controller;

import java.util.List;
import java.util.Optional;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.atenea.unimarket.datos.modelo.Conversacion;
import com.atenea.unimarket.datos.modelo.Mensaje;
import com.atenea.unimarket.datos.modelo.Servicio;
import com.atenea.unimarket.datos.modelo.Usuario;
import com.atenea.unimarket.negocio.ChatNoPermitidoException;
import com.atenea.unimarket.negocio.ChatService;
import com.atenea.unimarket.negocio.ConversacionNoEncontradaException;
import com.atenea.unimarket.negocio.ServicioNoEncontradoException;
import com.atenea.unimarket.negocio.UsuarioService;
import com.atenea.unimarket.presentacion.formulario.MensajeFormulario;

/** CHAT-USR-H01: chat privado entre el cliente y el ofertador de un servicio. */
@Controller
public class ChatController {

    private final ChatService chat;
    private final UsuarioService usuarios;

    public ChatController(ChatService chat, UsuarioService usuarios) {
        this.chat = chat;
        this.usuarios = usuarios;
    }

    /**
     * Entrada desde el detalle del servicio. Si el cliente ya tiene conversación con este servicio
     * lo lleva a ella; si no, muestra el formulario del primer mensaje (la conversación todavía
     * no se crea: nace al enviarlo).
     */
    @GetMapping("/servicios/{id}/chat")
    public String entrada(@AuthenticationPrincipal OidcUser oidc, @PathVariable Integer id, Model model,
            RedirectAttributes redireccion) {
        Usuario yo = usuarioActual(oidc);
        Optional<Conversacion> existente = chat.conversacionExistente(id, yo.getId());
        if (existente.isPresent()) {
            return "redirect:/conversaciones/" + existente.get().getId();
        }
        try {
            Servicio servicio = chat.servicioParaChat(id, yo.getId());
            model.addAttribute("nuevoMensaje", new MensajeFormulario());
            return vistaNueva(model, servicio, yo);
        } catch (ServicioNoEncontradoException noExiste) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (ChatNoPermitidoException noPermitido) {
            redireccion.addFlashAttribute("aviso", noPermitido.getMessage());
            return "redirect:/servicios";
        }
    }

    /** Primer mensaje: crea la conversación y lo guarda. */
    @PostMapping("/servicios/{id}/chat")
    public String primerMensaje(@AuthenticationPrincipal OidcUser oidc, @PathVariable Integer id,
            @Validated @ModelAttribute("nuevoMensaje") MensajeFormulario formulario, BindingResult errores,
            Model model, RedirectAttributes redireccion) {
        Usuario yo = usuarioActual(oidc);
        try {
            if (errores.hasErrors()) {
                return vistaNueva(model, chat.servicioParaChat(id, yo.getId()), yo);
            }
            Conversacion conversacion = chat.enviarAlOfertador(yo.getId(), id, formulario.getContenido());
            return "redirect:/conversaciones/" + conversacion.getId();
        } catch (ServicioNoEncontradoException noExiste) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (ChatNoPermitidoException noPermitido) {
            redireccion.addFlashAttribute("aviso", noPermitido.getMessage());
            return "redirect:/servicios";
        }
    }

    /** CHAT-USR-H02: las conversaciones del usuario, la más reciente primero. */
    @GetMapping("/conversaciones")
    public String lista(@AuthenticationPrincipal OidcUser oidc, Model model) {
        Usuario yo = usuarioActual(oidc);
        model.addAttribute("conversaciones", chat.conversacionesDe(yo.getId()));
        model.addAttribute("yoId", yo.getId());
        model.addAttribute("fecha", new SolicitudCompraController.FormatoFecha());
        return "conversacion/lista";
    }

    /** La conversación con sus mensajes. Solo la ven el cliente y el ofertador. */
    @GetMapping("/conversaciones/{id}")
    public String ver(@AuthenticationPrincipal OidcUser oidc, @PathVariable Integer id, Model model) {
        Usuario yo = usuarioActual(oidc);
        model.addAttribute("nuevoMensaje", new MensajeFormulario());
        return vistaConversacion(model, conversacion(id, yo), yo);
    }

    /** Un mensaje nuevo en una conversación que ya existe. */
    @PostMapping("/conversaciones/{id}/mensajes")
    public String enviar(@AuthenticationPrincipal OidcUser oidc, @PathVariable Integer id,
            @Validated @ModelAttribute("nuevoMensaje") MensajeFormulario formulario, BindingResult errores,
            Model model) {
        Usuario yo = usuarioActual(oidc);
        Conversacion conversacion = conversacion(id, yo);
        if (errores.hasErrors()) {
            return vistaConversacion(model, conversacion, yo);
        }
        chat.enviar(id, yo.getId(), formulario.getContenido());
        return "redirect:/conversaciones/" + id;
    }
    
        /**
     * Los mensajes posteriores al id {@code despues}. La página lo consulta cada pocos segundos
     * (polling) para mostrar los mensajes nuevos sin recargar.
     */
    @GetMapping("/api/conversaciones/{id}/mensajes")
    @ResponseBody
    public List<MensajeJson> mensajesNuevos(@AuthenticationPrincipal OidcUser oidc, @PathVariable Integer id,
            @RequestParam(defaultValue = "0") Integer despues) {
        Usuario yo = usuarioActual(oidc);
        conversacion(id, yo); // responde 404 si no existe o el usuario no participa
        SolicitudCompraController.FormatoFecha fecha = new SolicitudCompraController.FormatoFecha();
        return chat.mensajesNuevos(id, yo.getId(), despues).stream()
                .map(mensaje -> MensajeJson.desde(mensaje, yo, fecha))
                .toList();
    }

    /** La conversación si el usuario participa en ella; si no, responde 404 como si no existiera. */
    private Conversacion conversacion(Integer id, Usuario yo) {
        try {
            return chat.conversacionDe(id, yo.getId());
        } catch (ConversacionNoEncontradaException noExiste) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private String vistaNueva(Model model, Servicio servicio, Usuario yo) {
        return vista(model, servicio, servicio.getOfertador().getNombre(), yo, List.of(),
                "/servicios/" + servicio.getId() + "/chat");
    }

    private String vistaConversacion(Model model, Conversacion conversacion, Usuario yo) {
        Usuario otraPersona = conversacion.getCliente().getId().equals(yo.getId())
                ? conversacion.getOfertador()
                : conversacion.getCliente();
        model.addAttribute("urlMensajes", "/api/conversaciones/" + conversacion.getId() + "/mensajes");
        return vista(model, conversacion.getServicio(), otraPersona.getNombre(), yo,
                chat.mensajesDe(conversacion.getId(), yo.getId()),
                "/conversaciones/" + conversacion.getId() + "/mensajes");
    }

    /** Lo que la página del chat necesita, sea una conversación nueva o una existente. */
    private String vista(Model model, Servicio servicio, String otraPersona, Usuario yo, List<Mensaje> mensajes,
            String accionEnvio) {
        model.addAttribute("servicio", servicio);
        model.addAttribute("otraPersona", otraPersona);
        model.addAttribute("yoId", yo.getId());
        model.addAttribute("mensajes", mensajes);
        model.addAttribute("accionEnvio", accionEnvio);
        model.addAttribute("limite", Mensaje.LONGITUD_MAXIMA);
        model.addAttribute("fecha", new SolicitudCompraController.FormatoFecha());
        return "conversacion/ver";
    }

    private Usuario usuarioActual(OidcUser oidc) {
        return usuarios.buscarPorGoogleSub(oidc.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    }
        /** Un mensaje tal como lo recibe el JavaScript de la página. */
    public record MensajeJson(Integer id, String autor, boolean propio, String contenido, String fechaIso,
            String fechaTexto) {
        static MensajeJson desde(Mensaje mensaje, Usuario yo, SolicitudCompraController.FormatoFecha fecha) {
            return new MensajeJson(mensaje.getId(), mensaje.getAutor().getNombre(),
                    mensaje.getAutor().getId().equals(yo.getId()), mensaje.getContenido(),
                    mensaje.getFechaEnvio().toString(), fecha.texto(mensaje.getFechaEnvio()));
        }
    }
}