package com.cloud_technological.aura_pos.dto.compras;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompraDetalleLoteDto {
    private Long compraDetalleId;
    private Long loteId;
    private String codigoLote;
    private LocalDate fechaVencimiento;
    private LocalDate fechaFabricacion;
    /** En unidad base. */
    private BigDecimal cantidadBase;
}
