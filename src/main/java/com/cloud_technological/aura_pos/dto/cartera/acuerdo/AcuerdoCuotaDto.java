package com.cloud_technological.aura_pos.dto.cartera.acuerdo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AcuerdoCuotaDto {
    private Long id;
    private Integer numero;
    private LocalDate fechaVencimiento;
    private BigDecimal valor;
    private BigDecimal valorPagado;
    /** PENDIENTE, PARCIAL, PAGADA, VENCIDA. */
    private String estado;
    private LocalDateTime pagadaAt;
    /** Positivo = días vencida; negativo = días que faltan. */
    private Integer diasVencida;
}
