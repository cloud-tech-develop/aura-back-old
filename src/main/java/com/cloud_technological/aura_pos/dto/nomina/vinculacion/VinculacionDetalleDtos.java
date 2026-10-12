package com.cloud_technological.aura_pos.dto.nomina.vinculacion;

import java.math.BigDecimal;
import java.time.LocalDate;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * DTOs de las pestañas de la vinculación: centros de costo, conceptos propios y
 * novedades permitidas.
 */
public final class VinculacionDetalleDtos {

    private VinculacionDetalleDtos() {
    }

    /** Un centro de costo de la vinculación con su porcentaje del costo laboral. */
    @Getter
    @Setter
    public static class CentroCostoVinculacionDto {
        @NotNull(message = "Elija el centro de costo")
        private Long centroCostoId;
        private String centroCostoCodigo;
        private String centroCostoNombre;
        @NotNull(message = "El porcentaje es obligatorio")
        @DecimalMin(value = "0.01", message = "El porcentaje debe ser mayor a cero")
        @DecimalMax(value = "100", message = "El porcentaje no puede pasar de 100")
        private BigDecimal porcentaje;
    }

    /** Concepto propio de la vinculación (lectura). */
    @Getter
    @Setter
    public static class VinculacionConceptoDto {
        private Long id;
        private String conceptoCodigo;
        private String conceptoNombre;
        /** DEVENGADO | DEDUCCION | APORTE_EMPLEADOR | PROVISION. */
        private String clase;
        private BigDecimal cantidad;
        private BigDecimal valor;
        private BigDecimal porcentaje;
        private LocalDate fechaDesde;
        private LocalDate fechaHasta;
        private String observacion;
    }

    /** Crear o editar un concepto propio de la vinculación. */
    @Getter
    @Setter
    public static class CreateVinculacionConceptoDto {
        @NotBlank(message = "Elija el concepto")
        @Size(max = 30)
        private String conceptoCodigo;
        @DecimalMin(value = "0", message = "La cantidad no puede ser negativa")
        private BigDecimal cantidad;
        @DecimalMin(value = "0", message = "El valor no puede ser negativo")
        private BigDecimal valor;
        @DecimalMin(value = "0", message = "El porcentaje no puede ser negativo")
        @DecimalMax(value = "100", message = "El porcentaje no puede pasar de 100")
        private BigDecimal porcentaje;
        @NotNull(message = "La fecha desde es obligatoria")
        private LocalDate fechaDesde;
        private LocalDate fechaHasta;
        @Size(max = 300, message = "La observación admite hasta 300 caracteres")
        private String observacion;
    }

    /** Novedad permitida (lectura). */
    @Getter
    @Setter
    public static class NovedadPermitidaDto {
        private Long id;
        private String conceptoCodigo;
        private String conceptoNombre;
        private String clase;
        private BigDecimal topeCantidad;
        private BigDecimal topeValor;
        private Boolean requiereAutorizacion;
    }

    /** Crear o editar una novedad permitida. */
    @Getter
    @Setter
    public static class CreateNovedadPermitidaDto {
        @NotBlank(message = "Elija el concepto")
        @Size(max = 30)
        private String conceptoCodigo;
        @DecimalMin(value = "0", message = "El tope de cantidad no puede ser negativo")
        private BigDecimal topeCantidad;
        @DecimalMin(value = "0", message = "El tope de valor no puede ser negativo")
        private BigDecimal topeValor;
        private Boolean requiereAutorizacion = false;
    }
}
