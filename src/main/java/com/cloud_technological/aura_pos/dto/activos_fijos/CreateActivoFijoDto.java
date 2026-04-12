package com.cloud_technological.aura_pos.dto.activos_fijos;

import java.math.BigDecimal;
import java.time.LocalDate;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateActivoFijoDto {

    @NotBlank(message = "El código es obligatorio")
    private String codigo;

    @NotBlank(message = "La descripción es obligatoria")
    private String descripcion;

    @NotBlank(message = "La categoría es obligatoria")
    private String categoria;

    @NotNull(message = "La fecha de adquisición es obligatoria")
    private LocalDate fechaAdquisicion;

    @NotNull(message = "El valor de compra es obligatorio")
    @DecimalMin(value = "0.01", message = "El valor debe ser mayor a 0")
    private BigDecimal valorCompra;

    @NotNull(message = "La vida útil en meses es obligatoria")
    @Min(value = 1, message = "La vida útil debe ser al menos 1 mes")
    private Integer vidaUtilMeses;

    private String metodoDepreciacion = "LINEA_RECTA";

    private BigDecimal valorResidual = BigDecimal.ZERO;

    private String ubicacion;
    private String responsable;
    private Long cuentaActivoId;
    private Long cuentaDepreciacionId;
    private Long cuentaGastoDepId;
    private Long centroCostoId;
    private Long periodoContableId;
    private Long terceroId;
    private String observaciones;
}
