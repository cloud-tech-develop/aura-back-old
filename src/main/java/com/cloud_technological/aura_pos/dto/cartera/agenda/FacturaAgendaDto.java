package com.cloud_technological.aura_pos.dto.cartera.agenda;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FacturaAgendaDto {
    private Long cuentaCobrarId;
    private String numeroCuenta;
    private Long terceroId;
    private String terceroNombre;
    private String telefono;
    private LocalDateTime fechaVencimiento;
    private BigDecimal saldoPendiente;
    /** Días que faltan para vencer (0 = hoy). */
    private Integer diasParaVencer;
}
