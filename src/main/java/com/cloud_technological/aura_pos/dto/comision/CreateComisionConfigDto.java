package com.cloud_technological.aura_pos.dto.comision;

import java.math.BigDecimal;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateComisionConfigDto {

    // SERVICIO | VENTA  (default: SERVICIO)
    private String modalidad;

    // Para SERVICIO: obligatorio. Para VENTA: obligatorio si categoriaId es null
    private Long productoId;

    // Solo para VENTA: obligatorio si productoId es null
    private Long categoriaId;

    // Técnico (SERVICIO) o vendedor (VENTA) — ambos son usuario; opcional en ambos casos
    private Integer tecnicoId;

    @NotNull(message = "El tipo es requerido")
    private String tipo; // PORCENTAJE | VALOR_FIJO

    @NotNull(message = "El porcentaje del técnico / vendedor es requerido")
    @DecimalMin(value = "0.01", message = "El porcentaje debe ser mayor a 0")
    @DecimalMax(value = "100", message = "El porcentaje no puede superar 100")
    private BigDecimal porcentajeTecnico;

    // Solo relevante para modalidad SERVICIO (se calcula automáticamente para VENTA)
    @DecimalMin(value = "0", message = "El porcentaje del negocio no puede ser negativo")
    @DecimalMax(value = "99.99", message = "El porcentaje debe ser menor a 100")
    private BigDecimal porcentajeNegocio;

    private Boolean activo;
}
