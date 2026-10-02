package com.cloud_technological.aura_pos.dto.inventario;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** Fila del sugerido de compra (V185): un saldo por bodega en o bajo su punto de reorden. */
@Getter
@Setter
public class SugeridoCompraDto {
    private Long inventarioId;
    private Long sucursalId;
    private String sucursalNombre;
    private Long bodegaId;
    private String bodegaNombre;
    private Long productoId;
    private String productoNombre;
    private String productoSku;
    private String unidadAbreviatura;
    private BigDecimal stockActual;
    private BigDecimal stockMinimo;
    private BigDecimal puntoReorden;
    private BigDecimal stockMaximo;
    private BigDecimal cantidadSugerida;
    private BigDecimal costo;
    private BigDecimal valorEstimado;
    /** Sin máximo el sugerido solo vuelve al punto de reorden: conviene definirlo. */
    private Boolean sinMaximo;
    private Long ultimoProveedorId;
    private String ultimoProveedorNombre;
    private LocalDate ultimaCompra;
}
