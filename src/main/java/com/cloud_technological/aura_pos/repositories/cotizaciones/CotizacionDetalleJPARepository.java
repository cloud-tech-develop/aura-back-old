package com.cloud_technological.aura_pos.repositories.cotizaciones;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.CotizacionDetalleEntity;

public interface CotizacionDetalleJPARepository extends JpaRepository<CotizacionDetalleEntity, Long> {
    List<CotizacionDetalleEntity> findByCotizacionId(Long cotizacionId);
}
