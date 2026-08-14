package com.cloud_technological.aura_pos.dto.nomina.electronica;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Payload del documento soporte de nómina electrónica.
 *
 * <p><b>⚠️ Estructura provisional.</b> Está modelada sobre el esquema de la DIAN
 * (UBL de nómina), pero <b>el contrato exacto de Factus para nómina no se ha
 * verificado</b> — la integración actual del proyecto es solo factura de venta.
 * Al conectar con Factus hay que contrastar nombres de campo contra su
 * documentación y ajustar. Ver {@code FactusNominaService}.
 */
@Getter
@Setter
public class NominaElectronicaPayload {

    private String tipoDocumento = "102";   // 102 = nómina individual; 103 = ajuste
    private String prefijo;
    private Long consecutivo;
    private LocalDate fechaGeneracion;
    private Integer agno;
    private Integer mes;

    private Periodo periodo = new Periodo();
    private Empleador empleador = new Empleador();
    private Trabajador trabajador = new Trabajador();
    private Pago pago = new Pago();

    /**
     * La DIAN exige cada devengado en SU etiqueta (Basico, Transporte, HEDs,
     * Vacaciones, Prima, Cesantias...), no un total. De ahí sale
     * {@code concepto_nomina.codigo_dian}.
     */
    private List<Concepto> devengados = new ArrayList<>();
    private List<Concepto> deducciones = new ArrayList<>();

    private BigDecimal totalDevengado = BigDecimal.ZERO;
    private BigDecimal totalDeducciones = BigDecimal.ZERO;
    private BigDecimal comprobanteTotal = BigDecimal.ZERO;

    /** Solo para notas de ajuste (tipoDocumento = 103). */
    private String cuneNominaAjustada;

    @Getter @Setter
    public static class Periodo {
        private LocalDate fechaIngreso;
        private LocalDate fechaRetiro;
        private LocalDate fechaLiquidacionInicio;
        private LocalDate fechaLiquidacionFin;
        private Integer tiempoLaborado;
    }

    @Getter @Setter
    public static class Empleador {
        private String razonSocial;
        private String nit;
        private String dv;
        private String pais = "CO";
        private String departamento;
        private String municipio;
        private String direccion;
    }

    @Getter @Setter
    public static class Trabajador {
        /** Código DIAN del tipo de trabajador (01 = dependiente). */
        private String tipoTrabajador = "01";
        private String subTipoTrabajador = "00";
        private Boolean altoRiesgoPension = Boolean.FALSE;
        private String tipoDocumento;
        private String numeroDocumento;
        // Desagregado: la DIAN los exige por separado. Viene de tercero (Fase 1).
        private String primerApellido;
        private String segundoApellido;
        private String primerNombre;
        private String otrosNombres;
        private String lugarTrabajoPais = "CO";
        private String lugarTrabajoDepartamento;
        private String lugarTrabajoMunicipio;
        private String lugarTrabajoDireccion;
        private Boolean salarioIntegral = Boolean.FALSE;
        private String tipoContrato;
        private BigDecimal sueldo = BigDecimal.ZERO;
    }

    @Getter @Setter
    public static class Pago {
        private String forma = "1";   // 1 = contado
        private String metodo;        // código DIAN del medio de pago
        private String banco;
        private String tipoCuenta;
        private String numeroCuenta;
    }

    /** Una línea del documento. {@code codigoDian} es la etiqueta que exige la DIAN. */
    @Getter @Setter
    public static class Concepto {
        private String codigoDian;
        private String nombre;
        private BigDecimal cantidad;
        private BigDecimal porcentaje;
        private BigDecimal valor;

        public Concepto() {}

        public Concepto(String codigoDian, String nombre, BigDecimal valor) {
            this.codigoDian = codigoDian;
            this.nombre = nombre;
            this.valor = valor;
        }
    }
}
