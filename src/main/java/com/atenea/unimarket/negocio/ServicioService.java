package com.atenea.unimarket.negocio;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import jakarta.persistence.criteria.Join;

import com.atenea.unimarket.datos.modelo.Categoria;
import com.atenea.unimarket.datos.modelo.EstadoServicio;
import com.atenea.unimarket.datos.modelo.Servicio;
import com.atenea.unimarket.datos.modelo.Usuario;
import com.atenea.unimarket.datos.repositorio.CategoriaRepository;
import com.atenea.unimarket.datos.repositorio.ServicioRepository;
import com.atenea.unimarket.datos.repositorio.UsuarioRepository;

@Service
public class ServicioService {

    private final ServicioRepository servicios;
    private final UsuarioRepository usuarios;
    private final CategoriaRepository categorias;
    private final NegociacionesEnCurso negociaciones;
    private final Clock reloj;

    public ServicioService(ServicioRepository servicios, UsuarioRepository usuarios,
            CategoriaRepository categorias, NegociacionesEnCurso negociaciones, Clock reloj) {
        this.servicios = servicios;
        this.usuarios = usuarios;
        this.categorias = categorias;
        this.negociaciones = negociaciones;
        this.reloj = reloj;
    }

    /**
     * Publishes a service for the offerer, available right away. Every registered user can
     * publish: Google login is the institutional verification (CU-03) and registration always
     * creates the profile.
     */
    @Transactional
    public Servicio publicar(Integer ofertadorId, DatosServicio datos) {
        Usuario ofertador = usuarios.findById(ofertadorId)
                .orElseThrow(() -> new IllegalArgumentException("El ofertador no existe."));
        Categoria categoria = Optional.ofNullable(datos.categoriaId())
                .flatMap(categorias::findById)
                .orElseThrow(CategoriaNoEncontradaException::new);
        Servicio servicio = new Servicio(ofertador, categoria,
                obligatorio(datos.titulo(), "título"),
                obligatorio(datos.descripcion(), "descripción"),
                requerirNumero(datos.tarifaPorHora(), "tarifa por hora"),
                requerirNumero(datos.intensidadHoraria(), "intensidad horaria"),
                opcional(datos.experiencia()),
                opcional(datos.especialidad()),
                datos.horarios() == null ? List.of() : datos.horarios(),
                Instant.now(reloj));
        return servicios.save(servicio);
    }

        /**
     * Edits a service of the offerer. Only its owner may edit it. While the service has
     * negotiations in progress the rate and weekly hours stay fixed; everything else can change.
     */
    @Transactional
    public Servicio editar(Integer servicioId, Integer ofertadorId, DatosServicio datos) {
        Servicio servicio = servicios.findById(servicioId).orElseThrow(ServicioNoEncontradoException::new);
        if (!servicio.getOfertador().getId().equals(ofertadorId)) {
            throw new EdicionNoPermitidaException();
        }
        Categoria categoria = Optional.ofNullable(datos.categoriaId())
                .flatMap(categorias::findById)
                .orElseThrow(CategoriaNoEncontradaException::new);
        int tarifa = requerirNumero(datos.tarifaPorHora(), "tarifa por hora");
        int intensidad = requerirNumero(datos.intensidadHoraria(), "intensidad horaria");
        if (servicio.cambiaCondiciones(tarifa, intensidad) && negociaciones.hayParaServicio(servicioId)) {
            throw new CondicionesBloqueadasException();
        }
        servicio.actualizar(categoria,
                obligatorio(datos.titulo(), "título"),
                obligatorio(datos.descripcion(), "descripción"),
                tarifa, intensidad,
                opcional(datos.experiencia()),
                opcional(datos.especialidad()),
                datos.horarios() == null ? List.of() : datos.horarios());
        return servicios.save(servicio);
    }

    /** Whether the rate and weekly hours of the service are locked by negotiations in progress. */
    @Transactional(readOnly = true)
    public boolean condicionesBloqueadas(Integer servicioId) {
        return negociaciones.hayParaServicio(servicioId);
    }

    @Transactional(readOnly = true)
    public Optional<Servicio> buscarPorId(Integer id) {
        return servicios.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Servicio> disponiblesEnCategoria(Integer categoriaId) {
        return servicios.findByCategoriaIdAndEstadoOrderByFechaPublicacionAsc(categoriaId, EstadoServicio.DISPONIBLE);
    }

    @Transactional(readOnly = true)
    public List<Servicio> disponiblesDeOfertador(Integer ofertadorId) {
        return servicios.findByOfertadorIdAndEstadoOrderByFechaPublicacionAsc(ofertadorId, EstadoServicio.DISPONIBLE);
    }

    @Transactional(readOnly = true)
    public List<Categoria> categorias() {
        return categorias.findAllByOrderByNombreAsc();
    }

    @Transactional(readOnly = true)
    public Optional<Categoria> buscarCategoria(Integer id) {
        return categorias.findById(id);
    }
    @Transactional(readOnly = true)
    public Page<Servicio> buscarDisponibles(List<Integer> categoriaIds, String texto, OrdenServicio orden, int pagina) {
        return servicios.findAll(criteriosDisponibles(categoriaIds, texto),
                PageRequest.of(pagina, 20, orden.getSort().and(Sort.by("id"))));
    }

    private Specification<Servicio> criteriosDisponibles(List<Integer> categoriaIds, String texto) {
        Specification<Servicio> criterios = (servicio, consulta, cb) ->
                cb.equal(servicio.get("estado"), EstadoServicio.DISPONIBLE);
        if (categoriaIds != null && !categoriaIds.isEmpty()) {
            criterios = criterios.and((servicio, consulta, cb) ->
                    servicio.get("categoria").get("id").in(categoriaIds));
        }
        String limpio = opcional(texto);
        if (limpio != null) {
            String patron = "%" + escaparLike(limpio) + "%";
            criterios = criterios.and((servicio, consulta, cb) -> {
                Join<Servicio, Usuario> ofertador = servicio.join("ofertador");
                return cb.or(
                        cb.like(cb.lower(servicio.<String>get("titulo")), cb.lower(cb.literal(patron)), '\\'),
                        cb.like(cb.lower(servicio.<String>get("descripcion")), cb.lower(cb.literal(patron)), '\\'),
                        cb.like(cb.lower(servicio.<String>get("especialidad")), cb.lower(cb.literal(patron)), '\\'),
                        cb.like(cb.lower(servicio.<String>get("experiencia")), cb.lower(cb.literal(patron)), '\\'),
                        cb.like(cb.lower(ofertador.<String>get("nombre")), cb.lower(cb.literal(patron)), '\\'));
            });
        }
        return criterios;
    }

    /** Los caracteres comodín del usuario se buscan literalmente. */
    private static String escaparLike(String valor) {
        return valor.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String obligatorio(String valor, String campo) {
        String limpio = opcional(valor);
        if (limpio == null) {
            throw new IllegalArgumentException("Falta " + campo + ".");
        }
        return limpio;
    }

    private static int requerirNumero(Integer valor, String campo) {
        if (valor == null) {
            throw new IllegalArgumentException("Falta " + campo + ".");
        }
        return valor;
    }

    private static String opcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
