package com.atenea.unimarket.datos.repositorio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.atenea.unimarket.datos.modelo.Notificacion;

public interface NotificacionRepository extends JpaRepository<Notificacion, Integer> {

    List<Notificacion> findByUsuarioIdOrderByFechaCreacionDescIdDesc(Integer usuarioId);
    List<Notificacion> findTop3ByUsuarioIdAndDescartadaFalseOrderByFechaCreacionDescIdDesc(Integer usuarioId);

    long countByUsuarioIdAndLeidaFalseAndDescartadaFalse(Integer usuarioId);

    @Modifying
    @Query("update Notificacion n set n.leida = true where n.id = :id and n.usuario.id = :usuarioId")
    int marcarLeida(Integer id, Integer usuarioId);

    @Modifying
    @Query("update Notificacion n set n.descartada = :descartada where n.id = :id and n.usuario.id = :usuarioId")
    int descartar(Integer id, Integer usuarioId, boolean descartada);
}
