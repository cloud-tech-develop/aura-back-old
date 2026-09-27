package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.NotaDiarioSoporteEntity;

public interface NotaDiarioSoporteJPARepository extends JpaRepository<NotaDiarioSoporteEntity, Long> {

    List<NotaDiarioSoporteEntity> findByAsientoIdAndEmpresaIdAndDeletedAtIsNullOrderByIdAsc(
            Long asientoId, Integer empresaId);

    Optional<NotaDiarioSoporteEntity> findByIdAndAsientoIdAndEmpresaIdAndDeletedAtIsNull(
            Long id, Long asientoId, Integer empresaId);

    long countByAsientoIdAndDeletedAtIsNull(Long asientoId);
}
