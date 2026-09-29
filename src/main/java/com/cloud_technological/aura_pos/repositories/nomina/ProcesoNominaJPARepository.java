package com.cloud_technological.aura_pos.repositories.nomina;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.ProcesoNominaEntity;

@Repository
public interface ProcesoNominaJPARepository extends JpaRepository<ProcesoNominaEntity, Long> {

    Optional<ProcesoNominaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    List<ProcesoNominaEntity> findByEmpresaIdAndTipoOrderByIdDesc(Integer empresaId, String tipo);

    /** ¿Ya hay uno corriendo? Evita lanzar dos liquidaciones del mismo período. */
    @Query("""
            SELECT p FROM ProcesoNominaEntity p
             WHERE p.empresaId = :empresaId
               AND p.tipo = :tipo
               AND p.referenciaId = :referenciaId
               AND p.estado IN ('PENDIENTE', 'EN_PROCESO')
            """)
    List<ProcesoNominaEntity> findActivos(@Param("empresaId") Integer empresaId,
                                          @Param("tipo") String tipo,
                                          @Param("referenciaId") Long referenciaId);

    /**
     * Marca como FALLIDO lo que quedó colgado.
     *
     * <p>Si el proceso se reinicia mientras un job corría, su fila queda en
     * EN_PROCESO para siempre y bloquea relanzarlo. Esto se ejecuta al arrancar.
     */
    @Modifying
    @Query("""
            UPDATE ProcesoNominaEntity p
               SET p.estado = 'FALLIDO',
                   p.mensaje = 'Interrumpido por reinicio del servicio',
                   p.finalizadoAt = CURRENT_TIMESTAMP
             WHERE p.estado IN ('PENDIENTE', 'EN_PROCESO')
            """)
    int marcarColgadosComoFallidos();
}
