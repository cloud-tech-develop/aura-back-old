package com.cloud_technological.aura_pos.dto.merma;

import javax.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMotivoMermaDto {
    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;
    private Boolean afectaContabilidad;
}
