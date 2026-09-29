package com.cloud_technological.aura_pos.repositories.asistencia;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.AutorizacionLiquidacionEntity;

public interface AutorizacionLiquidacionJPARepository extends JpaRepository<AutorizacionLiquidacionEntity, Long> {

    boolean existsByEmpresaIdAndEmpleadoIdAndPeriodoNominaIdAndEstado(
            Integer empresaId, Long empleadoId, Long periodoNominaId, String estado);

    List<AutorizacionLiquidacionEntity> findByEmpresaIdAndPeriodoNominaId(
            Integer empresaId, Long periodoNominaId);
}
