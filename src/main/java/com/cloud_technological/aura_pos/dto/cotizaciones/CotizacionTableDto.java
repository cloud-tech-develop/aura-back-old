package com.cloud_technological.aura_pos.dto.cotizaciones;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CotizacionTableDto {
    private Long id;
    private String numero;
    private String terceroNombre;
    private String terceroDocumento;
    private LocalDate fecha;
    private LocalDate fechaVencimiento;
    private BigDecimal total;
    private String estado;
    private long totalRows;
}
