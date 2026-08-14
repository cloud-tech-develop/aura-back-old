package com.cloud_technological.aura_pos.repositories.nomina;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.ContratoSalarioHistorialEntity;

@Repository
public interface ContratoSalarioHistorialJPARepository
        extends JpaRepository<ContratoSalarioHistorialEntity, Long> {

    /** La fila vigente (fecha_hasta IS NULL). Solo puede haber una. */
    @Query("""
            SELECT h FROM ContratoSalarioHistorialEntity h
             WHERE h.contrato.id = :contratoId
               AND h.fechaHasta IS NULL
            """)
    Optional<ContratoSalarioHistorialEntity> findVigente(@Param("contratoId") Long contratoId);

    /** Salario que regía en una fecha. Para retroactivos y auditoría. */
    @Query("""
            SELECT h FROM ContratoSalarioHistorialEntity h
             WHERE h.contrato.id = :contratoId
               AND h.fechaDesde <= :fecha
               AND (h.fechaHasta IS NULL OR h.fechaHasta >= :fecha)
            """)
    Optional<ContratoSalarioHistorialEntity> findEnFecha(@Param("contratoId") Long contratoId,
                                                         @Param("fecha") LocalDate fecha);

    /**
     * Cambios salariales dentro de un rango.
     *
     * <p>Es lo que alimenta la bandera {@code vsp} (variación permanente de
     * salario) de PILA: si hay un cambio dentro del período, la bandera va en
     * true y {@code fecha_inicio_vsp} es el {@code fechaDesde} del cambio.
     */
    @Query("""
            SELECT h FROM ContratoSalarioHistorialEntity h
             WHERE h.contrato.id = :contratoId
               AND h.fechaDesde BETWEEN :desde AND :hasta
             ORDER BY h.fechaDesde
            """)
    List<ContratoSalarioHistorialEntity> findCambiosEnPeriodo(@Param("contratoId") Long contratoId,
                                                              @Param("desde") LocalDate desde,
                                                              @Param("hasta") LocalDate hasta);

    List<ContratoSalarioHistorialEntity> findByContratoIdOrderByFechaDesdeDesc(Long contratoId);
}
