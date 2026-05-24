package com.cloud_technological.aura_pos.dto.caja;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DetalleVentaCreditoDto {
    private Long       ventaId;
    private String     prefijo;
    private Long        consecutivo;
    private String     clienteNombre;
    private String     clienteDocumento;
    private BigDecimal totalVenta;     // total_pagar de la venta
    private BigDecimal montoAbonado;   // lo que pagó al momento de la venta
    private BigDecimal montoCredito;   // lo que quedó a crédito (cuenta por cobrar)
}
