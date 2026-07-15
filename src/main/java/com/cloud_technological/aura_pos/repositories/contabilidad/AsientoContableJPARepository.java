package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.AsientoContableEntity;

public interface AsientoContableJPARepository extends JpaRepository<AsientoContableEntity, Long> {

    Optional<AsientoContableEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    boolean existsByTipoOrigenAndOrigenIdAndEmpresaId(String tipoOrigen, Long origenId, Integer empresaId);

    Optional<AsientoContableEntity> findByTipoOrigenAndOrigenIdAndEmpresaId(String tipoOrigen, Long origenId, Integer empresaId);

    Optional<AsientoContableEntity> findFirstByEmpresaIdAndTipoOrigen(Integer empresaId, String tipoOrigen);

    long countByEmpresaIdAndTipoOrigenNot(Integer empresaId, String tipoOrigen);

    // ── E3 · modo revisión ───────────────────────────────────────────────
    java.util.List<AsientoContableEntity> findByEmpresaIdAndEstadoOrderByFechaDescIdDesc(
            Integer empresaId, String estado);

    java.util.List<AsientoContableEntity> findByEmpresaIdAndEstadoAndFechaBetweenOrderByFechaAscIdAsc(
            Integer empresaId, String estado, java.time.LocalDate desde, java.time.LocalDate hasta);

    boolean existsByEmpresaIdAndPeriodoContableIdAndEstado(
            Integer empresaId, Long periodoContableId, String estado);

    /** Red de seguridad: no debería existir ninguno (el validador lo impide). */
    @org.springframework.data.jpa.repository.Query(
            "select a from AsientoContableEntity a where a.empresaId = ?1 and a.totalDebito <> a.totalCredito")
    java.util.List<AsientoContableEntity> findDescuadrados(Integer empresaId);
}
