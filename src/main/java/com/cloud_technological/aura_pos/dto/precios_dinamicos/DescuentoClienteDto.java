package com.cloud_technological.aura_pos.dto.precios_dinamicos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DescuentoClienteDto {
    private Long id;
    private Long empresaId;
    private Long terceroId;
    private String terceroNombre;
    private String terceroDocumento;
    private Long categoriaId;
    private String categoriaNombre;
    private BigDecimal porcentajeDescuento;
    private String tipoDescuento;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private Boolean activo;
    private String observaciones;
    private LocalDateTime createdAt;
    private Long total_rows;
}
