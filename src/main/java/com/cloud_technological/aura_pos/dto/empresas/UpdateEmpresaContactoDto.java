package com.cloud_technological.aura_pos.dto.empresas;

import javax.validation.constraints.Email;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * Los datos de contacto que el administrador edita desde su perfil. No toca
 * razon social ni NIT: eso es identidad tributaria y se cambia desde la
 * administracion de la plataforma.
 */
@Getter
@Setter
public class UpdateEmpresaContactoDto {

    @Size(max = 30, message = "El telefono no puede superar 30 caracteres")
    private String telefono;

    @Email(message = "El correo no tiene un formato valido")
    @Size(max = 150, message = "El correo no puede superar 150 caracteres")
    private String correo;

    @Size(max = 200, message = "La direccion no puede superar 200 caracteres")
    private String direccion;

    @Size(max = 120, message = "El municipio no puede superar 120 caracteres")
    private String municipio;
}
