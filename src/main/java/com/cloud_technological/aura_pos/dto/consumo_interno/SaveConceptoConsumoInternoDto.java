package com.cloud_technological.aura_pos.dto.consumo_interno;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveConceptoConsumoInternoDto {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 80, message = "El nombre no puede superar 80 caracteres")
    private String nombre;

    /** Null = la cuenta por defecto de la configuración contable. */
    private Long cuentaId;

    private Boolean generaIva = Boolean.TRUE;

    private Boolean activo = Boolean.TRUE;
}
