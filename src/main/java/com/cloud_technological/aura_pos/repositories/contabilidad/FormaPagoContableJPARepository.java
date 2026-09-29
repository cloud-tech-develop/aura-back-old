package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.FormaPagoContableEntity;

public interface FormaPagoContableJPARepository
        extends JpaRepository<FormaPagoContableEntity, Long> {

    List<FormaPagoContableEntity> findByEmpresaIdOrderByNombreAsc(Integer empresaId);

    Optional<FormaPagoContableEntity> findByEmpresaIdAndCodigo(Integer empresaId, String codigo);

    Optional<FormaPagoContableEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
}
