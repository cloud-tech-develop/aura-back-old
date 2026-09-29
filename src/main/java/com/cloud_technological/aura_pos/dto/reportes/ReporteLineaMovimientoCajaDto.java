package com.cloud_technological.aura_pos.dto.reportes;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReporteLineaMovimientoCajaDto {
    private String fecha;
    private String tipo;
    private String concepto;
    private BigDecimal monto;
    private String cajaNombre;
    private String usuarioNombre;
}
