package com.cloud_technological.aura_pos.dto.retenciones;

import java.math.BigDecimal;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreateTarifaRetencionDto {

    @NotBlank(message = "El tipo es obligatorio")
    private String tipo;

    @NotBlank(message = "El concepto es obligatorio")
    private String concepto;

    private String codigoConcepto;

    @NotNull(message = "La tarifa para persona natural es obligatoria")
    private BigDecimal tarifaNatural;

    @NotNull(message = "La tarifa para persona jurídica es obligatoria")
    private BigDecimal tarifaJuridica;

    private BigDecimal baseMinima = BigDecimal.ZERO;

    private Boolean activo = true;
}
