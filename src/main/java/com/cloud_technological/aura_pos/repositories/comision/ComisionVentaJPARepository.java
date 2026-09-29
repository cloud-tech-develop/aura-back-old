package com.cloud_technological.aura_pos.repositories.comision;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.ComisionVentaEntity;

@Repository
public interface ComisionVentaJPARepository extends JpaRepository<ComisionVentaEntity, Long> {

    // Pendientes de un técnico (SERVICIO)
    @Query("SELECT cv FROM ComisionVentaEntity cv WHERE cv.tecnico.id = :tecnicoId AND cv.empresa.id = :empresaId AND cv.liquidacion IS NULL AND cv.modalidad = 'SERVICIO'")
    List<ComisionVentaEntity> findPendientesTecnico(
            @Param("tecnicoId") Integer tecnicoId,
            @Param("empresaId") Integer empresaId);

    // Pendientes de un vendedor (VENTA)
    @Query("SELECT cv FROM ComisionVentaEntity cv WHERE cv.vendedor.id = :vendedorId AND cv.empresa.id = :empresaId AND cv.liquidacion IS NULL AND cv.modalidad = 'VENTA'")
    List<ComisionVentaEntity> findPendientesVendedor(
            @Param("vendedorId") Long vendedorId,
            @Param("empresaId") Integer empresaId);

    // Todos los de una liquidación
    List<ComisionVentaEntity> findByLiquidacionId(Long liquidacionId);
}
