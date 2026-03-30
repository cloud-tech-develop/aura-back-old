package com.cloud_technological.aura_pos.dto.comision;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ComisionConfigTableDto {
    private Long id;
    private String modalidad;       // SERVICIO | VENTA
    private String productoNombre;  // null si es por categoría
    private String categoriaNombre; // null si es por producto
    private String tecnicoNombre;
    private String tipo;
    private BigDecimal porcentajeTecnico;
    private BigDecimal porcentajeNegocio;
    private Boolean activo;
    private Long totalRows;
}
