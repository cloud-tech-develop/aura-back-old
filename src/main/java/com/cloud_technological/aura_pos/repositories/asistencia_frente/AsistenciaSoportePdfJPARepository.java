package com.cloud_technological.aura_pos.repositories.asistencia_frente;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.AsistenciaSoportePdfEntity;

public interface AsistenciaSoportePdfJPARepository extends JpaRepository<AsistenciaSoportePdfEntity, Long> {

    Optional<AsistenciaSoportePdfEntity> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Integer empresaId);

    boolean existsByEmpresaIdAndHashArchivoAndDeletedAtIsNull(Integer empresaId, String hashArchivo);
}
