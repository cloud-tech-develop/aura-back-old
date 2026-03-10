package com.cloud_technological.aura_pos.dto.cuentas_cobrar;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CuentaCobrarDto {
    private Long id;
    private Long empresaId;
    private Long ventaId;
    private Long terceroId;
    private String clienteNombre;
    private String clienteDocumento;
    private String numeroCuenta;
    private LocalDateTime fechaEmision;
    private LocalDateTime fechaVencimiento;
    private BigDecimal totalDeuda;
    private BigDecimal totalAbonado;
    private BigDecimal saldoPendiente;
    private String estado;
    private String observaciones;
    private LocalDateTime createdAt;
    private List<AbonoCobrarDto> abonos;
}
