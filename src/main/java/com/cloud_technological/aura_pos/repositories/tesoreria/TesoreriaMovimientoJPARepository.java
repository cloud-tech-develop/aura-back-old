package com.cloud_technological.aura_pos.repositories.tesoreria;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cloud_technological.aura_pos.entity.TesoreriaMovimientoEntity;

public interface TesoreriaMovimientoJPARepository extends JpaRepository<TesoreriaMovimientoEntity, Long> {

    @Query("SELECT m FROM TesoreriaMovimientoEntity m WHERE m.empresaId = :empresaId " +
           "AND m.tipo = :tipo AND m.anulado = false " +
           "AND (:cuentaId IS NULL OR m.cuentaBancariaId = :cuentaId) " +
           "AND m.fecha BETWEEN :desde AND :hasta " +
           "ORDER BY m.fecha DESC, m.id DESC")
    List<TesoreriaMovimientoEntity> findByFiltros(
            @Param("empresaId") Integer empresaId,
            @Param("tipo") String tipo,
            @Param("cuentaId") Long cuentaId,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    Optional<TesoreriaMovimientoEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    @Query("SELECT m FROM TesoreriaMovimientoEntity m WHERE m.empresaId = :empresaId " +
           "AND m.cuentaBancariaId = :cuentaId AND m.anulado = false " +
           "AND m.fecha BETWEEN :desde AND :hasta " +
           "ORDER BY m.fecha ASC, m.id ASC")
    List<TesoreriaMovimientoEntity> findParaConciliacion(
            @Param("empresaId") Integer empresaId,
            @Param("cuentaId") Long cuentaId,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);
}
