package com.atenea.unimarket.datos.modelo;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "google_sub", nullable = false, unique = true)
    private String googleSub;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(name = "correo_institucional", nullable = false, unique = true, length = 150)
    private String correoInstitucional;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    /** Null for accounts created before the data policy existed. */
    @Column(name = "fecha_aceptacion_politica")
    private Instant fechaAceptacionPolitica;

    protected Usuario() {
    }

    public Usuario(String googleSub, String nombre, String correoInstitucional, Instant fechaCreacion) {
        this.googleSub = googleSub;
        this.nombre = nombre;
        this.correoInstitucional = correoInstitucional;
        this.fechaCreacion = fechaCreacion;
    }

    public Integer getId() {
        return id;
    }

    public String getGoogleSub() {
        return googleSub;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCorreoInstitucional() {
        return correoInstitucional;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    public Instant getFechaAceptacionPolitica() {
        return fechaAceptacionPolitica;
    }

    public void aceptarPoliticaDatos(Instant fecha) {
        this.fechaAceptacionPolitica = fecha;
    }
}
