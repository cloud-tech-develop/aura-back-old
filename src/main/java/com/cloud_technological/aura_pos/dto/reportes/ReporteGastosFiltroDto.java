package com.cloud_technological.aura_pos.dto.reportes;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/**
 * Filtros del reporte de gastos.
 *
 * <p>El mismo objeto alimenta el resumen agrupado, el detalle y las
 * exportaciones: así lo que se exporta es exactamente lo que se ve en pantalla
 * y no un conjunto parecido.
 */
@Getter
@Setter
public class ReporteGastosFiltroDto {

    private Integer page = 0;
    private Integer rows = 50;

    private LocalDate fechaDesde;
    private LocalDate fechaHasta;

    private Integer sucursalId;
    private String categoria;
    private Long terceroId;
    private Long centroCostoId;
    private Long cuentaContableId;
    private Long proyectoId;

    /** CONTADO | CREDITO. */
    private String formaPago;
    /** EFECTIVO | TRANSFERENCIA | TARJETA… */
    private String metodoPago;

    /**
     * true solo deducibles, false solo no deducibles, null ambos.
     *
     * <p>Es la separación que pide el contador en renta: un gasto sin soporte
     * válido suma al resultado del negocio pero no baja el impuesto, y
     * mezclarlos en un mismo total obliga a rehacer la cuenta a mano.
     */
    private Boolean deducible;

    /** Categoría, descripción, número de documento soporte o tercero. */
    private String search;

    /**
     * ACTIVO por defecto. Los eliminados no se cuentan, pero se pueden pedir
     * expresamente para auditar qué se borró en un período.
     */
    private String estado = "ACTIVO";

    // ── Agrupación del resumen ───────────────────────────────────────────
    public static final String POR_CATEGORIA = "CATEGORIA";
    public static final String POR_TERCERO = "TERCERO";
    public static final String POR_CENTRO_COSTO = "CENTRO_COSTO";
    public static final String POR_CUENTA = "CUENTA";
    public static final String POR_MES = "MES";
    public static final String POR_SUCURSAL = "SUCURSAL";

    /** CATEGORIA | TERCERO | CENTRO_COSTO | CUENTA | MES | SUCURSAL. */
    private String agrupacion = POR_CATEGORIA;
}
