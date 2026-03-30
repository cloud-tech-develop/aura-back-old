package com.cloud_technological.aura_pos.repositories.ordenes_compra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cloud_technological.aura_pos.entity.OrdenCompraEntity;

public interface OrdenCompraJPARepository extends JpaRepository<OrdenCompraEntity, Long> {

    Optional<OrdenCompraEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    List<OrdenCompraEntity> findByEmpresaIdOrderByCreatedAtDesc(Integer empresaId);

    /** Count OCs in the given year to generate the correlative number */
    @Query("SELECT COUNT(o) FROM OrdenCompraEntity o WHERE o.empresaId = :empresaId AND YEAR(o.createdAt) = :year")
    long countByEmpresaIdAndYear(@Param("empresaId") Integer empresaId, @Param("year") int year);
}
