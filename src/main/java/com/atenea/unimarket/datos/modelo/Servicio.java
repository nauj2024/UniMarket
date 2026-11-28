package com.atenea.unimarket.datos.modelo;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "servicio")
public class Servicio {

    public static final int INTENSIDAD_MAXIMA = 168;
    public static final int LARGO_TITULO = 100;
    public static final int LARGO_ESPECIALIDAD = 100;
    public static final int LARGO_TEXTO = 5000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ofertador_id", nullable = false, updatable = false)
    private Usuario ofertador;

    @ManyToOne(optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(nullable = false, length = 100)
    private String titulo;

    @Column(nullable = false, columnDefinition = "text")
    private String descripcion;

    /** Whole Colombian pesos per hour. */
    @Column(name = "tarifa_por_hora", nullable = false)
    private Integer tarifaPorHora;

    /** Hours per week. */
    @Column(name = "intensidad_horaria", nullable = false)
    private Integer intensidadHoraria;

    @Column(columnDefinition = "text")
    private String experiencia;

    @Column(length = 100)
    private String especialidad;

    @Convert(converter = ConvertidorFranjasHorario.class)
    @Column(name = "horarios_disponibilidad", columnDefinition = "text")
    private List<FranjaHorario> horariosDisponibilidad = List.of();

    @Column(name = "fecha_publicacion", nullable = false, updatable = false)
    private Instant fechaPublicacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoServicio estado;

    protected Servicio() {
    }

    /** Creates a published service: it starts {@link EstadoServicio#DISPONIBLE}. */
    public Servicio(Usuario ofertador, Categoria categoria, String titulo, String descripcion,
            int tarifaPorHora, int intensidadHoraria, String experiencia, String especialidad,
            List<FranjaHorario> horariosDisponibilidad, Instant fechaPublicacion) {
        validar(titulo, descripcion, tarifaPorHora, intensidadHoraria, experiencia, especialidad);
        this.ofertador = ofertador;
        this.categoria = categoria;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.tarifaPorHora = tarifaPorHora;
        this.intensidadHoraria = intensidadHoraria;
        this.experiencia = experiencia;
        this.especialidad = especialidad;
        this.horariosDisponibilidad = List.copyOf(horariosDisponibilidad);
        this.fechaPublicacion = fechaPublicacion;
        this.estado = EstadoServicio.DISPONIBLE;
    }

    /**
     * Replaces the offer's editable data with the same rules as publishing. The offerer, the
     * state and the publication date never change here.
     */
    public void actualizar(Categoria categoria, String titulo, String descripcion, int tarifaPorHora,
            int intensidadHoraria, String experiencia, String especialidad,
            List<FranjaHorario> horariosDisponibilidad) {
        validar(titulo, descripcion, tarifaPorHora, intensidadHoraria, experiencia, especialidad);
        this.categoria = categoria;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.tarifaPorHora = tarifaPorHora;
        this.intensidadHoraria = intensidadHoraria;
        this.experiencia = experiencia;
        this.especialidad = especialidad;
        this.horariosDisponibilidad = List.copyOf(horariosDisponibilidad);
    }

    public void activar(){
        this.estado = EstadoServicio.ACTIVO;
    }

    public void cerrar() {
        if (this.estado != EstadoServicio.ACTIVO) {
            throw new IllegalStateException("Solo se puede cerrar un servicio que esté activo.");
        }
        this.estado = EstadoServicio.CERRADO;
    }
    
    /** Whether the given rate or weekly hours differ from the ones published. */
    public boolean cambiaCondiciones(int tarifaPorHora, int intensidadHoraria) {
        return !this.tarifaPorHora.equals(tarifaPorHora) || !this.intensidadHoraria.equals(intensidadHoraria);
    }

    private static void validar(String titulo, String descripcion, int tarifaPorHora, int intensidadHoraria,
            String experiencia, String especialidad) {
        if (tarifaPorHora <= 0) {
            throw new IllegalArgumentException("La tarifa por hora debe ser mayor que cero.");
        }
        if (intensidadHoraria < 1 || intensidadHoraria > INTENSIDAD_MAXIMA) {
            throw new IllegalArgumentException("La intensidad horaria debe estar entre 1 y 168 horas por semana.");
        }
        limitarLargo(titulo, LARGO_TITULO, "título");
        limitarLargo(descripcion, LARGO_TEXTO, "descripción");
        limitarLargo(experiencia, LARGO_TEXTO, "experiencia");
        limitarLargo(especialidad, LARGO_ESPECIALIDAD, "especialidad");
    }

    private static void limitarLargo(String valor, int maximo, String campo) {
        if (valor != null && valor.length() > maximo) {
            throw new IllegalArgumentException("El campo " + campo + " supera " + maximo + " caracteres.");
        }
    }

    public Integer getId() {
        return id;
    }

    public Usuario getOfertador() {
        return ofertador;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public Integer getTarifaPorHora() {
        return tarifaPorHora;
    }

    public Integer getIntensidadHoraria() {
        return intensidadHoraria;
    }

    public String getExperiencia() {
        return experiencia;
    }

    public String getEspecialidad() {
        return especialidad;
    }

    public List<FranjaHorario> getHorariosDisponibilidad() {
        return horariosDisponibilidad;
    }

    public Instant getFechaPublicacion() {
        return fechaPublicacion;
    }

    public EstadoServicio getEstado() {
        return estado;
    }
}
