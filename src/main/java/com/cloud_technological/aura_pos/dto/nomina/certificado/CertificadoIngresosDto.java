package com.cloud_technological.aura_pos.dto.nomina.certificado;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * Certificado de ingresos y retenciones — formato 220 DIAN.
 *
 * <p>Obligación <b>anual</b>: se entrega al empleado antes del 31 de marzo del
 * año siguiente.
 *
 * <p>Sale de agregar {@code nomina_detalle} del año por concepto. Depende de la
 * Fase 4.5 (retefuente) para tener qué reportar en el renglón de retención.
 */
@Getter
@Setter
public class CertificadoIngresosDto {

    private Integer agno;

    // ── Agente retenedor ────────────────────────────────────────────────────
    private String empresaRazonSocial;
    private String empresaNit;
    private String empresaDireccion;

    // ── Empleado ────────────────────────────────────────────────────────────
    private String tipoDocumento;
    private String numeroDocumento;
    private String apellido1;
    private String apellido2;
    private String nombre1;
    private String nombre2;

    // ── Ingresos (renglones del formato) ────────────────────────────────────
    private BigDecimal pagosSalarios = BigDecimal.ZERO;
    private BigDecimal cesantiasEIntereses = BigDecimal.ZERO;
    private BigDecimal gastosRepresentacion = BigDecimal.ZERO;
    private BigDecimal pensionesJubilacion = BigDecimal.ZERO;
    private BigDecimal otrosPagos = BigDecimal.ZERO;
    private BigDecimal totalIngresos = BigDecimal.ZERO;

    // ── Aportes (ingresos no constitutivos) ─────────────────────────────────
    private BigDecimal aportesSalud = BigDecimal.ZERO;
    private BigDecimal aportesPension = BigDecimal.ZERO;
    private BigDecimal aportesFondoSolidaridad = BigDecimal.ZERO;
    private BigDecimal aportesVoluntarios = BigDecimal.ZERO;
    private BigDecimal aportesAfc = BigDecimal.ZERO;

    // ── Retención ───────────────────────────────────────────────────────────
    private BigDecimal retencionPracticada = BigDecimal.ZERO;
}
