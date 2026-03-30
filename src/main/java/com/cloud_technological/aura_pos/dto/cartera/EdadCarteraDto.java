package com.cloud_technological.aura_pos.dto.cartera;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EdadCarteraDto {
    private Long       terceroId;
    private String     terceroNombre;
    private String     numeroDocumento;
    private BigDecimal corriente;    // 0-30 días
    private BigDecimal dias31a60;
    private BigDecimal dias61a90;
    private BigDecimal mas90dias;
    private BigDecimal total;
}
