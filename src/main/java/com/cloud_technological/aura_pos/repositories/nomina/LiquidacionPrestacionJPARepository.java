package com.cloud_technological.aura_pos.repositories.nomina;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cloud_technological.aura_pos.entity.LiquidacionPrestacionEntity;

public interface LiquidacionPrestacionJPARepository extends JpaRepository<LiquidacionPrestacionEntity, Long> {

    Optional<LiquidacionPrestacionEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    List<LiquidacionPrestacionEntity> findByEmpresaIdOrderByCreatedAtDesc(Integer empresaId);

    /** Filas de un lote (una liquidación agrupada). */
    List<LiquidacionPrestacionEntity> findByEmpresaIdAndLoteOrderByIdAsc(Integer empresaId, String lote);

    /** Prestaciones ya pagadas de un tipo para un empleado, excluyendo la actual. */
    @Query("""
        SELECT COALESCE(SUM(l.valor), 0) FROM LiquidacionPrestacionEntity l
        WHERE l.empresaId = :empresaId AND l.empleado.id = :empleadoId
          AND l.tipo = :tipo AND l.estado = 'PAGADA' AND l.id <> :selfId
        """)
    BigDecimal sumPagadoByEmpleadoTipo(@Param("empresaId") Integer empresaId,
                                       @Param("empleadoId") Long empleadoId,
                                       @Param("tipo") String tipo,
                                       @Param("selfId") Long selfId);

    /** Última prestación PAGADA de un tipo para un empleado (por fecha hasta). */
    Optional<LiquidacionPrestacionEntity> findTopByEmpresaIdAndEmpleadoIdAndTipoAndEstadoOrderByFechaHastaDesc(
            Integer empresaId, Long empleadoId, String tipo, String estado);

    /**
     * B-04 — última prestación NO anulada de un tipo para un empleado (por fecha
     * hasta). Ancla el "desde" de la liquidación definitiva para NO re-liquidar un
     * período ya cubierto por una consignación/lote existente, aunque todavía no
     * esté marcado como PAGADA. Evita el doble pago de cesantías.
     */
    Optional<LiquidacionPrestacionEntity> findTopByEmpresaIdAndEmpleadoIdAndTipoAndEstadoNotOrderByFechaHastaDesc(
            Integer empresaId, Long empleadoId, String tipo, String estado);

    /** F3 — días de vacaciones tomados por prestación (no anuladas) de un empleado. */
    @Query("""
        SELECT COALESCE(SUM(l.dias), 0) FROM LiquidacionPrestacionEntity l
        WHERE l.empresaId = :empresaId AND l.empleado.id = :empleadoId
          AND l.tipo = 'VACACIONES' AND l.estado <> 'ANULADA'
        """)
    Long sumDiasVacacionesPrestacion(@Param("empresaId") Integer empresaId,
                                     @Param("empleadoId") Long empleadoId);
}
