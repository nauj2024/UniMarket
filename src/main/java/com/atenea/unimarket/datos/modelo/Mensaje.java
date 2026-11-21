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

/** Un mensaje de una conversación, con su autor y su fecha. No se edita ni se borra. */
@Entity
@Table(name = "mensaje")
public class Mensaje {

    /** Debe coincidir con el CHECK de la tabla mensaje (migración V9). */
    public static final int LONGITUD_MAXIMA = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "conversacion_id", nullable = false, updatable = false)
    private Conversacion conversacion;

    @ManyToOne(optional = false)
    @JoinColumn(name = "autor_id", nullable = false, updatable = false)
    private Usuario autor;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String contenido;

    @Column(name = "fecha_envio", nullable = false, updatable = false)
    private Instant fechaEnvio;

    protected Mensaje() {
    }

    public Mensaje(Conversacion conversacion, Usuario autor, String contenido, Instant fechaEnvio) {
        if (contenido == null || contenido.isBlank()) {
            throw new IllegalArgumentException("El mensaje no puede estar vacío.");
        }
        String limpio = contenido.strip();
        if (limpio.length() > LONGITUD_MAXIMA) {
            throw new IllegalArgumentException("El mensaje no puede superar " + LONGITUD_MAXIMA + " caracteres.");
        }
        this.conversacion = conversacion;
        this.autor = autor;
        this.contenido = limpio;
        this.fechaEnvio = fechaEnvio;
    }

    public Integer getId() {
        return id;
    }

    public Conversacion getConversacion() {
        return conversacion;
    }

    public Usuario getAutor() {
        return autor;
    }

    public String getContenido() {
        return contenido;
    }

    public Instant getFechaEnvio() {
        return fechaEnvio;
    }
}