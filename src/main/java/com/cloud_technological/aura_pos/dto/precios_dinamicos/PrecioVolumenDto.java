package com.cloud_technological.aura_pos.dto.precios_dinamicos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PrecioVolumenDto {
    private Long id;
    private Long empresaId;
    private Long productoPresentacionId;
    private String productoPresentacionNombre;
    private String productoNombre;
    private Integer cantidadMinima;
    private Integer cantidadMaxima;
    private BigDecimal precioUnitario;
    private Boolean activo;
    private String observaciones;
    private LocalDateTime createdAt;
}
