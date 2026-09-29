package com.cloud_technological.aura_pos.dto.cartera.recibo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReciboCajaTableDto {
    private Long id;
    private String numero;
    private Long terceroId;
    private String terceroNombre;
    private String terceroDocumento;
    private LocalDateTime fechaPago;
    private BigDecimal valorRecibido;
    private BigDecimal valorAplicado;
    private BigDecimal valorAnticipo;
    private String metodoPago;
    private String referencia;
    private String estado;
    private Integer facturas;
    private String usuarioNombre;
    private Long totalRows;
}
