package com.cloud_technological.aura_pos.dto.cartera;

import java.math.BigDecimal;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CarteraDashboardDto {

    // ── KPIs principales ──────────────────────────────────────────
    private BigDecimal totalCartera;          // suma de saldo_pendiente de cuentas activas
    private BigDecimal carteraVencida;        // suma donde fecha_vencimiento < hoy
    private BigDecimal carteraPorVencer;      // vencen en los próximos 30 días
    private BigDecimal recaudoMes;            // abonos recibidos en el mes actual
    private long      clientesConMora;        // clientes con al menos 1 doc vencido
    private long      clientesBloqueados;     // tercero_credito.estado = BLOQUEADO
    private long      solicitudesPendientes;  // solicitudes_autorizacion estado=PENDIENTE

    // ── Edades de cartera ─────────────────────────────────────────
    private BigDecimal edad0a30;
    private BigDecimal edad31a60;
    private BigDecimal edad61a90;
    private BigDecimal edadMas90;

    // ── Top vencidas (para alerta) ────────────────────────────────
    private List<CuentaVencidaAlertaDto> alertasVencidas;
}
