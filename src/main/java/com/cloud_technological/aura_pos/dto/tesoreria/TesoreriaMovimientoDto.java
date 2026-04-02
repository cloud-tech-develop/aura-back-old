package com.cloud_technological.aura_pos.dto.tesoreria;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TesoreriaMovimientoDto {
    private Long id;
    private Long cuentaBancariaId;
    private String cuentaBancariaNombre;
    private String tipo;
    private BigDecimal monto;
    private String concepto;
    private String beneficiario;
    private String referencia;
    private LocalDate fecha;
    private String categoria;
    private Boolean conciliado;
    private Boolean anulado;
}
