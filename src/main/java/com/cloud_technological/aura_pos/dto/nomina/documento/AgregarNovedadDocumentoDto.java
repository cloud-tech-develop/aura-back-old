package com.cloud_technological.aura_pos.dto.nomina.documento;

import javax.validation.constraints.NotNull;

import com.cloud_technological.aura_pos.dto.nomina.nomina.AddNovedadDto;

import lombok.Getter;
import lombok.Setter;

/** Novedad para un empleado de la prenómina: la fila y los datos de la novedad. */
@Getter
@Setter
public class AgregarNovedadDocumentoDto extends AddNovedadDto {

    @NotNull(message = "Elija el empleado")
    private Long nominaId;
}
