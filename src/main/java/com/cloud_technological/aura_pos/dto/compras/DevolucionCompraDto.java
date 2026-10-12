package com.cloud_technological.aura_pos.dto.compras;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * Una devolución en compras: la nota crédito de compra vista como lo que es
 * para el usuario (docs/PLAN_EXCEL_IMPORTADORES_MODULOS.md §3). Los importes
 * van en positivo: en la tabla `compra` la NC se guarda en negativo.
 */
@Getter
@Setter
public class DevolucionCompraDto {
    private Long id;
    /** Número de la nota crédito que entregó el proveedor. */
    private String numeroCompra;
    private LocalDateTime fecha;
    private String proveedorNombre;
    private String sucursalNombre;
    private String bodegaNombre;

    /** Factura que corrige. */
    private Long compraOrigenId;
    private String compraOrigenNumero;

    /** CRUCE_CXP | DEVOLUCION_DINERO | SALDO_A_FAVOR. */
    private String destinoNotaCredito;
    /** Motivo de la devolución. */
    private String observaciones;
    private String estado;

    /** Unidades devueltas (unidad base). */
    private BigDecimal unidades;
    private BigDecimal total;

    private long totalRows;
}
