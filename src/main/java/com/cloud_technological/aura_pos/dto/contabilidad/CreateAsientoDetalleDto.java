package com.cloud_technological.aura_pos.dto.contabilidad;

import java.math.BigDecimal;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreateAsientoDetalleDto {

    @NotNull
    private Long cuentaId;

    private String descripcion;

    @NotNull
    private BigDecimal debito = BigDecimal.ZERO;

    @NotNull
    private BigDecimal credito = BigDecimal.ZERO;
}
