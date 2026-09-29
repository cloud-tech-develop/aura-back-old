package com.cloud_technological.aura_pos.repositories.productos_composicion;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cloud_technological.aura_pos.entity.ProductoComposicionEntity;

public interface ProductoComposicionJPARepository extends JpaRepository<ProductoComposicionEntity, Long> {
    Optional<ProductoComposicionEntity> findByIdAndProductoPadreEmpresaId(Long id, Integer empresaId);
    boolean existsByProductoPadreIdAndProductoHijoId(Long padreId, Long hijoId);
    List<ProductoComposicionEntity> findByProductoPadreId(Long productoPadreId);

    List<ProductoComposicionEntity> findByProductoPadreIdOrderByOrdenAscIdAsc(Long productoPadreId);

    boolean existsByProductoPadreId(Long productoPadreId);

    /**
     * Solo los ids de los componentes. Lo usan la detección de ciclos y el
     * costeo recursivo, que recorren el árbol y no necesitan entidades enteras.
     */
    @Query("SELECT c.productoHijo.id FROM ProductoComposicionEntity c WHERE c.productoPadre.id = :padreId")
    List<Long> findHijoIdsByPadreId(@Param("padreId") Long padreId);
}
