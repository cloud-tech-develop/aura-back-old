package com.cloud_technological.aura_pos.dto.cuentas_pagar;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCuentaPagarDto {
    
    @NotNull(message = "El ID del proveedor es requerido")
    private Long proveedorId;

    private Long compraId;

    @NotNull(message = "El total de deuda es requerido")
    private BigDecimal totalDeuda;

    @NotNull(message = "La fecha de emisión es requerida")
    private LocalDateTime fechaEmision;

    private LocalDateTime fechaVencimiento;

    private String numeroFacturaExterno;

    private String observaciones;

    public String getNumeroFacturaExterno() {
        return numeroFacturaExterno;
    }

    public void setNumeroFacturaExterno(String numeroFacturaExterno) {
        this.numeroFacturaExterno = numeroFacturaExterno;
    }
}
