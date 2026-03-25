package com.cloud_technological.aura_pos.dto.reconteo;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReconteoDetalleResponseDto {
    private Long id;
    private Long productoId;
    private String productoNombre;
    private String productoSku;
    private Long loteId;
    private String codigoLote;
    private BigDecimal stockSistema;
    private BigDecimal stockContado;
    private BigDecimal diferencia;
    private Boolean ajusteAplicado;
}
