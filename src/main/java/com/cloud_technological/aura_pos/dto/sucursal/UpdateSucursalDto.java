package com.cloud_technological.aura_pos.dto.sucursal;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateSucursalDto {
    @Size(max = 20)
    private String codigo;

    @NotBlank
    @Size(max = 150)
    private String nombre;

    @Size(max = 255)
    private String direccion;

    @Size(max = 100)
    private String ciudad;

    @Size(max = 50)
    private String telefono;

    @Size(max = 10)
    private String prefijoFacturacion;

    private Boolean activa;
}
