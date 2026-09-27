package com.cloud_technological.aura_pos.repositories.documento_soporte;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.DocumentoSoporteEntity;

public interface DocumentoSoporteJPARepository extends JpaRepository<DocumentoSoporteEntity, Long> {

    Optional<DocumentoSoporteEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    boolean existsByEmpresaIdAndOrigenTipoAndOrigenIdAndEstado(
            Integer empresaId, String origenTipo, Long origenId, String estado);

    List<DocumentoSoporteEntity> findByEmpresaIdAndOrigenTipoAndOrigenIdOrderByIdDesc(
            Integer empresaId, String origenTipo, Long origenId);

    List<DocumentoSoporteEntity> findByEmpresaIdAndCreatedAtBetweenOrderByIdDesc(
            Integer empresaId, LocalDateTime desde, LocalDateTime hasta);
}
