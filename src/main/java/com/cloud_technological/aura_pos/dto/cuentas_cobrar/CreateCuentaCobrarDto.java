package com.cloud_technological.aura_pos.dto.cuentas_cobrar;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCuentaCobrarDto {
    
    @NotNull(message = "El ID del cliente es requerido")
    private Long clienteId;

    private Long ventaId;

    @NotNull(message = "El total de deuda es requerido")
    private BigDecimal totalDeuda;

    @NotNull(message = "La fecha de emisión es requerida")
    private LocalDateTime fechaEmision;

    private LocalDateTime fechaVencimiento;

    private String observaciones;
}
