package com.cloud_technological.aura_pos.dto.contabilidad;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlujoCajaProyeccionDto {
    private String fechaVencimiento;
    private String tercero;
    private String referencia;
    private BigDecimal saldo;
    /** CXC | CXP */
    private String tipo;
}
