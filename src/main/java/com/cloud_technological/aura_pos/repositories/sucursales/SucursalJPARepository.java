package com.cloud_technological.aura_pos.repositories.sucursales;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.SucursalEntity;

public interface SucursalJPARepository extends JpaRepository<SucursalEntity, Integer> {
    Optional<SucursalEntity> findByIdAndEmpresaId(Integer id, Integer empresaId);
}