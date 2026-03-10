package com.cloud_technological.aura_pos.dto.cuentas_cobrar;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CuentaCobrarTableDto {
    private Long id;
    private String numeroCuenta;
    private String clienteNombre;
    private String clienteDocumento;
    private LocalDateTime fechaEmision;
    private LocalDateTime fechaVencimiento;
    private BigDecimal totalDeuda;
    private BigDecimal totalAbonado;
    private BigDecimal saldoPendiente;
    private String estado;
    private Long totalRows;
}
