package com.cloud_technological.aura_pos.dto.super_admin;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class CreateEmpresaPlataformaDto {
    @NotBlank(message = "La razón social es obligatoria")
    @Size(max = 200)
    private String razonSocial;

    @Size(max = 200)
    private String nombreComercial;

    @NotBlank(message = "El NIT es obligatorio")
    @Size(max = 20)
    private String nit;

    private String dv;

    // Datos del usuario SUPER_ADMIN de la empresa
    @NotBlank(message = "El email del administrador es obligatorio")
    @Email
    private String emailAdmin;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 6)
    private String passwordAdmin;

    @NotBlank
    private String nombresAdmin;

    @NotBlank
    private String apellidosAdmin;

    @NotBlank
    private String documentoAdmin;

    // Sucursal principal
    @NotBlank
    private String nombreSucursal;
}
