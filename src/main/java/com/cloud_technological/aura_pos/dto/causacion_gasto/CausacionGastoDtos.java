package com.cloud_technological.aura_pos.dto.causacion_gasto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** DTOs de la causación de gastos y servicios (docs/PLAN_EXCEL_IMPORTADORES_MODULOS.md §4). */
public final class CausacionGastoDtos {

    private CausacionGastoDtos() {
    }

    /** Lo que manda el formulario (también para /calcular, que no graba). */
    @Getter
    @Setter
    public static class Crear {
        private Long terceroId;
        private Integer sucursalId;
        private LocalDate fecha;
        private LocalDate fechaVencimiento;
        /** FACTURA | DOCUMENTO_SOPORTE | CUENTA_COBRO | OTRO */
        private String tipoSoporte;
        private String numeroSoporte;
        private String descripcion;
        /** Cuenta por pagar (2205, 2335…); sin ella, la de proveedores. */
        private Long cuentaPagarContableId;
        private List<LineaCrear> lineas = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class LineaCrear {
        private Long cuentaId;
        private String descripcion;
        private Long centroCostoId;
        private BigDecimal base;
        private BigDecimal tarifaIva;
        /** null = true. */
        private Boolean ivaDescontable;
        /** Concepto de retención en la fuente (tabla de tarifas). */
        private Long tarifaRetencionId;
        /** Tarifa de retefuente escrita a mano; manda sobre la del concepto. */
        private BigDecimal tarifaRetefuente;
        /** % del IVA que se retiene (15 normalmente). */
        private BigDecimal tarifaReteiva;
        /** % de ReteICA (9,66 por mil = 0,966). */
        private BigDecimal tarifaReteica;
    }

    /** Una línea ya calculada. */
    @Getter
    @Setter
    public static class Linea {
        private Long id;
        private Long cuentaId;
        private String cuentaCodigo;
        private String cuentaNombre;
        private String descripcion;
        private Long centroCostoId;
        private String centroCostoNombre;
        private BigDecimal base;
        private BigDecimal tarifaIva;
        private BigDecimal valorIva;
        private Boolean ivaDescontable;
        private Long tarifaRetencionId;
        private String conceptoRetencion;
        private BigDecimal tarifaRetefuente;
        private BigDecimal valorRetefuente;
        private BigDecimal tarifaReteiva;
        private BigDecimal valorReteiva;
        private BigDecimal tarifaReteica;
        private BigDecimal valorReteica;
    }

    /** Resultado de /calcular: líneas con valores, totales y avisos. */
    @Getter
    @Setter
    public static class Calculo {
        private List<Linea> lineas = new ArrayList<>();
        private BigDecimal subtotal = BigDecimal.ZERO;
        private BigDecimal iva = BigDecimal.ZERO;
        private BigDecimal retefuente = BigDecimal.ZERO;
        private BigDecimal reteiva = BigDecimal.ZERO;
        private BigDecimal reteica = BigDecimal.ZERO;
        private BigDecimal totalPagar = BigDecimal.ZERO;
        /** Persona natural o jurídica según el tercero (define la tarifa del concepto). */
        private String tipoPersona;
        private List<String> avisos = new ArrayList<>();
    }

    /** Detalle de una causación grabada. */
    @Getter
    @Setter
    public static class Detalle extends Calculo {
        private Long id;
        private String numero;
        private Long terceroId;
        private String terceroNombre;
        private String terceroDocumento;
        private Integer sucursalId;
        private String sucursalNombre;
        private LocalDate fecha;
        private LocalDate fechaVencimiento;
        private String tipoSoporte;
        private String numeroSoporte;
        private String descripcion;
        private Long cuentaPagarContableId;
        private String cuentaPagarCodigo;
        private String cuentaPagarNombre;
        private Long cuentaPagarId;
        /** Lo que queda por pagar en la cuenta por pagar. */
        private BigDecimal saldoPendiente;
        private String estado;
        private String motivoAnulacion;
        private LocalDateTime createdAt;
        /** Comprobante del asiento vigente, si ya se contabilizó. */
        private String comprobante;
    }

    /** Fila del listado. */
    @Getter
    @Setter
    public static class Fila {
        private Long id;
        private Integer consecutivo;
        private LocalDate fecha;
        private LocalDate fechaVencimiento;
        private String terceroNombre;
        private String tipoSoporte;
        private String numeroSoporte;
        private String descripcion;
        private BigDecimal subtotal;
        private BigDecimal iva;
        private BigDecimal retenciones;
        private BigDecimal totalPagar;
        private BigDecimal saldoPendiente;
        private String estado;
        private long totalRows;
    }

    @Getter
    @Setter
    public static class Anular {
        private String motivo;
    }
}
