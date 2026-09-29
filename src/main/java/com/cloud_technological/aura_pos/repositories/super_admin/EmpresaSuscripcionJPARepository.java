package com.cloud_technological.aura_pos.repositories.super_admin;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.EmpresaSuscripcionEntity;

public interface EmpresaSuscripcionJPARepository extends JpaRepository<EmpresaSuscripcionEntity, Long> {

    List<EmpresaSuscripcionEntity> findByDeletedAtIsNull();

    Optional<EmpresaSuscripcionEntity> findByEmpresaIdAndDeletedAtIsNull(Integer empresaId);
}
