package com.cloud_technological.aura_pos.repositories.obligaciones;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ObligacionFinancieraEntity;

public interface ObligacionFinancieraJPARepository extends JpaRepository<ObligacionFinancieraEntity, Long> {
    Optional<ObligacionFinancieraEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
    List<ObligacionFinancieraEntity> findByEmpresaIdOrderByFechaDesembolsoDesc(Integer empresaId);
}
