package com.cloud_technological.aura_pos.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VentaRecienteDto {
    private Long id;
    private String prefijo;
    private Long consecutivo;
    private String clienteNombre;
    private BigDecimal totalPagar;
    private LocalDateTime fechaEmision;
    private String estadoVenta;
}
