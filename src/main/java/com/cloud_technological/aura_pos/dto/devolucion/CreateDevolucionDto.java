package com.cloud_technological.aura_pos.dto.devolucion;

import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateDevolucionDto {

    @NotNull(message = "El ventaId es obligatorio")
    private Long ventaId;

    /** TOTAL | PARCIAL */
    private String tipo = "PARCIAL";

    @NotBlank(message = "El motivo es obligatorio")
    private String motivo;

    private Boolean reintegraInventario = true;

    private String observaciones;

    /**
     * Método con el que se devuelve el dinero al cliente.
     * EFECTIVO | TRANSFERENCIA | NOTA_CREDITO | SIN_DEVOLUCION
     * SIN_DEVOLUCION = no hay reintegro de dinero (solo nota crédito en cartera si aplica).
     */
    private String metodoDevolucion = "SIN_DEVOLUCION";

    @NotEmpty(message = "Debe incluir al menos un detalle")
    @Valid
    private List<CreateDevolucionDetalleDto> detalles;
}
