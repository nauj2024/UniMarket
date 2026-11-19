package com.atenea.unimarket.presentacion.formulario;

import java.util.ArrayList;
import java.util.List;

import com.atenea.unimarket.datos.modelo.FranjaHorario;
import com.atenea.unimarket.negocio.PerfilService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PerfilFormulario {

    @NotBlank(message = "Ingresa tu nombre.")
    @Size(max = 100, message = "El nombre puede tener máximo 100 caracteres.")
    private String nombre;

    @NotBlank(message = "Ingresa un teléfono de contacto.")
    @Pattern(regexp = PerfilService.FORMATO_TELEFONO,
            message = "Ingresa un celular colombiano de 10 dígitos que empiece por 3, por ejemplo 3001234567.")
    private String telefonoContacto;

    @Valid
    private List<FranjaFormulario> franjas = new ArrayList<>();

    @AssertTrue(message = "Debes aceptar la política de tratamiento de datos personales.")
    private boolean aceptaPolitica;

    public List<FranjaHorario> horarios() {
        return franjas.stream().map(FranjaFormulario::aFranja).toList();
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getTelefonoContacto() {
        return telefonoContacto;
    }

    public void setTelefonoContacto(String telefonoContacto) {
        this.telefonoContacto = telefonoContacto;
    }

    public List<FranjaFormulario> getFranjas() {
        return franjas;
    }

    public void setFranjas(List<FranjaFormulario> franjas) {
        this.franjas = franjas;
    }

    public boolean isAceptaPolitica() {
        return aceptaPolitica;
    }

    public void setAceptaPolitica(boolean aceptaPolitica) {
        this.aceptaPolitica = aceptaPolitica;
    }
}
