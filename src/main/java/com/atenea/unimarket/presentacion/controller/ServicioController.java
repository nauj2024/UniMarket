package com.atenea.unimarket.presentacion.controller;

import java.text.NumberFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
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


import com.atenea.unimarket.datos.modelo.EstadoServicio;
import com.atenea.unimarket.negocio.OrdenServicio;
import com.atenea.unimarket.datos.modelo.Categoria;
import com.atenea.unimarket.datos.modelo.DiaSemana;
import com.atenea.unimarket.datos.modelo.Servicio;
import com.atenea.unimarket.datos.modelo.Usuario;
import com.atenea.unimarket.negocio.CategoriaNoEncontradaException;
import com.atenea.unimarket.negocio.CondicionesBloqueadasException;
import com.atenea.unimarket.negocio.ServicioService;
import com.atenea.unimarket.negocio.UsuarioService;
import com.atenea.unimarket.presentacion.formulario.FranjaFormulario;
import com.atenea.unimarket.presentacion.formulario.ServicioFormulario;

@Controller
public class ServicioController {

    private static final Locale COLOMBIA = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", COLOMBIA);

    private final ServicioService servicios;
    private final UsuarioService usuarios;

    public ServicioController(ServicioService servicios, UsuarioService usuarios) {
        this.servicios = servicios;
        this.usuarios = usuarios;
    }

    @ModelAttribute("dias")
    DiaSemana[] dias() {
        return DiaSemana.values();
    }
    @GetMapping("/servicios")
    public String catalogo(@RequestParam(required = false) String orden,
            @RequestParam(name = "categoria", required = false) List<Integer> categoriaIds,
            @RequestParam(name = "q", required = false) String texto,
            @RequestParam(defaultValue = "0") int pagina, Model model) {
        model.addAttribute("catalogoInicial", Map.of(
                "resultado", buscar(orden, categoriaIds, texto, pagina),
                "categorias", servicios.categorias().stream()
                        .map(c -> new OpcionCatalogo(c.getId().toString(), c.getNombre())).toList(),
                "ordenes", List.of(OrdenServicio.values()).stream()
                        .map(o -> new OpcionCatalogo(o.name(), o.getEtiqueta())).toList()));
        return "servicio/lista";
    }

    @GetMapping("/api/servicios")
    @ResponseBody
    public ResultadoCatalogo buscar(@RequestParam(required = false) String orden,
            @RequestParam(name = "categoria", required = false) List<Integer> categoriaIds,
            @RequestParam(name = "q", required = false) String texto,
            @RequestParam(defaultValue = "0") int pagina) {
        String busqueda = validarBusqueda(texto, pagina);
        return ResultadoCatalogo.desde(servicios.buscarDisponibles(
                categoriaIds == null ? List.of() : categoriaIds, busqueda, OrdenServicio.desde(orden), pagina));
    }

