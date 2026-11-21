package com.atenea.unimarket.datos.repositorio;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.atenea.unimarket.datos.modelo.Conversacion;

public interface ConversacionRepository extends JpaRepository<Conversacion, Integer> {

    /** La conversación de un cliente sobre un servicio, si ya existe (como máximo una). */
    Optional<Conversacion> findByServicioIdAndClienteId(Integer servicioId, Integer clienteId);
}