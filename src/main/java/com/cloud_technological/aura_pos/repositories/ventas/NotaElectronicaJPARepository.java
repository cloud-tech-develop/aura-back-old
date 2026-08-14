package com.cloud_technological.aura_pos.repositories.ventas;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.NotaElectronicaEntity;

public interface NotaElectronicaJPARepository extends JpaRepository<NotaElectronicaEntity, Long> {

    Optional<NotaElectronicaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    Optional<NotaElectronicaEntity> findByEmpresaIdAndReferenceCode(Integer empresaId, String referenceCode);

    boolean existsByEmpresaIdAndReferenceCode(Integer empresaId, String referenceCode);

    /** Todas las notas de la empresa, más recientes primero. */
    List<NotaElectronicaEntity> findByEmpresaIdOrderByIdDesc(Integer empresaId);

    /** Notas crédito y débito emitidas en un rango (reporte exportable a Excel). */
    List<NotaElectronicaEntity> findByEmpresaIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
            Integer empresaId, LocalDateTime desde, LocalDateTime hasta);

    /** Igual que el anterior, acotado a un tipo ({@code CREDITO} o {@code DEBITO}). */
    List<NotaElectronicaEntity> findByEmpresaIdAndTipoAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
            Integer empresaId, String tipo, LocalDateTime desde, LocalDateTime hasta);
}
