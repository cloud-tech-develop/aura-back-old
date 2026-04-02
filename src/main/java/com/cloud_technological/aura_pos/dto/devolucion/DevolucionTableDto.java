package com.cloud_technological.aura_pos.dto.devolucion;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DevolucionTableDto {
    private Long id;
    private Long consecutivo;
    private Long ventaId;
    private Long ventaConsecutivo;
    private String clienteNombre;
    private String tipo;
    private String estado;
    private BigDecimal totalDevolucion;
    private String motivo;
    private String createdAt;
    private long totalRows;
}
