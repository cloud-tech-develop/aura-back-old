package com.cloud_technological.aura_pos.dto.contabilidad;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** Aplicación (cruce) de un comprobante contra una cuenta por cobrar/pagar. */
@Getter
@Setter
public class AplicacionCarteraDto {
    private String tipo;       // CXC | CXP
    private Long cuentaId;     // id de la cuenta por cobrar o pagar
    private BigDecimal monto;  // valor aplicado
}
