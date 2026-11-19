package com.atenea.unimarket.negocio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrdenServicioTests {

    @Test
    void elOrdenPorDefectoEsLaFechaDePublicacionAscendente() {
        assertThat(OrdenServicio.desde(null)).isEqualTo(OrdenServicio.ANTIGUOS);
        assertThat(OrdenServicio.desde("")).isEqualTo(OrdenServicio.ANTIGUOS);
        assertThat(OrdenServicio.desde("no-existe")).isEqualTo(OrdenServicio.ANTIGUOS);
        assertThat(OrdenServicio.ANTIGUOS.getEtiqueta()).contains("antiguos");
    }

    @Test
    void reconoceLosOrdenesPorSuNombre() {
        assertThat(OrdenServicio.desde("ANTIGUOS")).isEqualTo(OrdenServicio.ANTIGUOS);
        assertThat(OrdenServicio.desde("RECIENTES")).isEqualTo(OrdenServicio.RECIENTES);
        assertThat(OrdenServicio.desde("TARIFA_ASC")).isEqualTo(OrdenServicio.TARIFA_ASC);
        assertThat(OrdenServicio.desde("TARIFA_DESC")).isEqualTo(OrdenServicio.TARIFA_DESC);
    }
}