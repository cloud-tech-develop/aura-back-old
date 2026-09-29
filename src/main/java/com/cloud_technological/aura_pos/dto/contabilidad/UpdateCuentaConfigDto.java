package com.cloud_technological.aura_pos.dto.contabilidad;

import javax.validation.constraints.NotNull;

import lombok.Data;

/** Cuerpo para asignar una cuenta a un concepto contable. */
@Data
public class UpdateCuentaConfigDto {

    @NotNull(message = "cuentaId es obligatorio")
    private Long cuentaId;
}
