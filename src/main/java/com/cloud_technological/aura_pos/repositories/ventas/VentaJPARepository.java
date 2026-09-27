package com.cloud_technological.aura_pos.repositories.ventas;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cloud_technological.aura_pos.entity.VentaEntity;

public interface VentaJPARepository extends JpaRepository<VentaEntity, Long> {
    Optional<VentaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    /** Venta local por el número de factura de Factus (para prefill de notas). */
    Optional<VentaEntity> findByEmpresaIdAndFactusNumero(Integer empresaId, String factusNumero);
    
    List<VentaEntity> findByEmpresaId(Integer empresaId);

    /**
     * Ventas con factura electrónica (tienen CUFE) en el rango. Es donde el POS
     * guarda lo que devuelve Factus; la tabla {@code factura} es de un flujo viejo.
     */
    @Query("""
           SELECT v FROM VentaEntity v
           LEFT JOIN FETCH v.cliente
           WHERE v.empresa.id = :empresaId
             AND v.cufe IS NOT NULL AND v.cufe <> ''
             AND v.fechaEmision >= :desde
             AND v.fechaEmision <  :hasta
           ORDER BY v.fechaEmision ASC, v.id ASC
           """)
    List<VentaEntity> findFacturadasElectronicamente(@Param("empresaId") Integer empresaId,
                                                     @Param("desde") LocalDateTime desde,
                                                     @Param("hasta") LocalDateTime hasta);
    
    List<VentaEntity> findByEmpresaIdAndFechaEmisionBetween(Integer empresaId, LocalDateTime desde, LocalDateTime hasta);

    List<VentaEntity> findByClienteIdAndEmpresaIdOrderByFechaEmisionAsc(Long clienteId, Integer empresaId);

    List<VentaEntity> findByClienteIdAndEmpresaIdAndFechaEmisionBetweenOrderByFechaEmisionAsc(
            Long clienteId, Integer empresaId, LocalDateTime desde, LocalDateTime hasta);
}