package com.cloud_technological.aura_pos.dto.sucursal;

import lombok.Setter;
import lombok.Getter;

@Getter
@Setter
public class SucursalDto {
    private Integer id;
    private String codigo;
    private String nombre;
    private String direccion;
    private String ciudad;
    private String telefono;
    private String prefijoFacturacion;
    private Long consecutivoActual;
    private Boolean activa;
}
