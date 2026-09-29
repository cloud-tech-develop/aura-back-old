package com.cloud_technological.aura_pos.dto.contabilidad;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Estado de Situación Financiera (Balance General) profesional: detalle a
 * nivel de cuenta agrupado en la estructura clásica corriente / no corriente,
 * con encabezado de la empresa y verificación de la ecuación contable.
 */
@Getter
@Setter
@Builder
public class BalanceGeneralDetalladoDto {

    // ── Encabezado ────────────────────────────────────────────────────
    private String empresaNombre;
    private String nit;
    private String fechaCorte;

    // ── Secciones (cada grupo con sus cuentas) ────────────────────────
    private List<GrupoBalanceDto> activoCorriente;
    private List<GrupoBalanceDto> activoNoCorriente;
    private List<GrupoBalanceDto> pasivoCorriente;
    private List<GrupoBalanceDto> pasivoNoCorriente;
    private List<GrupoBalanceDto> patrimonio;

    // ── Totales ───────────────────────────────────────────────────────
    private BigDecimal totalActivoCorriente;
    private BigDecimal totalActivoNoCorriente;
    private BigDecimal totalActivo;

    private BigDecimal totalPasivoCorriente;
    private BigDecimal totalPasivoNoCorriente;
    private BigDecimal totalPasivo;

    private BigDecimal totalPatrimonio;
    private BigDecimal totalPasivoPatrimonio;

    /** Resultado del ejercicio (ingresos − costos − gastos) mostrado en patrimonio. */
    private BigDecimal resultadoEjercicio;

    /** true si Activo == Pasivo + Patrimonio (diferencia < 1). */
    private boolean cuadra;
    private BigDecimal diferencia;

    @Getter
    @Setter
    @Builder
    public static class GrupoBalanceDto {
        private String codigo;   // grupo PUC de 2 dígitos, p.ej. "13"
        private String nombre;   // nombre del grupo
        private BigDecimal saldo;
        private List<LineaBalanceDto> cuentas;
    }

    @Getter
    @Setter
    @Builder
    public static class LineaBalanceDto {
        private String codigo;
        private String nombre;
        private BigDecimal saldo;
    }
}
