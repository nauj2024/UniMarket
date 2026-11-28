package com.atenea.unimarket.datos.repositorio;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import com.atenea.unimarket.datos.modelo.EstadoSolicitud;
import com.atenea.unimarket.datos.modelo.SolicitudCompra;

public interface SolicitudCompraRepository extends JpaRepository<SolicitudCompra, Integer> {

    boolean existsByServicioIdAndEstadoIn(Integer servicioId, Collection<EstadoSolicitud> estados);

    boolean existsByClienteIdAndServicioIdAndEstado(Integer clienteId, Integer servicioId, EstadoSolicitud estado);

     List<SolicitudCompra> findByEstado(EstadoSolicitud estado);
     @Lock(LockModeType.PESSIMISTIC_WRITE)
     List<SolicitudCompra> findByServicioIdAndEstado(Integer servicioId, EstadoSolicitud estado);2
}
