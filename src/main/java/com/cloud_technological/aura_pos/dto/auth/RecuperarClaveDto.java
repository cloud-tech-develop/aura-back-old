package com.cloud_technological.aura_pos.dto.auth;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** "¿Olvidaste tu contraseña?": el usuario con el que entra al sistema. */
@Getter
@Setter
public class RecuperarClaveDto {

    @NotBlank(message = "Escriba su usuario")
    @Size(max = 150, message = "El usuario no puede superar 150 caracteres")
    private String usuario;
}
