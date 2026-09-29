package com.cloud_technological.aura_pos.dto.inventario;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HistorialMovimientoDto {
    private Long id;
    private String tipo;
    private Long documentoId;
    private String documentoNumero;
    private LocalDateTime fecha;
    private BigDecimal cantidad;
    private BigDecimal costoUnitario;
    private BigDecimal precioUnitario;
    private BigDecimal saldoAnterior;
    private BigDecimal saldoNuevo;
    private String terceroNombre;
    private String sucursalNombre;
}