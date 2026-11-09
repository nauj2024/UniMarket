package com.atenea.unimarket.datos.repositorio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.atenea.unimarket.datos.modelo.EstadoServicio;
import com.atenea.unimarket.datos.modelo.Servicio;

public interface ServicioRepository extends JpaRepository<Servicio, Integer> , JpaSpecificationExecutor<Servicio>{

    @Override
    @EntityGraph(attributePaths = {"categoria", "ofertador"})
    Page<Servicio> findAll(Specification<Servicio> spec, Pageable pageable);

    List<Servicio> findByCategoriaIdAndEstadoOrderByFechaPublicacionAsc(Integer categoriaId, EstadoServicio estado);

    List<Servicio> findByOfertadorIdAndEstadoOrderByFechaPublicacionAsc(Integer ofertadorId, EstadoServicio estado);

}
