package com.cloud_technological.aura_pos.repositories.cartera;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.GestionCobroEntity;

public interface GestionCobroJPARepository extends JpaRepository<GestionCobroEntity, Long> {
    List<GestionCobroEntity> findByTerceroIdAndEmpresaIdOrderByCreatedAtDesc(Long terceroId, Integer empresaId);
    List<GestionCobroEntity> findByCuentaCobrarIdOrderByCreatedAtDesc(Long cuentaCobrarId);
}
