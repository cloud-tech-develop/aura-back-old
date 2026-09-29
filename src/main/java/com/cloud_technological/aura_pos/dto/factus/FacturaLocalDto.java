package com.cloud_technological.aura_pos.dto.factus;

import java.math.BigDecimal;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Datos de una factura tomados de la venta LOCAL (no de Factus), para prellenar
 * la nota crédito/débito: trae el CUFE y los ítems con su IVA desde la BD.
 */
@Getter
@Setter
public class FacturaLocalDto {
    private Long ventaId;
    private String numero;
    private String cufe;
    private BigDecimal total;
    private List<FactusItemDto> items;
}
