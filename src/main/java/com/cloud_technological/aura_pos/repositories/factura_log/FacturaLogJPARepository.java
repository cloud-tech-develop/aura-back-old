package com.cloud_technological.aura_pos.repositories.factura_log;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.FacturaLogEntity;

@Repository
public interface FacturaLogJPARepository extends JpaRepository<FacturaLogEntity, Long> {
    
    List<FacturaLogEntity> findByFacturaIdOrderByCreatedAtDesc(Long facturaId);

    /**
     * Busca los logs más recientes por factura que estén en PENDIENTE y tengan metadata de reintento.
     * Se usa para detectar operaciones que necesitan ser reintentadas.
     */
    @Query(value = """
        SELECT DISTINCT ON (factura_id) *
        FROM factura_log
        WHERE estado_nuevo = 'PENDIENTE'
          AND metadata IS NOT NULL
          AND metadata::jsonb ? 'action'
        ORDER BY factura_id, created_at DESC
        """, nativeQuery = true)
    List<FacturaLogEntity> findPendingWithRetryPayload();
}
