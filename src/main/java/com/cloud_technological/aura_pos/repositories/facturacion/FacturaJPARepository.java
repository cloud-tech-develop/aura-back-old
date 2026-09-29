package com.cloud_technological.aura_pos.repositories.facturacion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cloud_technological.aura_pos.entity.FacturaEntity;

public interface FacturaJPARepository extends JpaRepository<FacturaEntity, Long> {
    Optional<FacturaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    /**
     * Facturas electrónicas emitidas en un rango, para el reporte exportable a
     * Excel. Trae venta y cliente en el mismo query para no caer en N+1 al
     * recorrer miles de filas.
     */
    @Query("""
           SELECT f FROM FacturaEntity f
           LEFT JOIN FETCH f.venta v
           LEFT JOIN FETCH v.cliente
           WHERE f.empresa.id = :empresaId
             AND f.deletedAt IS NULL
             AND f.fechaHoraEmision >= :desde
             AND f.fechaHoraEmision <  :hasta
           ORDER BY f.fechaHoraEmision ASC, f.consecutivo ASC
           """)
    List<FacturaEntity> findParaReporte(@Param("empresaId") Integer empresaId,
                                        @Param("desde") LocalDateTime desde,
                                        @Param("hasta") LocalDateTime hasta);
    
    Optional<FacturaEntity> findByVentaId(Long ventaId);
    
    Optional<FacturaEntity> findByCufe(String cufe);
    
    Optional<FacturaEntity> findByPrefijoAndConsecutivoAndEmpresaId(String prefijo, Long consecutivo, Integer empresaId);
}
