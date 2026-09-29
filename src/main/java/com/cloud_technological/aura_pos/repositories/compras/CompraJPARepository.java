package com.cloud_technological.aura_pos.repositories.compras;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.CompraEntity;

public interface CompraJPARepository extends JpaRepository<CompraEntity, Long> {
    Optional<CompraEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    /**
     * Las notas crédito emitidas sobre una factura de compra. Sirven para saber
     * cuánto de cada producto ya se acreditó antes de aceptar una nueva.
     */
    List<CompraEntity> findByCompraOrigenIdAndEmpresaId(Long compraOrigenId, Integer empresaId);
}
