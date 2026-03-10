package com.cloud_technological.aura_pos.dto.productos;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCodigoBarrasDto {
    @NotBlank(message = "El código de barras no puede estar vacío")
    @Size(max = 50, message = "El código de barras no puede superar 50 caracteres")
    private String codigoBarras;
}
