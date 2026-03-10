package com.cloud_technological.aura_pos.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MovimientoRecienteDto {
    private Long id;
    private String tipoMovimiento;
    private String productoNombre;
    private String sucursalNombre;
    private BigDecimal cantidad;
    private BigDecimal saldoNuevo;
    private LocalDateTime createdAt;
}
