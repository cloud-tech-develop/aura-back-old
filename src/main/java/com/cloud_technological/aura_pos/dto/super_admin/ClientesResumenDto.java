package com.cloud_technological.aura_pos.dto.super_admin;

import java.math.BigDecimal;

import lombok.Data;

/** KPIs de clientes/membresías para el dashboard del platform. */
@Data
public class ClientesResumenDto {

    private long totalClientes;      // empresas con suscripción
    private long activos;
    private long vencidos;           // mensuales activos cuya fecha_proximo_pago ya pasó
    private long enPrueba;
    private long suspendidos;
    private long cancelados;
    private long mensuales;
    private long unicos;
    private long sinMembresia;       // empresas sin suscripción registrada

    private BigDecimal mrr;          // ingreso mensual recurrente (mensuales activos)
    private BigDecimal recaudadoMesActual;
}
