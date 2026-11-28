package com.atenea.unimarket.datos.modelo;

import java.time.Instant;

import jakarta.persistence.Column;
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
@Table(name = "solicitud_compra")
public class SolicitudCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cliente_id", nullable = false, updatable = false)
    private Usuario cliente;

    @ManyToOne(optional = false)
    @JoinColumn(name = "servicio_id", nullable = false, updatable = false)
    private Servicio servicio;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_ultima_actividad", nullable = false)
    private Instant fechaUltimaActividad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSolicitud estado;

    protected SolicitudCompra() {
    }

/** Creates a request that starts {@link EstadoSolicitud#NEGOCIANDO}. */
    public SolicitudCompra(Usuario cliente, Servicio servicio, Instant ahora) {
        this.cliente = cliente;
        this.servicio = servicio;
        this.fechaCreacion = ahora;
        this.fechaUltimaActividad = ahora;
        this.estado = EstadoSolicitud.NEGOCIANDO;
    }

    /** Records that a proposal was exchanged. Only while negotiating. */
    public void registrarActividad(Instant ahora) {
        exigirNegociando();
        this.fechaUltimaActividad = ahora;
    }

    /** An agreement was reached: the request becomes active. */
    public void activar(Instant ahora) {
        exigirNegociando();
        this.estado = EstadoSolicitud.ACTIVA;
        this.fechaUltimaActividad = ahora;
    }

    /** The negotiation ended without an agreement. */
    public void cancelar(Instant ahora) {
        exigirNegociando();
        this.estado = EstadoSolicitud.CANCELADA;
        this.fechaUltimaActividad = ahora;
    }

    /** COMPRA-CLI-H05: the client confirms the service finished; the request becomes closed. */
    public void cerrar(Instant ahora) {
        if (estado != EstadoSolicitud.ACTIVA) {
            throw new IllegalStateException("Solo se puede cerrar una solicitud que esté activa.");
        }
        this.estado = EstadoSolicitud.CERRADA;
        this.fechaUltimaActividad = ahora;
    }

    private void exigirNegociando() {
        if (estado != EstadoSolicitud.NEGOCIANDO) {
            throw new IllegalStateException("La solicitud ya no está en negociación.");
        }
    }

    public Integer getId() {
        return id;
    }

    public Usuario getCliente() {
        return cliente;
    }

    public Servicio getServicio() {
        return servicio;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    public Instant getFechaUltimaActividad() {
        return fechaUltimaActividad;
    }

    public EstadoSolicitud getEstado() {
        return estado;
    }
}
