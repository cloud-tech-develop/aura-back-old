package com.cloud_technological.aura_pos.dto.comision;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ComisionConfigDto {
    private Long id;
    private Integer empresaId;
    private String modalidad;       // SERVICIO | VENTA
    private Long productoId;
    private String productoNombre;
    private Long categoriaId;
    private String categoriaNombre;
    private Integer tecnicoId;
    private String tecnicoNombre;
    private String tipo;
    private BigDecimal porcentajeTecnico;
    private BigDecimal porcentajeNegocio;
    private Boolean activo;
}
