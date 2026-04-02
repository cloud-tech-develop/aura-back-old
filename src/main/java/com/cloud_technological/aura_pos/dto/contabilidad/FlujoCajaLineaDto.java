package com.cloud_technological.aura_pos.dto.contabilidad;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlujoCajaLineaDto {
    private String fecha;
    private String concepto;
    /** INGRESO | EGRESO */
    private String tipo;
    private String categoria;
    private String cuentaBanco;
    private BigDecimal monto;
}
