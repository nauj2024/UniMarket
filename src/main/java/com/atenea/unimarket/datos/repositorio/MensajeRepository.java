package com.atenea.unimarket.datos.repositorio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.atenea.unimarket.datos.modelo.Mensaje;

public interface MensajeRepository extends JpaRepository<Mensaje, Integer> {

    /** Todos los mensajes de una conversación, del más antiguo al más reciente. */
    List<Mensaje> findByConversacionIdOrderByIdAsc(Integer conversacionId);

    /** Solo los mensajes nuevos: los de id mayor al último que la página ya muestra (para el polling). */
    List<Mensaje> findByConversacionIdAndIdGreaterThanOrderByIdAsc(Integer conversacionId, Integer despuesDeId);

    /**
     * El último mensaje de cada conversación en la que participa el usuario, como cliente o como
     * ofertador del servicio, del más reciente al más antiguo. Toda conversación tiene al menos un
     * mensaje porque nace con el primero, así que cada una aparece exactamente una vez. Trae en la
     * misma consulta lo que muestra la lista (autor, conversación, cliente, servicio y ofertador).
     */
    @Query("""
            select m from Mensaje m
            join fetch m.autor
            join fetch m.conversacion c
            join fetch c.cliente
            join fetch c.servicio s
            join fetch s.ofertador
            where m.id in (
                select max(otro.id) from Mensaje otro
                where otro.conversacion.cliente.id = :usuarioId
                   or otro.conversacion.servicio.ofertador.id = :usuarioId
                group by otro.conversacion.id)
            order by m.id desc
            """)
    List<Mensaje> ultimosMensajesDeLasConversacionesDe(Integer usuarioId);
}