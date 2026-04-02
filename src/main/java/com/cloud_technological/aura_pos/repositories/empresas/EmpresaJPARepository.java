package com.cloud_technological.aura_pos.repositories.empresas;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.EmpresaEntity;

public interface EmpresaJPARepository extends JpaRepository<EmpresaEntity, Integer> {

    boolean existsByNit(String nit);

    Optional<EmpresaEntity> findByNit(String nit);
}
