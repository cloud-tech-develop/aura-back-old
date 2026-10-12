package com.cloud_technological.aura_pos.dto.contabilidad.cierre;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** Cierre de mes, fases 2 a 5 (docs/PLAN_CIERRE_CONTABLE.md). */
public final class CierreMesDtos {

    private CierreMesDtos() {
    }

    /** OK · ADVERTENCIA (deja cerrar) · BLOQUEA (no deja cerrar) · NO_APLICA. */
    public static final String OK = "OK";
    public static final String ADVERTENCIA = "ADVERTENCIA";
    public static final String BLOQUEA = "BLOQUEA";
    public static final String NO_APLICA = "NO_APLICA";

    @Data
    public static class Chequeo {
        private String codigo;
        private String grupo;
        private String titulo;
        private String estado;
        private String detalle;
        /** Cruces: saldo según el módulo (cartera, proveedores…) y según el mayor. */
        private BigDecimal valorAuxiliar;
        private BigDecimal valorMayor;
        private BigDecimal diferencia;
        /** AJUSTE (botón que arma el comprobante) o RUTA (pantalla donde se corrige). */
        private String accionTipo;
        private String accionValor;
        private String accionTexto;
        /** Hasta 10 ejemplos: comprobantes, cuentas, activos… */
        private List<String> ejemplos = new ArrayList<>();
    }

    @Data
    public static class ChequeoCierre {
        private Long periodoId;
        private String periodo;
        private String estadoPeriodo;
        private LocalDate desde;
        private LocalDate hasta;
        private LocalDateTime generadoEn;
        private boolean puedeCerrar;
        private int bloqueos;
        private int advertencias;
        private List<Chequeo> chequeos = new ArrayList<>();
    }

    @Data
    public static class LineaBalance {
        private String codigo;
        private String nombre;
        private BigDecimal saldoAnterior = BigDecimal.ZERO;
        private BigDecimal debitos = BigDecimal.ZERO;
        private BigDecimal creditos = BigDecimal.ZERO;
        private BigDecimal saldoFinal = BigDecimal.ZERO;
    }

    @Data
    public static class LineaAsiento {
        private String codigo;
        private String nombre;
        private BigDecimal debito;
        private BigDecimal credito;
    }

    @Data
    public static class LineaComparativo {
        private String codigo;
        private String nombre;
        /** INGRESO · COSTO · GASTO, para armar el estado de resultados. */
        private String seccion;
        private BigDecimal actual = BigDecimal.ZERO;
        private BigDecimal mesAnterior = BigDecimal.ZERO;
        private BigDecimal anioAnterior = BigDecimal.ZERO;
    }

    @Data
    public static class Comparativo {
        private String periodo;
        private String mesAnterior;
        private String anioAnterior;
        private List<LineaComparativo> lineas = new ArrayList<>();
    }

    @Data
    public static class ActaCierre {
        private Long periodoId;
        private String periodo;
        private String estado;
        private String empresaNombre;
        private String empresaNit;
        private LocalDate fechaCierre;
        private String cerradoPor;
        private String observaciones;
        private int reaperturas;
        /** El chequeo guardado al cerrar; si el mes sigue abierto, el de ahora. */
        private ChequeoCierre chequeo;
        private boolean chequeoGuardado;
        private List<LineaBalance> balance = new ArrayList<>();
        private String asientoCierreNumero;
        private List<LineaAsiento> asientoCierre = new ArrayList<>();
        private BigDecimal ingresos = BigDecimal.ZERO;
        private BigDecimal costos = BigDecimal.ZERO;
        private BigDecimal gastos = BigDecimal.ZERO;
        private BigDecimal utilidad = BigDecimal.ZERO;
    }

    @Data
    public static class ResultadoAjuste {
        private String tipo;
        private String mensaje;
        private int cantidad;
    }
}
