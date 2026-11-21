package com.atenea.unimarket.datos.modelo;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Chat privado entre un cliente y el ofertador de un servicio: una conversación por (servicio, cliente). */
@Entity
@Table(name = "conversacion")
public class Conversacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "servicio_id", nullable = false, updatable = false)
    private Servicio servicio;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cliente_id", nullable = false, updatable = false)
    private Usuario cliente;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    protected Conversacion() {
    }

    public Conversacion(Servicio servicio, Usuario cliente, Instant fechaCreacion) {
        this.servicio = servicio;
        this.cliente = cliente;
        this.fechaCreacion = fechaCreacion;
    }

    /** El ofertador no se guarda aparte: es el dueño del servicio. */
    public Usuario getOfertador() {
        return servicio.getOfertador();
    }

    /** Solo el cliente y el ofertador participan en la conversación. */
    public boolean esParticipante(Integer usuarioId) {
        return cliente.getId().equals(usuarioId) || getOfertador().getId().equals(usuarioId);
    }

    public Integer getId() {
        return id;
    }

    public Servicio getServicio() {
        return servicio;
    }

    public Usuario getCliente() {
        return cliente;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }
}