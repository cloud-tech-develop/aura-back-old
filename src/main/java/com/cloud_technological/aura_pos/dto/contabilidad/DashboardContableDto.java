package com.cloud_technological.aura_pos.dto.contabilidad;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Resumen del Centro de Contabilidad: todo lo que la pantalla /contabilidad
 * necesita en una sola llamada. Sale del mayor (asientos CONTABILIZADOS, sin
 * los de CIERRE), con la misma regla que el estado de resultados.
 */
@Getter @Setter
public class DashboardContableDto {

    private int anio;
    private int mes;

    /** Mes consultado y el anterior, para la variación de los KPIs. */
    private ResultadoMesDto mesActual;
    private ResultadoMesDto mesAnterior;

    /** Enero..mes del año consultado. */
    private List<ResultadoMesDto> serie = new ArrayList<>();

    /** Gastos del mes agrupados por cuenta de dos dígitos (51, 52, 53, 54…). */
    private List<GrupoGastoDto> distribucionGastos = new ArrayList<>();

    private EstadoContableDto estado;

    @Getter @Setter
    public static class ResultadoMesDto {
        private int anio;
        private int mes;
        private BigDecimal ingresos = BigDecimal.ZERO;
        private BigDecimal costos = BigDecimal.ZERO;
        private BigDecimal gastos = BigDecimal.ZERO;

        /** Ingresos − costos − gastos. */
        public BigDecimal getUtilidad() {
            return ingresos.subtract(costos).subtract(gastos);
        }
    }

    @Getter @Setter
    public static class GrupoGastoDto {
        private String codigo;
        private String nombre;
        private BigDecimal valor;
    }

    @Getter @Setter
    public static class EstadoContableDto {
        /** ABIERTO | CERRADO | SIN_PERIODO (el mes aún no tiene período creado). */
        private String periodoEstado;
        /** Comprobantes contabilizados del mes. */
        private long comprobantesMes;
        /** Comprobantes en borrador de todos los meses (bloquean el cierre). */
        private long comprobantesBorrador;
        /** Extractos bancarios sin conciliar. */
        private long conciliacionesAbiertas;
        /** Año cuyo cierre se reporta (el anterior al consultado). */
        private int cierreAnualAnio;
        /** NO_INICIADO | PROVISIONADO | CERRADO. */
        private String cierreAnualEstado;
    }
}
