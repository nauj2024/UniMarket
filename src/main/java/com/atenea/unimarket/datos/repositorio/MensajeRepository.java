package com.atenea.unimarket.datos.repositorio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.atenea.unimarket.datos.modelo.Mensaje;

public interface MensajeRepository extends JpaRepository<Mensaje, Integer> {

    /** Todos los mensajes de una conversación, del más antiguo al más reciente. */
    List<Mensaje> findByConversacionIdOrderByIdAsc(Integer conversacionId);

    /** Solo los mensajes nuevos: los de id mayor al último que la página ya muestra (para el polling). */
    List<Mensaje> findByConversacionIdAndIdGreaterThanOrderByIdAsc(Integer conversacionId, Integer despuesDeId);
}