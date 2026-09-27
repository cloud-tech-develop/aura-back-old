package com.cloud_technological.aura_pos.repositories.bodegas;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.BodegaEntity;

public interface BodegaJPARepository extends JpaRepository<BodegaEntity, Long> {

    Optional<BodegaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    Optional<BodegaEntity> findBySucursalIdAndEsPrincipalTrue(Integer sucursalId);

    List<BodegaEntity> findBySucursalIdOrderByNombreAsc(Integer sucursalId);

    List<BodegaEntity> findByEmpresaIdOrderByNombreAsc(Integer empresaId);

    boolean existsBySucursalIdAndNombreIgnoreCase(Integer sucursalId, String nombre);
}
