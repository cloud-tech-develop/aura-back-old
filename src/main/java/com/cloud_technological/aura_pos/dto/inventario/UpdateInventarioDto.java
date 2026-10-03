package com.cloud_technological.aura_pos.dto.inventario;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateInventarioDto {
    private BigDecimal stockMinimo;
    private BigDecimal stockMaximo;
    private BigDecimal puntoReorden;
    private BigDecimal stockActual;
    private String ubicacion;
    /** Obligatorio si cambia stockActual: queda en el kardex como referencia del ajuste. */
    private String motivoAjuste;
}