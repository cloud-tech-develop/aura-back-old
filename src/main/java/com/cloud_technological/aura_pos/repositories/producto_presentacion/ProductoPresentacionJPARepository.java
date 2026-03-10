package com.cloud_technological.aura_pos.repositories.producto_presentacion;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ProductoPresentacionEntity;

public interface ProductoPresentacionJPARepository extends JpaRepository<ProductoPresentacionEntity, Long> {
    Optional<ProductoPresentacionEntity> findByIdAndProductoEmpresaId(Long id, Integer empresaId);
    List<ProductoPresentacionEntity> findByProductoIdAndActivoTrue(Long productoId);
    // Buscar defaults para desmarcarlos
    List<ProductoPresentacionEntity> findByProductoIdAndEsDefaultCompraTrue(Long productoId);
    List<ProductoPresentacionEntity> findByProductoIdAndEsDefaultVentaTrue(Long productoId);

    // Validar factor duplicado al crear
    boolean existsByProductoIdAndFactorConversion(Long productoId, BigDecimal factorConversion);

    // Validar factor duplicado al editar (excluyendo la actual)
    boolean existsByProductoIdAndFactorConversionAndIdNot(Long productoId, BigDecimal factorConversion, Long id);

    // Contar presentaciones activas (para no eliminar la única)
    long countByProductoIdAndActivoTrue(Long productoId);
}