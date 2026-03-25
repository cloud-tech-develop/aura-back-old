package com.cloud_technological.aura_pos.dto.cierre_contable;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MovimientoCierreDto {
    private String     tipo;       // INGRESO | EGRESO
    private String     concepto;
    private BigDecimal monto;
    private String     fecha;
    private String     cajaNombre;
    private String     usuarioNombre;
}
