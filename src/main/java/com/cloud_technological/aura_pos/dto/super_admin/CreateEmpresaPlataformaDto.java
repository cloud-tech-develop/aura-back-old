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

    private String logoUrl;
    private String telefono;
    private String municipio;
    private Integer municipioId;

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

    private String tipoDocumentoAdmin = "CC";
    private String tipoPersonaAdmin   = "NATURAL";
    private String regimenAdmin       = "NO_RESPONSABLE_IVA";
    private Boolean granContribuyenteAdmin = false;
    private Boolean autoRetenedorAdmin     = false;
    private String paisAdmin       = "Colombia";
    private String codigoPaisAdmin = "CO";

    // Sucursal principal
    @NotBlank
    private String nombreSucursal;
}
