package com.cloud_technological.aura_pos.dto.obligaciones;

import java.math.BigDecimal;
import java.time.LocalDate;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

import lombok.Data;

@Data
public class CreateObligacionDto {
    @NotNull private String entidad;
    private Long terceroId;
    private String numero;
    @NotNull @Positive private BigDecimal montoPrincipal;
    /** Tasa de interés mensual en % (ej: 1.5). */
    @NotNull private BigDecimal tasaMensual;
    @NotNull @Positive private Integer plazoMeses;
    @NotNull private LocalDate fechaDesembolso;
    private Long cuentaBancariaId;
}
