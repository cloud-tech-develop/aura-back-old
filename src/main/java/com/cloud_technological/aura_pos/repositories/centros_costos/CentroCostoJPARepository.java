package com.cloud_technological.aura_pos.repositories.centros_costos;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.CentroCostoEntity;

public interface CentroCostoJPARepository extends JpaRepository<CentroCostoEntity, Long> {

    Optional<CentroCostoEntity> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Integer empresaId);

    boolean existsByCodigoAndEmpresaIdAndDeletedAtIsNull(String codigo, Integer empresaId);

    boolean existsByCodigoAndEmpresaIdAndIdNotAndDeletedAtIsNull(String codigo, Integer empresaId, Long id);

    boolean existsByNombreAndEmpresaIdAndDeletedAtIsNull(String nombre, Integer empresaId);

    boolean existsByNombreAndEmpresaIdAndIdNotAndDeletedAtIsNull(String nombre, Integer empresaId, Long id);

    boolean existsByPadreIdAndDeletedAtIsNull(Long padreId);
}
