package com.cloud_technological.aura_pos.repositories.super_admin;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.SuscripcionPagoEntity;

public interface SuscripcionPagoJPARepository extends JpaRepository<SuscripcionPagoEntity, Long> {

    List<SuscripcionPagoEntity> findByDeletedAtIsNull();

    List<SuscripcionPagoEntity> findByEmpresaIdAndDeletedAtIsNullOrderByFechaPagoDesc(Integer empresaId);
}
