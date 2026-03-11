package com.cloud_technological.aura_pos.dto.super_admin;

import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateEmpresaPlataformaDto {
    @Size(max = 200)
    private String razonSocial;

    @Size(max = 200)
    private String nombreComercial;

    private String dv;
    private String logoUrl;
    private String telefono;
    private String municipio;
    private Integer municipioId;
    private Boolean activa;
}
