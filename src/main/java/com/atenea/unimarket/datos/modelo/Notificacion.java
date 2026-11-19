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

/** A message for a user about a purchase request. */
@Entity
@Table(name = "notificacion")
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @ManyToOne(optional = false)
    @JoinColumn(name = "solicitud_id", nullable = false, updatable = false)
    private SolicitudCompra solicitud;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String mensaje;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private TipoNotificacion tipo;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @Column(nullable = false)
    private boolean leida;

    @Column(nullable = false)
    private boolean descartada;

    public boolean isLeida() {
        return leida;
    }

    public boolean isDescartada() {
        return descartada;
    }

    protected Notificacion() {
    }

    public Notificacion(Usuario usuario, SolicitudCompra solicitud, TipoNotificacion tipo, String mensaje,
            Instant fechaCreacion) {
        if (mensaje == null || mensaje.isBlank()) {
            throw new IllegalArgumentException("La notificación necesita un mensaje.");
        }
        this.usuario = usuario;
        this.solicitud = solicitud;
        this.tipo = tipo;
        this.mensaje = mensaje;
        this.fechaCreacion = fechaCreacion;
    }

    public Integer getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public SolicitudCompra getSolicitud() {
        return solicitud;
    }

    public String getMensaje() {
        return mensaje;
    }

    public TipoNotificacion getTipo() {
        return tipo;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }
}
