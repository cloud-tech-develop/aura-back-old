package com.cloud_technological.aura_pos.dto.terceros;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
public class MovimientoCuentaDto {

    /** VENTA, CUENTA_COBRAR, ABONO, NOTA_CREDITO, NOTA_DEBITO */
    private String tipo;

    private LocalDateTime fecha;

    /** Número de venta, cuenta, etc. */
    private String referencia;

    private String descripcion;

    /** Aumenta la deuda (ventas, notas débito) */
    private BigDecimal cargo;

    /** Reduce la deuda (abonos, notas crédito) */
    private BigDecimal abono;

    /** Saldo acumulado hasta este movimiento */
    private BigDecimal saldoAcumulado;

    /** Solo aplica a tipo VENTA: true = venta a crédito (tiene CuentaCobrar asociada) */
    private boolean esCredito;
}
