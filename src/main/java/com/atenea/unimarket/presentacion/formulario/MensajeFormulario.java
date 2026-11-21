package com.atenea.unimarket.presentacion.formulario;

import com.atenea.unimarket.datos.modelo.Mensaje;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Lo que el usuario envía al escribir en el chat: el texto del mensaje. */
public class MensajeFormulario {

    @NotBlank(message = "Escribe un mensaje.")
    @Size(max = Mensaje.LONGITUD_MAXIMA, message = "El mensaje no puede superar {max} caracteres.")
    private String contenido;

    public String getContenido() {
        return contenido;
    }

    public void setContenido(String contenido) {
        this.contenido = contenido;
    }
}