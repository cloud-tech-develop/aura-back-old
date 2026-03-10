package com.cloud_technological.aura_pos.dto.merma;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MermaDetalleDto {
    private Long id;
    private Long productoId;
    private String productoNombre;
    private Long loteId;
    private String codigoLote;
    private BigDecimal cantidad;
    private BigDecimal costoUnitario;
}
