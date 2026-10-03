package com.cloud_technological.aura_pos.services;

import com.cloud_technological.aura_pos.dto.contabilidad.DashboardContableDto;

public interface DashboardContableService {

    /**
     * Resumen del Centro de Contabilidad para el mes indicado: KPIs del mes y
     * del anterior, serie enero..mes, distribución de gastos y pendientes.
     */
    DashboardContableDto resumen(Integer empresaId, int anio, int mes);
}
