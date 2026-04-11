package com.cloud_technological.aura_pos.dto.contabilidad;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class BalanceComprobacionLineaDto {
    private Long cuentaId;
    private String codigo;
    private String nombre;
    private String tipo;
    private String naturaleza;
    private BigDecimal totalDebito;
    private BigDecimal totalCredito;
    private BigDecimal saldo;
}
