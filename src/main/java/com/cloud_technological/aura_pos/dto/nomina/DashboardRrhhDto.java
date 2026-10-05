package com.cloud_technological.aura_pos.dto.nomina;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Resumen del Centro de Recursos Humanos (/recursos-humanos en el front), en una
 * sola llamada. La nómina de un mes es la de los períodos que TERMINAN en ese mes
 * (una quincena del 16 al 31 cuenta en el mes de su fecha fin), sin anuladas.
 */
@Getter @Setter
public class DashboardRrhhDto {

    private int anio;
    private int mes;

    /** Mes consultado y el anterior, para la variación de los KPIs. */
    private NominaMesDto mesActual;
    private NominaMesDto mesAnterior;

    /** Enero..mes del año consultado. */
    private List<NominaMesDto> serie = new ArrayList<>();

    /** Costo del mes por rubro (salarios, auxilio, seguridad social…), sin los que dan cero. */
    private List<RubroCostoDto> distribucionCosto = new ArrayList<>();

    private PersonalDto personal;
    private EstadoNominaDto estado;

    @Getter @Setter
    public static class NominaMesDto {
        private int anio;
        private int mes;
        private BigDecimal devengado = BigDecimal.ZERO;
        /** Seguridad social y parafiscales a cargo del empleador. */
        private BigDecimal aportes = BigDecimal.ZERO;
        /** Prima, cesantías, intereses y vacaciones. */
        private BigDecimal provisiones = BigDecimal.ZERO;
        private BigDecimal neto = BigDecimal.ZERO;
        /** Empleados liquidados en el mes. */
        private long empleadosLiquidados;

        /** Lo que le cuesta la nómina a la empresa: devengado + aportes + provisiones. */
        public BigDecimal getCostoTotal() {
            return devengado.add(aportes).add(provisiones);
        }
    }

    @Getter @Setter
    public static class RubroCostoDto {
        private String codigo;
        private String nombre;
        private BigDecimal valor;
    }

    @Getter @Setter
    public static class PersonalDto {
        /** Vinculados al cierre del mes consultado. */
        private long activos;
        private long activosMesAnterior;
        private long ingresosMes;
        private long retirosMes;
    }

    @Getter @Setter
    public static class EstadoNominaDto {
        /** ABIERTO | LIQUIDADO | PAGADO | SIN_PERIODO: el último período que termina en el mes. */
        private String periodoEstado;
        private String periodoDescripcion;
        private long nominasBorrador;
        /** Aprobadas y sin pagar. */
        private long nominasPorPagar;
        /** Novedades de asistencia pendientes de aprobar (todas las fechas). */
        private long novedadesPendientes;
        /** Contratos de empleados activos que vencen en los próximos 30 días. */
        private long contratosPorVencer;
    }
}
