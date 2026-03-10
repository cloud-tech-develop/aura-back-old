package com.cloud_technological.aura_pos.dto.cuentas_pagar;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CuentaPagarTableDto {
    private Long id;
    private String numeroCuenta;
    private String numeroFacturaExterno;
    private String proveedorNombre;
    private String proveedorDocumento;
    private LocalDateTime fechaEmision;
    private LocalDateTime fechaVencimiento;
    private BigDecimal totalDeuda;
    private BigDecimal totalAbonado;
    private BigDecimal saldoPendiente;
    private String estado;
    private Long totalRows;

    public String getNumeroFacturaExterno() {
        return numeroFacturaExterno;
    }

    public void setNumeroFacturaExterno(String numeroFacturaExterno) {
        this.numeroFacturaExterno = numeroFacturaExterno;
    }
}
