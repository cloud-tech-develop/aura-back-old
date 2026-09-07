package com.cloud_technological.aura_pos.dto.reportes;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * Una fila del resumen de gastos: lo agrupado por categoría, tercero, centro de
 * costo, cuenta, mes o sucursal.
 *
 * <p>Deducible y no deducible van en columnas separadas, no en un total único:
 * un gasto sin soporte válido baja la utilidad del negocio pero no el impuesto
 * de renta, y sumarlos juntos obliga al contador a rehacer la cuenta a mano.
 */
@Getter
@Setter
public class ReporteGastosLineaDto {

    /** La clave de la agrupación, ya legible (nombre, no id). */
    private String grupo;
    /** El id detrás del grupo, cuando lo hay: sirve para navegar al detalle. */
    private Long grupoId;

    private Integer cantidad;

    private BigDecimal total;
    private BigDecimal totalDeducible;
    private BigDecimal totalNoDeducible;

    /** Lo pagado de una vez y lo que quedó como cuenta por pagar. */
    private BigDecimal totalContado;
    private BigDecimal totalCredito;

    // ── Impuestos y retenciones ─────────────────────────────────────────
    private BigDecimal baseIva;
    private BigDecimal valorIva;
    private BigDecimal valorRetefuente;
    private BigDecimal valorReteica;

    /** Porcentaje del total del reporte que representa este grupo. */
    private BigDecimal participacion;

    private long totalRows;
}
