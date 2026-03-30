package com.cloud_technological.aura_pos.dto.tesoreria;

import java.math.BigDecimal;
import java.time.LocalDate;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateMovimientoDto {

    @NotNull
    private Long cuentaBancariaId;

    @NotNull @Positive
    private BigDecimal monto;

    @NotBlank
    private String concepto;

    private String beneficiario;
    private String referencia;

    @NotNull
    private LocalDate fecha;

    @NotBlank
    private String categoria;
}
