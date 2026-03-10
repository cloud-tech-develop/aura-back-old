package com.cloud_technological.aura_pos.dto.cuentas_pagar;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CuentaPagarDto {
    private Long id;
    private Long empresaId;
    private Long compraId;
    private Long terceroId;
    private String proveedorNombre;
    private String proveedorDocumento;
    private String numeroCuenta;
    private String numeroFacturaExterno;
    private LocalDateTime fechaEmision;
    private LocalDateTime fechaVencimiento;
    private BigDecimal totalDeuda;
    private BigDecimal totalAbonado;
    private BigDecimal saldoPendiente;
    private String estado;
    private String observaciones;
    private LocalDateTime createdAt;
    private List<AbonoPagarDto> abonos;

    public String getNumeroFacturaExterno() {
        return numeroFacturaExterno;
    }

    public void setNumeroFacturaExterno(String numeroFacturaExterno) {
        this.numeroFacturaExterno = numeroFacturaExterno;
    }
}
