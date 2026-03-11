package com.cloud_technological.aura_pos.dto.terceros;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstadoCuentaClienteDto {

    // ── Datos del cliente ────────────────────────────────────────────────────
    private Long clienteId;
    private String nombreCliente;
    private String tipoDocumento;
    private String numeroDocumento;
    private String email;
    private String telefono;
    private String municipio;

    // ── Resumen financiero ───────────────────────────────────────────────────

    /** Suma de venta.totalPagar de todas sus ventas en el período */
    private BigDecimal totalVentas;

    /** Suma de cuentas por cobrar (deuda total contraída en crédito) */
    private BigDecimal totalDeuda;

    /** Suma de abonos registrados */
    private BigDecimal totalAbonado;

    /** Saldo pendiente actual (deuda - abonos) */
    private BigDecimal saldoPendiente;

    private Long cuentasActivas;
    private Long cuentasVencidas;

    // ── Movimientos cronológicos ─────────────────────────────────────────────
    private List<MovimientoCuentaDto> movimientos;
}
