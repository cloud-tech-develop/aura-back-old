package com.cloud_technological.aura_pos.dto.cartera.mora;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import lombok.Data;

/** Cartera C5 (intereses de mora) y C9 (deterioro y castigo). docs/PLAN_CARTERA.md */
public final class MoraCastigoDtos {

    private MoraCastigoDtos() {
    }

    // ── Política ─────────────────────────────────────────────────────────────

    @Data
    public static class Politica {
        private boolean interesActivo;
        /** Tasa de mora pactada, % mensual. */
        private BigDecimal tasaMoraMensual = BigDecimal.ZERO;
        /** Tasa de usura vigente que registra el usuario, % mensual. */
        private BigDecimal tasaUsuraMensual = BigDecimal.ZERO;
        private LocalDate usuraVigenteHasta;
        private int diasGracia;
        /** Días de mora mínimos → % de deterioro. */
        private Map<Integer, BigDecimal> deterioroTramos = new TreeMap<>();
        private int diasSugerirCastigo = 360;
        // Solo lectura
        private BigDecimal tasaAplicable;
        private List<String> avisos = new ArrayList<>();
        private LocalDateTime updatedAt;
    }

    // ── Intereses de mora ────────────────────────────────────────────────────

    @Data
    public static class InteresCuenta {
        private Long cuentaCobrarId;
        private String numeroCuenta;
        private String referencia;
        private LocalDate fechaVencimiento;
        private BigDecimal saldo;
        private int diasMora;
        /** Primer día que se cobra (el vencimiento o el último corte facturado). */
        private LocalDate desde;
        private LocalDate hasta;
        private int dias;
        private BigDecimal valor;
    }

    @Data
    public static class InteresCliente {
        private Long terceroId;
        private String terceroNombre;
        private String documento;
        private BigDecimal saldoVencido = BigDecimal.ZERO;
        private BigDecimal valor = BigDecimal.ZERO;
        private List<InteresCuenta> cuentas = new ArrayList<>();
    }

    @Data
    public static class CalculoInteres {
        private LocalDate fecha;
        private boolean interesActivo;
        private BigDecimal tasaMensual;
        private int diasGracia;
        private BigDecimal total = BigDecimal.ZERO;
        private List<InteresCliente> clientes = new ArrayList<>();
        private List<String> avisos = new ArrayList<>();
    }

    @Data
    public static class FacturarInteres {
        private Long terceroId;
        private LocalDate fecha;
        /** Días para pagar la cuenta de intereses (0 = vence el mismo día). */
        private Integer plazoDias;
        /** Si viene, solo esas facturas; si no, todas las que tienen interés. */
        private List<Long> cuentaCobrarIds;
        private String observaciones;
    }

    @Data
    public static class InteresDetalle {
        private Long cuentaCobrarId;
        private String numeroCuenta;
        private LocalDate desde;
        private LocalDate hasta;
        private int dias;
        private BigDecimal saldoBase;
        private BigDecimal valor;
    }

    @Data
    public static class InteresMora {
        private Long id;
        private String numero;
        private LocalDate fecha;
        private Long terceroId;
        private String terceroNombre;
        private String documento;
        private BigDecimal tasaMensual;
        private BigDecimal valor;
        private Long cuentaCobrarId;
        private String numeroCuenta;
        private BigDecimal saldoCuenta;
        private String estado;
        private String observaciones;
        private String motivoAnulacion;
        private String usuarioNombre;
        private LocalDateTime createdAt;
        private List<InteresDetalle> detalle = new ArrayList<>();
    }

    // ── Deterioro ────────────────────────────────────────────────────────────

    @Data
    public static class DeterioroFactura {
        private Long cuentaCobrarId;
        private String numeroCuenta;
        private String terceroNombre;
        private LocalDate fechaVencimiento;
        private int diasMora;
        private BigDecimal saldo;
        private BigDecimal porcentaje;
        private BigDecimal valor;
    }

    @Data
    public static class DeterioroSugerido {
        private LocalDate fecha;
        private Map<Integer, BigDecimal> tramos = new TreeMap<>();
        private BigDecimal totalCalculado = BigDecimal.ZERO;
        /** Saldo crédito de la provisión de cartera (1399) en el mayor. */
        private BigDecimal provisionActual = BigDecimal.ZERO;
        /** Lo que falta provisionar: calculado − provisión. Negativo = sobra provisión. */
        private BigDecimal ajuste = BigDecimal.ZERO;
        private long borradoresPendientes;
        private List<DeterioroFactura> facturas = new ArrayList<>();
    }

    // ── Castigo ──────────────────────────────────────────────────────────────

    @Data
    public static class CandidatoCastigo {
        private Long cuentaCobrarId;
        private String numeroCuenta;
        private String referencia;
        private Long terceroId;
        private String terceroNombre;
        private String documento;
        private LocalDate fechaVencimiento;
        private int diasMora;
        private BigDecimal saldo;
    }

    @Data
    public static class Castigar {
        private List<Long> cuentaCobrarIds;
        private LocalDate fecha;
        private String motivo;
    }

    @Data
    public static class Castigo {
        private Long id;
        private String numero;
        private LocalDate fecha;
        private Long cuentaCobrarId;
        private String numeroCuenta;
        private Long terceroId;
        private String terceroNombre;
        private String documento;
        private BigDecimal valor;
        private BigDecimal contraProvision;
        private BigDecimal contraGasto;
        private int diasMora;
        private String motivo;
        private String estado;
        private String motivoAnulacion;
        private String usuarioNombre;
        private LocalDateTime createdAt;
    }
}
