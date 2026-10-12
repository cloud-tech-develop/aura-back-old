package com.cloud_technological.aura_pos.dto.terceros;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** Un teléfono del tercero o de su persona de contacto. */
@Getter
@Setter
public class TerceroTelefonoDto {

    /** true = teléfono de la persona de contacto. */
    private Boolean deContacto = false;

    @Pattern(regexp = "CELULAR|CASA|OFICINA|OTRO", message = "Tipo de teléfono no válido")
    private String tipo = "CELULAR";

    @NotBlank(message = "El número de teléfono es obligatorio")
    @Size(max = 30, message = "El teléfono admite hasta 30 caracteres")
    private String numero;

    @Size(max = 10, message = "La extensión admite hasta 10 caracteres")
    private String extension;
}
