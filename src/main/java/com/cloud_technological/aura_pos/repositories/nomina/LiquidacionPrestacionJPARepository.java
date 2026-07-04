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
}
