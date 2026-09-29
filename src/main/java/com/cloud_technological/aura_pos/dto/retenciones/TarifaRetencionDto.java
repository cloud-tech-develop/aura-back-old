package com.cloud_technological.aura_pos.dto.retenciones;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class TarifaRetencionDto {
    private Long id;
    private Integer empresaId;
    private String tipo;
    private String concepto;
    private String codigoConcepto;
    private BigDecimal tarifaNatural;
    private BigDecimal tarifaJuridica;
    private BigDecimal baseMinima;
    private Boolean activo;
}
