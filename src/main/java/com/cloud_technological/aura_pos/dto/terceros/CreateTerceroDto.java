package com.cloud_technological.aura_pos.dto.terceros;

import javax.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateTerceroDto {
    @NotBlank(message = "El tipo de documento es obligatorio")
    private String tipoDocumento;
    @NotBlank(message = "El número de documento es obligatorio")
    private String numeroDocumento;
    private String dv;
    private String razonSocial;
    private String nombres;
    private String apellidos;
    private String direccion;
    private String telefono;
    private String email;
    private String emailFe;
    private String responsabilidadFiscal;
    private Boolean esCliente = true;
    private Boolean esProveedor = false;
    private Boolean esEmpleado = false;
    private Long municipioId;
    private Boolean activo = true;
}
