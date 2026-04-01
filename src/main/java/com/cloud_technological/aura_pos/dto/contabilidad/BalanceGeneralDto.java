package com.cloud_technological.aura_pos.dto.contabilidad;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class BalanceGeneralDto {
    private String hasta;
    private BigDecimal totalActivo;
    private BigDecimal totalPasivo;
    private BigDecimal totalPatrimonio;
    private BigDecimal totalIngreso;
    private BigDecimal totalGasto;
    private BigDecimal totalCosto;
    private BigDecimal utilidadNeta;      // totalIngreso - totalGasto - totalCosto
    private BigDecimal ecuacionContable;  // totalActivo - (totalPasivo + totalPatrimonio + utilidadNeta)
}
