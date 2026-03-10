package com.cloud_technological.aura_pos.repositories.cotizaciones;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.CotizacionEntity;

public interface CotizacionJPARepository extends JpaRepository<CotizacionEntity, Long> {
    Optional<CotizacionEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
    
    List<CotizacionEntity> findByEmpresaId(Integer empresaId);
    
    Optional<CotizacionEntity> findByNumeroAndEmpresaId(String numero, Integer empresaId);
    
    List<CotizacionEntity> findByEmpresaIdAndEstado(Integer empresaId, String estado);
    
    List<CotizacionEntity> findByEmpresaIdAndFechaVencimientoBeforeAndEstado(
            Integer empresaId, java.time.LocalDate fecha, String estado);
}
