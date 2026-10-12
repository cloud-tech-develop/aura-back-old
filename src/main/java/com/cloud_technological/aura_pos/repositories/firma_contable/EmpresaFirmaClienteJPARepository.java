package com.cloud_technological.aura_pos.repositories.firma_contable;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.EmpresaFirmaClienteEntity;

/** Regla del proyecto: por JPA solo findById/save/delete; las consultas van en FirmaContableQueryRepository. */
public interface EmpresaFirmaClienteJPARepository extends JpaRepository<EmpresaFirmaClienteEntity, Long> {
}
