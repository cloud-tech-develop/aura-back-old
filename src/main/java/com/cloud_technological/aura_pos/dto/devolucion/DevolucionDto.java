package com.cloud_technological.aura_pos.dto.devolucion;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DevolucionDto {
    private Long id;
    private Integer empresaId;
    private Integer sucursalId;
    private Long ventaId;
    private String numeroVenta;
    private Long clienteId;
    private String clienteNombre;
    private Integer usuarioId;
    private Long consecutivo;
    private String tipo;
    private String estado;
    private String motivo;
    private BigDecimal totalDevolucion;
    private Boolean reintegraInventario;
    private String observaciones;
    private String metodoDevolucion;
    private Boolean afectoCartera;
    private BigDecimal montoCarteraAfectado;
    private LocalDateTime createdAt;
    private List<DevolucionDetalleDto> detalles;
}
