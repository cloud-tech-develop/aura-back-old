package com.cloud_technological.aura_pos.dto.obligaciones;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CuotaAmortizacionDto {
    private Long id;
    private Integer numeroCuota;
    private LocalDate fechaVencimiento;
    private BigDecimal cuota;
    private BigDecimal abonoCapital;
    private BigDecimal interes;
    private BigDecimal saldo;
    private String estado;
    private LocalDate fechaPago;
}
