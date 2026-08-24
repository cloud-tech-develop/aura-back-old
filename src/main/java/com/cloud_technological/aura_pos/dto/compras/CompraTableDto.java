package com.cloud_technological.aura_pos.dto.compras;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompraTableDto {
    private Long id;
    private String numeroCompra;
    private String proveedorNombre;
    private String sucursalNombre;
    private LocalDateTime fecha;
    private BigDecimal total;
    private String estado;

    /** FACTURA_COMPRA | NOTA_DEBITO | NOTA_CREDITO | RECIBO. */
    private String tipoDocumento;

    /**
     * Factura que corrige, cuando la fila es una nota crédito. Sin esto el
     * listado muestra un documento en negativo sin decir a qué compra pertenece.
     */
    private Long compraOrigenId;

    private long totalRows;
}
