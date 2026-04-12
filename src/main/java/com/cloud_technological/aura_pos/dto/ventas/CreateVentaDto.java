package com.cloud_technological.aura_pos.dto.ventas;

import java.time.LocalDateTime;
import java.util.List;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateVentaDto {
    private Long clienteId; // opcional, puede ser consumidor final
    private Long turnoCajaId;   // null cuando el usuario es VENDEDOR (sin caja)
    private Integer sucursalId; // requerido cuando turnoCajaId es null
    private String tipoDocumento = "POS";
    private String observaciones;
    private LocalDateTime fechaVencimiento; // Para cuentas por cobrar
    @NotEmpty(message = "Debe agregar al menos un producto")
    private List<CreateVentaDetalleDto> detalles;
    @NotEmpty(message = "Debe agregar al menos un método de pago")
    private List<CreateVentaPagoDto> pagos;
    private java.math.BigDecimal descuentoGeneral;
}

