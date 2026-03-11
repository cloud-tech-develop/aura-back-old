package com.cloud_technological.aura_pos.repositories.precios_listas_productos;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ProductoPrecioEntity;

public interface ProductoPrecioJPARepository extends JpaRepository<ProductoPrecioEntity, Long> {
    Optional<ProductoPrecioEntity> findByIdAndListaPrecioEmpresaId(Long id, Integer empresaId);
    boolean existsByListaPrecioIdAndProductoPresentacionId(Long listaPrecioId, Long productoPresentacionId);
    boolean existsByListaPrecioIdAndProductoId(Long listaPrecioId, Long productoId);
}