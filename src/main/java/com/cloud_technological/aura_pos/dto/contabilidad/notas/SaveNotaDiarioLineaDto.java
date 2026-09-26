package com.cloud_technological.aura_pos.dto.contabilidad.notas;

import java.math.BigDecimal;

import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** Línea de la nota: una cuenta auxiliar con su débito o su crédito. */
@Getter @Setter
public class SaveNotaDiarioLineaDto {

    @NotNull(message = "Cada línea debe tener una cuenta contable")
    private Long cuentaId;

    private String descripcion;

    private BigDecimal debito = BigDecimal.ZERO;

    private BigDecimal credito = BigDecimal.ZERO;

    private Long terceroId;

    private Long centroCostoId;

    private Long proyectoId;

    private Long frenteId;
}
