package com.atenea.unimarket.datos;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.atenea.unimarket.datos.modelo.Categoria;
import com.atenea.unimarket.datos.modelo.Conversacion;
import com.atenea.unimarket.datos.modelo.Servicio;
import com.atenea.unimarket.datos.modelo.Usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Quién participa en una conversación y qué datos expone. */
class ConversacionTests {

    private static final Instant AHORA = Instant.parse("2026-11-20T15:00:00Z");

    Usuario ana;     // ofertadora, id 1
    Usuario beto;    // cliente, id 2
    Usuario carla;   // ajena, id 3
    Servicio servicio;
    Conversacion conversacion;

    @BeforeEach
    void preparar() {
        ana = usuario(1, "ana");
        beto = usuario(2, "beto");
        carla = usuario(3, "carla");
        servicio = new Servicio(ana, mock(Categoria.class), "Cálculo I", "Tutorías de cálculo.",
                25000, 4, null, null, List.of(), AHORA);
        conversacion = new Conversacion(servicio, beto, AHORA);
        ReflectionTestUtils.setField(conversacion, "id", 7);
    }

    @Test
    void exponeLosDatosConLosQueSeCreo() {
        assertThat(conversacion.getId()).isEqualTo(7);
        assertThat(conversacion.getServicio()).isSameAs(servicio);
        assertThat(conversacion.getCliente()).isSameAs(beto);
        assertThat(conversacion.getFechaCreacion()).isEqualTo(AHORA);
    }

    @Test
    void elOfertadorEsElDuenoDelServicio() {
        assertThat(conversacion.getOfertador()).isSameAs(ana);
    }

    @Test
    void soloElClienteYElOfertadorParticipan() {
        assertThat(conversacion.esParticipante(beto.getId())).isTrue();
        assertThat(conversacion.esParticipante(ana.getId())).isTrue();
        assertThat(conversacion.esParticipante(carla.getId())).isFalse();
    }

    private static Usuario usuario(int id, String nombre) {
        Usuario usuario = new Usuario("sub-" + nombre, nombre, nombre + "@unal.edu.co", AHORA);
        ReflectionTestUtils.setField(usuario, "id", id);
        return usuario;
    }
}