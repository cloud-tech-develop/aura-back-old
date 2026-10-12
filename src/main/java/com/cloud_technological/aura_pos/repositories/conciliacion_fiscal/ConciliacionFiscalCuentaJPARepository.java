package com.cloud_technological.aura_pos.repositories.conciliacion_fiscal;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ConciliacionFiscalCuentaEntity;

/** Solo findById/save/delete: las consultas van en ConciliacionFiscalQueryRepository. */
public interface ConciliacionFiscalCuentaJPARepository extends JpaRepository<ConciliacionFiscalCuentaEntity, Long> {
}
