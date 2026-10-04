package com.cloud_technological.aura_pos.repositories.factura_venta;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.CondicionPagoEntity;

public interface CondicionPagoJPARepository extends JpaRepository<CondicionPagoEntity, Long> {
}
