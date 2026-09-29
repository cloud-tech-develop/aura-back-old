package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.contabilidad.CreatePlanCuentaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.PlanCuentaDto;

public interface PlanCuentasService {
    List<PlanCuentaDto> listar(Integer empresaId);

    /**
     * Cuentas habilitadas como origen de un pago: caja, caja menor, bancos y
     * las cuentas puente de fondos entregados sin legalizar.
     *
     * <p>Alimenta el combo "¿de dónde sale la plata?" de compras y gastos. Sin
     * esta lista el front tendría que mostrar el plan de cuentas completo, y el
     * usuario podía terminar pagando un gasto contra una cuenta de ingresos.
     */
    List<PlanCuentaDto> listarMediosPago(Integer empresaId);
    PlanCuentaDto crear(Integer empresaId, CreatePlanCuentaDto dto);
    PlanCuentaDto actualizar(Long id, Integer empresaId, CreatePlanCuentaDto dto);
    void eliminar(Long id, Integer empresaId);
    void seedPUC(Integer empresaId);
}
