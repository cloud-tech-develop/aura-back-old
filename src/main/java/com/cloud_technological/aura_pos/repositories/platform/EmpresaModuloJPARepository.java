package com.cloud_technological.aura_pos.repositories.platform;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.EmpresaModuloEntity;

public interface EmpresaModuloJPARepository extends JpaRepository<EmpresaModuloEntity, Integer> {
    Optional<EmpresaModuloEntity> findByEmpresaIdAndModuloId(Integer empresaId, Integer moduloId);
    boolean existsByEmpresaIdAndModuloIdAndIdNot(Integer empresaId, Integer moduloId, Integer id);
    void deleteByEmpresaIdAndModuloId(Integer empresaId, Integer moduloId);
}
