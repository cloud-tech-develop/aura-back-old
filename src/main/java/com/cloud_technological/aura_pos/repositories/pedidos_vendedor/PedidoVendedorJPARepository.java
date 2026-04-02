package com.cloud_technological.aura_pos.repositories.pedidos_vendedor;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.PedidoVendedorEntity;

public interface PedidoVendedorJPARepository extends JpaRepository<PedidoVendedorEntity, Long> {

    Optional<PedidoVendedorEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
}
