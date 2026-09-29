package com.cloud_technological.aura_pos.dto.compras;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * Una factura de compra sobre la que todavía se puede emitir nota crédito.
 *
 * <p>Trae el saldo pendiente de su cuenta por pagar porque de eso depende qué
 * puede hacer la nota crédito con la plata: si la factura aún se debe se cruza
 * contra esa deuda; si ya se pagó, el proveedor devuelve el dinero o queda a
 * favor.
 */
@Getter
@Setter
public class CompraAcreditableDto {
    private Long id;
    private String numeroCompra;
    private LocalDateTime fecha;
    private BigDecimal total;
    private String formaPago;

    /** Lo que aún se le debe al proveedor por esta factura; 0 si ya se pagó. */
    private BigDecimal saldoPendiente;

    private long totalRows;
}
