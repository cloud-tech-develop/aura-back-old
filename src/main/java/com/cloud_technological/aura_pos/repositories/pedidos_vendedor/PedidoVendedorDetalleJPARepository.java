package com.cloud_technological.aura_pos.repositories.pedidos_vendedor;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.PedidoVendedorDetalleEntity;

public interface PedidoVendedorDetalleJPARepository extends JpaRepository<PedidoVendedorDetalleEntity, Long> {
}
