package com.cloud_technological.aura_pos.dto.devolucion;

import java.time.LocalDate;
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
     * Fecha en que se registra la devolución. Los movimientos de tesorería y el
     * asiento contable se fechan en la fecha de la VENTA original; esta fecha es
     * de registro/control. Si no se envía se asume la fecha actual.
     */
    private LocalDate fechaDevolucion;

    /**
     * Método con el que se devuelve el dinero al cliente (o se cobra el faltante
     * cuando el cambio deja saldo a favor del negocio).
     * EFECTIVO | TRANSFERENCIA | NOTA_CREDITO | SIN_DEVOLUCION
     * SIN_DEVOLUCION = no hay reintegro de dinero (solo nota crédito en cartera si aplica).
     */
    private String metodoDevolucion = "SIN_DEVOLUCION";

    @NotEmpty(message = "Debe incluir al menos un detalle")
    @Valid
    private List<CreateDevolucionDetalleDto> detalles;

    /**
     * Productos que el cliente se lleva en un cambio. Se suman a la venta
     * original (nuevos venta_detalle + salida de inventario) y su valor se neta
     * contra lo devuelto para calcular el faltante a cobrar o el sobrante a
     * reembolsar.
     */
    @Valid
    private List<CreateDevolucionAgregadoDto> productosAgregados;
}
