package com.cloud_technological.aura_pos.dto.producto_presentacion;

import java.math.BigDecimal;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateProductoPresentacionDto {
    @NotNull(message = "El producto es obligatorio")
    private Long productoId;
    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;
    private String codigoBarras;
    @NotNull(message = "El factor de conversión es obligatorio")
    private BigDecimal factorConversion;
    private Boolean esDefaultCompra;
    private Boolean esDefaultVenta;
    @NotNull(message = "El precio es obligatorio")
    private BigDecimal precio;
    @NotNull(message = "El costo es obligatorio")
    private BigDecimal costo;
    private Boolean activo = true;
}
