package com.cloud_technological.aura_pos.repositories.platform;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.EmpresaSubmoduloEntity;

public interface EmpresaSubmoduloJPARepository extends JpaRepository<EmpresaSubmoduloEntity, Integer> {
    Optional<EmpresaSubmoduloEntity> findByEmpresaIdAndSubmoduloId(Integer empresaId, Integer submoduloId);
    boolean existsByEmpresaIdAndSubmoduloIdAndIdNot(Integer empresaId, Integer submoduloId, Integer id);
    void deleteByEmpresaIdAndSubmoduloId(Integer empresaId, Integer submoduloId);
}
