package com.cloud_technological.aura_pos.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoteVencimientoDto {
    private Long loteId;
    private String codigoLote;
    private String productoNombre;
    private String sucursalNombre;
    private LocalDate fechaVencimiento;
    private BigDecimal stockActual;
    private Long diasRestantes;
}
