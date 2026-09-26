package com.cloud_technological.aura_pos.dto.cartera.acuerdo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** Cuenta por cobrar incluida en un acuerdo. */
@Getter
@Setter
public class AcuerdoCuentaDto {
    private Long cuentaCobrarId;
    private String numeroCuenta;
    private String numeroVenta;
    private BigDecimal saldoInicial;
    private BigDecimal saldoActual;
    private LocalDateTime fechaVencimientoOriginal;
}
