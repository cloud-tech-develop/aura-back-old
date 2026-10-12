package com.cloud_technological.aura_pos.dto.nomina.vinculacion;

import java.math.BigDecimal;
import java.time.LocalDate;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * Crear o editar una vinculación. Empleado, jefe y evaluador llegan como
 * terceros (buscador de terceros con rol EMPLEADO) y el servicio resuelve su
 * ficha de empleado. Al editar se ignoran el empleado y el salario:
 * el salario cambia por {@code PUT /api/contrato/{id}/salario} para conservar el
 * histórico.
 */
@Getter
@Setter
public class CreateVinculacionDto {

    // ── General ──────────────────────────────────────────────────────────────

    /** Tercero del empleado; debe tener ficha de empleado en la empresa. */
    @NotNull(message = "El empleado es obligatorio")
    private Long terceroId;

    @NotNull(message = "El tipo de vinculación es obligatorio")
    private Long tipoVinculacionId;

    @NotNull(message = "El cargo es obligatorio")
    private Long cargoId;

    private Long grupoNominaId;

    @NotNull(message = "El tipo de contrato es obligatorio")
    @Pattern(regexp = "INDEFINIDO|FIJO|OBRA_LABOR|PRESTACION_SERVICIOS|APRENDIZAJE",
            message = "Tipo de contrato no válido")
    private String tipoContrato;

    private Boolean temporal = false;

    @NotNull(message = "El tipo de remuneración es obligatorio")
    @Pattern(regexp = "MENSUAL|INTEGRAL|JORNAL|HORA", message = "Tipo de remuneración no válido")
    private String tipoRemuneracion;

    /** Solo al crear. */
    private BigDecimal salarioBase;

    @NotNull(message = "La frecuencia de pago es obligatoria")
    @Pattern(regexp = "MENSUAL|QUINCENAL|SEMANAL", message = "Frecuencia de pago no válida")
    private String frecuenciaPago;

    @DecimalMin(value = "1", message = "Las horas por semana deben ser mayores a cero")
    @DecimalMax(value = "48", message = "Las horas por semana no pueden pasar de 48")
    private BigDecimal horasSemana;

    private Long calendarioId;

    @NotNull(message = "La fecha de inicio es obligatoria")
    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    private LocalDate fechaFinPrueba;

    /** LECTIVA | PRACTICA, solo aprendices. */
    @Pattern(regexp = "LECTIVA|PRACTICA", message = "Fase de aprendizaje no válida")
    private String fase;

    private Boolean esPrincipal = true;

    private Boolean primerEmpleo = false;

    // ── Ubicación y jerarquía ────────────────────────────────────────────────

    private Integer sucursalId;

    @Size(max = 100, message = "El área admite hasta 100 caracteres")
    private String area;

    /** Municipio del lugar de trabajo (el texto lo pone el servicio). */
    private Long municipioTrabajoId;

    @Size(max = 200, message = "La dirección admite hasta 200 caracteres")
    private String direccionTrabajo;

    @Size(max = 30, message = "El teléfono admite hasta 30 caracteres")
    private String telefonoTrabajo;

    /** Tercero del jefe directo (debe tener ficha de empleado). */
    private Long jefeDirectoTerceroId;

    /** Tercero del evaluador (debe tener ficha de empleado). */
    private Long evaluadorTerceroId;

    // ── Seguridad social ─────────────────────────────────────────────────────

    @Size(max = 5)
    private String tipoCotizante;

    @Size(max = 5)
    private String subtipoCotizante;

    private Long riesgoArlId;

    private Boolean enExtranjero = false;

    // ── Soporte ──────────────────────────────────────────────────────────────

    @Pattern(regexp = "RESOLUCION|CONTRATO|OTROSI|ACTA|OTRO", message = "Tipo de acto administrativo no válido")
    private String docInternoTipo;

    @Size(max = 50, message = "El número del documento admite hasta 50 caracteres")
    private String docInternoNumero;

    private LocalDate docInternoFecha;

    @Size(max = 500)
    private String observacion;
}
