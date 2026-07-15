package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ImpuestoEntity;

public interface ImpuestoJPARepository extends JpaRepository<ImpuestoEntity, Long> {

    List<ImpuestoEntity> findByEmpresaIdOrderByNombreAsc(Integer empresaId);

    Optional<ImpuestoEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    Optional<ImpuestoEntity> findByEmpresaIdAndNombre(Integer empresaId, String nombre);
}
