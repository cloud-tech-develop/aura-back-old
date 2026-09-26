package com.cloud_technological.aura_pos.dto.inventario;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** Lote vencido o por vencer, con lo que vale en costo y en venta. */
@Getter
@Setter
public class VencimientoLoteDto {
    private Long loteId;
    private Long productoId;
    private String productoNombre;
    private String productoSku;
    private String categoriaNombre;
    private Long sucursalId;
    private String sucursalNombre;
    private String codigoLote;
    private LocalDate fechaVencimiento;
    /** Negativo = ya venció. */
    private Integer diasParaVencer;
    private BigDecimal stockActual;
    private String unidadAbreviatura;
    private BigDecimal costoUnitario;
    /** stock × costo del lote: la plata inmovilizada. */
    private BigDecimal valorCosto;
    private BigDecimal precioVenta;
    /** stock × precio de venta del producto: lo que se deja de vender. */
    private BigDecimal valorVenta;
}
