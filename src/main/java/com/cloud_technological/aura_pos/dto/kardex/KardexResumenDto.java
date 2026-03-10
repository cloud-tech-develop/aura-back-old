package com.cloud_technological.aura_pos.dto.kardex;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class KardexResumenDto {
    private Long sucursalId;
    private String sucursalNombre;
    private Long productoId;
    private String productoNombre;
    private String productoSku;
    private BigDecimal stockActual;
    private BigDecimal stockMinimo;
    private Boolean stockCritico;
}