    private static String validarBusqueda(String texto, int pagina) {
        if (pagina < 0 || pagina > 100000 || (texto != null && texto.length() > 100)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        return texto == null ? "" : texto.strip();
    }

    public record TarjetaServicio(Integer id, String titulo, String categoria, String ofertador,
            int tarifaPorHora, int intensidadHoraria) {
        static TarjetaServicio desde(Servicio servicio) {
            return new TarjetaServicio(servicio.getId(), servicio.getTitulo(), servicio.getCategoria().getNombre(),
                    servicio.getOfertador().getNombre(), servicio.getTarifaPorHora(), servicio.getIntensidadHoraria());
        }
    }

    public record ResultadoCatalogo(List<TarjetaServicio> servicios, long total, int pagina, boolean hayMas) {
        static ResultadoCatalogo desde(Page<Servicio> pagina) {
            return new ResultadoCatalogo(pagina.getContent().stream().map(TarjetaServicio::desde).toList(),
                    pagina.getTotalElements(), pagina.getNumber(), pagina.hasNext());
        }
    }

    public record OpcionCatalogo(String value, String label) {}

    @GetMapping("/servicios/nuevo")
    public String formulario(Model model) {
        model.addAttribute("servicio", new ServicioFormulario());
        model.addAttribute("categorias", servicios.categorias());
        return "servicio/formulario";
    }

    /** Adds or removes a slot row without JavaScript; the form is re-rendered without validating. */
    @PostMapping(path = "/servicios/nuevo", params = {"accion"})
    public String editarFranjas(@ModelAttribute("servicio") ServicioFormulario formulario,
            BindingResult erroresDeConversion, @RequestParam String accion, Model model) {
        FranjaFormulario.aplicarAccion(formulario.getFranjas(), accion);
        model.addAttribute("categorias", servicios.categorias());
        return "servicio/formulario";
    }

    @PostMapping("/servicios/nuevo")
    public String publicar(@AuthenticationPrincipal OidcUser oidc,
            @Validated @ModelAttribute("servicio") ServicioFormulario formulario, BindingResult errores,
            RedirectAttributes redireccion, Model model) {
        if (formulario.getCategoriaId() != null && servicios.buscarCategoria(formulario.getCategoriaId()).isEmpty()) {
            errores.rejectValue("categoriaId", "categoria.inexistente", "Selecciona una categoría de la lista.");
        }
        if (errores.hasErrors()) {
            model.addAttribute("categorias", servicios.categorias());
            return "servicio/formulario";
        }
        Servicio servicio;
        try {
            servicio = servicios.publicar(usuarioActual(oidc).getId(), formulario.aDatos());
        } catch (CategoriaNoEncontradaException noExiste) {
            errores.rejectValue("categoriaId", "categoria.inexistente", "Selecciona una categoría de la lista.");
            model.addAttribute("categorias", servicios.categorias());
            return "servicio/formulario";
        }
        redireccion.addFlashAttribute("mensaje", "Tu servicio fue publicado.");
        return "redirect:/servicios/" + servicio.getId();
    }

    @GetMapping("/servicios/{id}/editar")
    public String formularioEdicion(@AuthenticationPrincipal OidcUser oidc, @PathVariable Integer id, Model model) {
        Servicio servicio = servicioDelOfertador(id, oidc);
        model.addAttribute("servicio", ServicioFormulario.desde(servicio));
        prepararEdicion(model, id);
        return "servicio/formulario";
    }

    /** Adds or removes a slot row while editing; same as for a new service. */
    @PostMapping(path = "/servicios/{id}/editar", params = {"accion"})
    public String editarFranjasAlEditar(@AuthenticationPrincipal OidcUser oidc, @PathVariable Integer id,
            @ModelAttribute("servicio") ServicioFormulario formulario, BindingResult erroresDeConversion,
            @RequestParam String accion, Model model) {
        servicioDelOfertador(id, oidc);
        FranjaFormulario.aplicarAccion(formulario.getFranjas(), accion);
        prepararEdicion(model, id);
        return "servicio/formulario";
    }

    @PostMapping("/servicios/{id}/editar")
    public String editar(@AuthenticationPrincipal OidcUser oidc, @PathVariable Integer id,
            @Validated @ModelAttribute("servicio") ServicioFormulario formulario, BindingResult errores,
            RedirectAttributes redireccion, Model model) {
        servicioDelOfertador(id, oidc);
        if (formulario.getCategoriaId() != null && servicios.buscarCategoria(formulario.getCategoriaId()).isEmpty()) {
            errores.rejectValue("categoriaId", "categoria.inexistente", "Selecciona una categoría de la lista.");
        }
        if (errores.hasErrors()) {
            prepararEdicion(model, id);
            return "servicio/formulario";
        }
        try {
            servicios.editar(id, usuarioActual(oidc).getId(), formulario.aDatos());
        } catch (CategoriaNoEncontradaException noExiste) {
            errores.rejectValue("categoriaId", "categoria.inexistente", "Selecciona una categoría de la lista.");
            prepararEdicion(model, id);
            return "servicio/formulario";
        } catch (CondicionesBloqueadasException bloqueadas) {
            errores.reject("condiciones.bloqueadas", bloqueadas.getMessage());
            prepararEdicion(model, id);
            return "servicio/formulario";
        }
        redireccion.addFlashAttribute("mensaje", "Tu servicio fue actualizado.");
        return "redirect:/servicios/" + id;
    }

    @GetMapping("/servicios/{id}")
    public String ver(@AuthenticationPrincipal OidcUser oidc, @PathVariable Integer id, Model model, RedirectAttributes redireccion) {
        Servicio servicio = servicios.buscarPorId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ZonedDateTime publicacion = servicio.getFechaPublicacion().atZone(PerfilController.ZONA);
        boolean esOfertador = usuarios.buscarPorGoogleSub(oidc.getSubject())
                .map(usuario -> usuario.getId().equals(servicio.getOfertador().getId()))
                .orElse(false);
                if (servicio.getEstado() != EstadoServicio.DISPONIBLE && !esOfertador) {
                    redireccion.addFlashAttribute("aviso", "Este servicio ya no está disponible.");
                    return "redirect:/servicios";
                }
        model.addAttribute("servicio", servicio);
        model.addAttribute("esOfertador", esOfertador);
        model.addAttribute("tarifa", pesos(servicio.getTarifaPorHora()));
        model.addAttribute("fechaPublicacionIso", publicacion.toOffsetDateTime().toString());
        model.addAttribute("fechaPublicacionTexto", FECHA.format(publicacion));
        return "servicio/ver";
    }

    @GetMapping("/categorias")
    public String categorias(Model model) {
        model.addAttribute("categorias", servicios.categorias());
        return "categoria/lista";
    }

    @GetMapping("/categorias/{id}")
    public String categoria(@PathVariable Integer id, Model model) {
        Categoria categoria = servicios.buscarCategoria(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("categoria", categoria);
        model.addAttribute("servicios", servicios.disponiblesEnCategoria(id));
        model.addAttribute("formato", new FormatoPesos());
        return "categoria/ver";
    }

    static String pesos(int valor) {
        NumberFormat formato = NumberFormat.getIntegerInstance(COLOMBIA);
        return "$" + formato.format(valor) + " COP";
    }

    /** Lets templates format a list of rates without a helper per item. */
    public static final class FormatoPesos {
        public String pesos(int valor) {
            return ServicioController.pesos(valor);
        }
    }

    private Usuario usuarioActual(OidcUser oidc) {
        return usuarios.buscarPorGoogleSub(oidc.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    }

    /** The service if it exists and belongs to the signed-in user; 404 or 403 otherwise. */
    private Servicio servicioDelOfertador(Integer id, OidcUser oidc) {
        Servicio servicio = servicios.buscarPorId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!servicio.getOfertador().getId().equals(usuarioActual(oidc).getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return servicio;
    }

    private void prepararEdicion(Model model, Integer id) {
        model.addAttribute("categorias", servicios.categorias());
        model.addAttribute("editando", true);
        model.addAttribute("servicioId", id);
        model.addAttribute("bloqueado", servicios.condicionesBloqueadas(id));
    }
}
