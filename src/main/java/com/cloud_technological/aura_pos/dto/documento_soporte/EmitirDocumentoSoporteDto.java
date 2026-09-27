package com.cloud_technological.aura_pos.dto.documento_soporte;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class EmitirDocumentoSoporteDto {

    /** COMPRA | GASTO */
    @NotBlank(message = "Indique si es una compra o un gasto")
    private String origenTipo;

    @NotNull(message = "Indique el documento de origen")
    private Long origenId;

    /** Solo si la empresa tiene varios rangos de documento soporte activos en Factus. */
    private String numberingRangeId;

    private String observacion;
}
