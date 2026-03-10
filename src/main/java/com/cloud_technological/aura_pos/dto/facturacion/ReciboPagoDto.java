package com.cloud_technological.aura_pos.dto.facturacion;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReciboPagoDto {
    private Long id;
    private Long facturaId;
    private BigDecimal valor;
    private String banco;
    private String tipo;
    private String descripcion;
    private Integer usuarioId;
    private String metodoPago;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
