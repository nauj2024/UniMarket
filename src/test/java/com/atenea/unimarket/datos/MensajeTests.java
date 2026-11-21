package com.atenea.unimarket.datos;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.atenea.unimarket.datos.modelo.Conversacion;
import com.atenea.unimarket.datos.modelo.Mensaje;
import com.atenea.unimarket.datos.modelo.Usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;

/** Reglas del contenido de un mensaje: no vacío, sin espacios sobrantes, máximo 1000 caracteres. */
class MensajeTests {

    private static final Instant AHORA = Instant.parse("2026-11-20T15:00:00Z");

    // Dummy: el constructor de Mensaje pide una conversación y un autor, pero estos tests solo
    // miran el contenido. Se pasan null porque nunca se usan.
    private static final Conversacion CONVERSACION_DUMMY = null;
    private static final Usuario AUTOR_DUMMY = null;

    @ParameterizedTest
    @NullSource                       // prueba con null
    @ValueSource(strings = {"", "   ", "\t", "\n", " \t\r\n "}) // y con vacío o solo espacios/tab/saltos
    void rechazaContenidoNuloOEnBlanco(String contenido) {
        assertThatThrownBy(() -> new Mensaje(CONVERSACION_DUMMY, AUTOR_DUMMY, contenido, AHORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El mensaje no puede estar vacío.");
    }

    @Test
    void guardaElContenidoSinEspaciosAlInicioNiAlFinal() {
        Mensaje mensaje = new Mensaje(CONVERSACION_DUMMY, AUTOR_DUMMY, "  hola  \n", AHORA);

        assertThat(mensaje.getContenido()).isEqualTo("hola");
        assertThat(mensaje.getFechaEnvio()).isEqualTo(AHORA);
    }

    @Test
    void aceptaExactamenteElMaximoDeCaracteres() {
        String justoEnElLimite = "a".repeat(Mensaje.LONGITUD_MAXIMA);

        Mensaje mensaje = new Mensaje(CONVERSACION_DUMMY, AUTOR_DUMMY, justoEnElLimite, AHORA);

        assertThat(mensaje.getContenido()).hasSize(1000);
    }

    @Test
    void rechazaUnCaracterMasDelMaximo() {
        String unoDeMas = "a".repeat(Mensaje.LONGITUD_MAXIMA + 1);

        assertThatThrownBy(() -> new Mensaje(CONVERSACION_DUMMY, AUTOR_DUMMY, unoDeMas, AHORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El mensaje no puede superar 1000 caracteres.");
    }

    @Test
    void losEspaciosDelBordeNoCuentanParaElLimite() {
        // 1000 letras rodeadas de espacios: tras el strip() quedan 1000, así que es válido.
        String conEspacios = "  " + "a".repeat(Mensaje.LONGITUD_MAXIMA) + "  ";

        assertThat(new Mensaje(CONVERSACION_DUMMY, AUTOR_DUMMY, conEspacios, AHORA).getContenido())
                .hasSize(Mensaje.LONGITUD_MAXIMA);
    }

    @Test
    void noInterpretaHtmlAlGuardar() {
        // El escapado ocurre al mostrar (th:text), no al guardar: el texto se conserva tal cual.
        Mensaje mensaje = new Mensaje(CONVERSACION_DUMMY, AUTOR_DUMMY, "<script>alert(1)</script>", AHORA);

        assertThat(mensaje.getContenido()).isEqualTo("<script>alert(1)</script>");
    }
        @Test
    void conservaLaConversacionElAutorYElIdAsignado() {
        Conversacion conversacion = mock(Conversacion.class);
        Usuario autor = mock(Usuario.class);

        Mensaje mensaje = new Mensaje(conversacion, autor, "hola", AHORA);
        ReflectionTestUtils.setField(mensaje, "id", 5);

        assertThat(mensaje.getConversacion()).isSameAs(conversacion);
        assertThat(mensaje.getAutor()).isSameAs(autor);
        assertThat(mensaje.getId()).isEqualTo(5);
    }
}