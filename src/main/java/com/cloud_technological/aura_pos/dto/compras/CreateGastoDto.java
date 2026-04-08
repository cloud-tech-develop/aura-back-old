package com.cloud_technological.aura_pos.dto.compras;

import java.math.BigDecimal;
import java.time.LocalDate;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateGastoDto {

    @NotNull(message = "La sucursal es obligatoria")
    private Integer sucursalId;

    @NotBlank(message = "La categoría es obligatoria")
    private String categoria;

    private String descripcion;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
    private BigDecimal monto;

    private LocalDate fecha;

    @NotNull(message = "Debe indicar si el gasto es deducible")
    private Boolean deducible;
    // Campos tributarios (V54)
    private Long terceroId;
    private Long cuentaContableId;
    private Long centroCostoId;
    private Long periodoContableId;
    private BigDecimal baseIva        = BigDecimal.ZERO;
    private BigDecimal tarifaIva      = BigDecimal.ZERO;
    private BigDecimal valorIva       = BigDecimal.ZERO;
    private BigDecimal baseRetefuente = BigDecimal.ZERO;
    private BigDecimal tarifaRetefuente = BigDecimal.ZERO;
    private BigDecimal valorRetefuente  = BigDecimal.ZERO;
    private BigDecimal baseReteica    = BigDecimal.ZERO;
    private BigDecimal tarifaReteica  = BigDecimal.ZERO;
    private BigDecimal valorReteica   = BigDecimal.ZERO;
    private String tipoDocSoporte;
    private String numeroDocSoporte;
}
