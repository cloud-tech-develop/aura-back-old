package com.cloud_technological.aura_pos.repositories.ventas;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.VentaEntity;

public interface VentaJPARepository extends JpaRepository<VentaEntity, Long> {
    Optional<VentaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
    
    List<VentaEntity> findByEmpresaId(Integer empresaId);
    
    List<VentaEntity> findByEmpresaIdAndFechaEmisionBetween(Integer empresaId, LocalDateTime desde, LocalDateTime hasta);

    List<VentaEntity> findByClienteIdAndEmpresaIdOrderByFechaEmisionAsc(Long clienteId, Integer empresaId);

    List<VentaEntity> findByClienteIdAndEmpresaIdAndFechaEmisionBetweenOrderByFechaEmisionAsc(
            Long clienteId, Integer empresaId, LocalDateTime desde, LocalDateTime hasta);
}