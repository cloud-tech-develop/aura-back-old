package com.cloud_technological.aura_pos.dto.caja;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DetalleEfectivoDto {
    private Long       ventaId;
    private Integer    consecutivo;
    private BigDecimal montoEfectivo;   // lo que pagó en efectivo en esta venta
    private BigDecimal totalVenta;      // total_pagar de la venta
    private String     estadoVenta;
}
