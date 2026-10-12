package com.cloud_technological.aura_pos.repositories.conciliacion_fiscal;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ConciliacionFiscalAjusteEntity;

/** Solo findById/save/delete: las consultas van en ConciliacionFiscalQueryRepository. */
public interface ConciliacionFiscalAjusteJPARepository extends JpaRepository<ConciliacionFiscalAjusteEntity, Long> {
}
