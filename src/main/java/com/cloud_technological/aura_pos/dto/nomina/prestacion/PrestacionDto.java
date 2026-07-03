package com.cloud_technological.aura_pos.dto.nomina.prestacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PrestacionDto {
    private Long id;
    private Long empleadoId;
    private String empleadoNombre;
    private String empleadoDocumento;
    private String tipo;
    private LocalDate fechaDesde;
    private LocalDate fechaHasta;
    private Integer dias;
    private BigDecimal baseSalarial;
    private BigDecimal valor;
    private String estado;
    private String medioPago;
    private Long cuentaBancariaId;
    private LocalDateTime fechaPago;
    private String observacion;
    private LocalDateTime createdAt;
}
