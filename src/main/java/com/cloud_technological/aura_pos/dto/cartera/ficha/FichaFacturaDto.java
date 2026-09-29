package com.cloud_technological.aura_pos.dto.cartera.ficha;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FichaFacturaDto {
    private Long id;
    private String numeroCuenta;
    private Long ventaId;
    private String numeroVenta;
    private LocalDateTime fechaEmision;
    private LocalDateTime fechaVencimiento;
    private BigDecimal totalDeuda;
    private BigDecimal totalAbonado;
    private BigDecimal saldoPendiente;
    /** Positivo = días vencida; negativo = días que faltan. */
    private Integer diasVencida;
    /** Acuerdo de pago vivo en el que está la cuenta; su vencimiento es el de la próxima cuota. */
    private Long acuerdoId;
    private String acuerdoNumero;
}
