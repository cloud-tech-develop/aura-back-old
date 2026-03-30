package com.cloud_technological.aura_pos.repositories.comision;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.ComisionConfigEntity;

@Repository
public interface ComisionConfigJPARepository extends JpaRepository<ComisionConfigEntity, Long> {

    Optional<ComisionConfigEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    // ── SERVICIO: busca config activa por producto (modalidad = SERVICIO) ──────
    Optional<ComisionConfigEntity> findByProductoIdAndEmpresaIdAndModalidadAndActivoTrue(
            Long productoId, Integer empresaId, String modalidad);

    // ── VENTA: busca config activa por producto ────────────────────────────────
    Optional<ComisionConfigEntity> findFirstByProductoIdAndEmpresaIdAndModalidadAndActivoTrue(
            Long productoId, Integer empresaId, String modalidad);

    // ── VENTA: busca config activa por categoría ───────────────────────────────
    Optional<ComisionConfigEntity> findFirstByCategoriaIdAndEmpresaIdAndModalidadAndActivoTrue(
            Long categoriaId, Integer empresaId, String modalidad);
}
