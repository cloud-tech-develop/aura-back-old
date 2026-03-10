package com.cloud_technological.aura_pos.dto.facturacion;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotaContableDto {
    private Long id;
    private Long facturaId;
    private Long compraId;
    private BigDecimal valor;
    private String banco;
    private Integer tipo;
    private String nota;
    private Integer usuarioId;
    private String metodoPago;
    private LocalDateTime createdAt;
}
