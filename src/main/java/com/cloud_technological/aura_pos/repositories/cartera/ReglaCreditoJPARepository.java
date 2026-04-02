package com.cloud_technological.aura_pos.repositories.cartera;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ReglaCreditoEntity;

public interface ReglaCreditoJPARepository extends JpaRepository<ReglaCreditoEntity, Long> {
    List<ReglaCreditoEntity> findByEmpresaIdAndActivoTrueOrderByOrdenAsc(Integer empresaId);
    List<ReglaCreditoEntity> findByEmpresaIdAndActivoTrueAndEventoOrderByOrdenAsc(Integer empresaId, String evento);
}
