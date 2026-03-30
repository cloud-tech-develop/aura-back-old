package com.cloud_technological.aura_pos.repositories.cartera;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.HistorialCreditoEntity;

public interface HistorialCreditoJPARepository extends JpaRepository<HistorialCreditoEntity, Long> {
    List<HistorialCreditoEntity> findByTerceroIdAndEmpresaIdOrderByCreatedAtDesc(Long terceroId, Integer empresaId);
    long countByTerceroIdAndEmpresaIdAndTipoEvento(Long terceroId, Integer empresaId, String tipoEvento);
}
