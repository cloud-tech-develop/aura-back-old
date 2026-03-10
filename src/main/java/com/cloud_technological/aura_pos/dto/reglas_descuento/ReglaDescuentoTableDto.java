package com.cloud_technological.aura_pos.dto.reglas_descuento;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReglaDescuentoTableDto {
    private Long id;
    private String nombre;
    private String categoriaNombre;
    private String productoNombre;
    private String tipoDescuento;
    private BigDecimal valor;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private Boolean activo;
    private long totalRows;
}
