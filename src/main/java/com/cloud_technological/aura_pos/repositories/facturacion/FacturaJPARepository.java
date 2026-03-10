package com.cloud_technological.aura_pos.repositories.facturacion;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.FacturaEntity;

public interface FacturaJPARepository extends JpaRepository<FacturaEntity, Long> {
    Optional<FacturaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
    
    Optional<FacturaEntity> findByVentaId(Long ventaId);
    
    Optional<FacturaEntity> findByCufe(String cufe);
    
    Optional<FacturaEntity> findByPrefijoAndConsecutivoAndEmpresaId(String prefijo, Long consecutivo, Integer empresaId);
}
