package com.cloud_technological.aura_pos.dto.sucursal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SucursalTableDto {
    private Integer id;
    private String codigo;
    private String nombre;
    private String ciudad;
    private String telefono;
    private Boolean activa;
    private Long totalRows;
}
