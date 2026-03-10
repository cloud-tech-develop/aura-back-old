package com.cloud_technological.aura_pos.dto.kardex;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KardexTableDto {
    private Long id;
    private String tipoMovimiento;
    private BigDecimal cantidad;
    private BigDecimal saldoAnterior;
    private BigDecimal saldoNuevo;
    private BigDecimal costoHistorico;
    private String referenciaOrigen;
    private LocalDateTime createdAt;
    private String productoNombre;
    private String sucursalNombre;
    private String codigoLote;
    private long totalRows;
}
