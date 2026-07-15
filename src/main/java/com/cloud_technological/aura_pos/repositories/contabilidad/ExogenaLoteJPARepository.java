package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ExogenaLoteEntity;

public interface ExogenaLoteJPARepository extends JpaRepository<ExogenaLoteEntity, Long> {

    List<ExogenaLoteEntity> findByEmpresaIdAndAnioOrderByFormatoIdAscVersionDesc(
            Integer empresaId, Integer anio);

    List<ExogenaLoteEntity> findByEmpresaIdOrderByAnioDescVersionDesc(Integer empresaId);

    Optional<ExogenaLoteEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    Optional<ExogenaLoteEntity> findFirstByEmpresaIdAndFormatoIdAndAnioOrderByVersionDesc(
            Integer empresaId, Long formatoId, Integer anio);
}
