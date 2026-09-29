package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ConceptoContable;
import com.cloud_technological.aura_pos.entity.CuentaConfigEntity;

public interface CuentaConfigJPARepository extends JpaRepository<CuentaConfigEntity, Long> {

    List<CuentaConfigEntity> findByEmpresaId(Integer empresaId);

    Optional<CuentaConfigEntity> findByEmpresaIdAndConcepto(Integer empresaId, ConceptoContable concepto);
}
