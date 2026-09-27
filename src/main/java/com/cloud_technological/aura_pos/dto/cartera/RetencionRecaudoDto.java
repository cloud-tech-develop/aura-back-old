package com.cloud_technological.aura_pos.dto.cartera;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una retención que el cliente le practicó a la empresa al pagar: la
 * descuenta del pago y entrega el certificado.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RetencionRecaudoDto {
    /** RETEFUENTE | RETEIVA | RETEICA */
    private String tipo;
    private BigDecimal valor;
    /** Base sobre la que la calculó el cliente (informativa, opcional). */
    private BigDecimal base;
}
