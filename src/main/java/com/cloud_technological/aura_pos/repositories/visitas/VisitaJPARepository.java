package com.cloud_technological.aura_pos.repositories.visitas;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.VisitaEntity;

@Repository
public interface VisitaJPARepository extends JpaRepository<VisitaEntity, Long> {
    
    List<VisitaEntity> findByVendedorIdAndFechaProgramadaBetweenAndEstado(
            Long vendedorId, LocalDateTime start, LocalDateTime end, VisitaEntity.Estado estado);
    
    @Query("SELECT v FROM VisitaEntity v WHERE v.vendedor.id = :vendedorId AND v.estado = :estado")
    List<VisitaEntity> findByVendedorIdAndEstado(@Param("vendedorId") Long vendedorId, @Param("estado") VisitaEntity.Estado estado);
    
    boolean existsByLocalIdAndFechaProgramadaAndEstadoNot(Long localId, LocalDateTime fechaProgramada, VisitaEntity.Estado estado);
}