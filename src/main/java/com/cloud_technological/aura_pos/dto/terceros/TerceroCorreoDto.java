package com.cloud_technological.aura_pos.dto.terceros;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** Un correo del tercero o de su persona de contacto. */
@Getter
@Setter
public class TerceroCorreoDto {

    /** true = correo de la persona de contacto. */
    private Boolean deContacto = false;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "Correo no válido")
    @Size(max = 150, message = "El correo admite hasta 150 caracteres")
    private String correo;
}
