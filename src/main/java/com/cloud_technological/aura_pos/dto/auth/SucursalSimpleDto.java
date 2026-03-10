package com.cloud_technological.aura_pos.dto.auth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SucursalSimpleDto {
    private Integer id;
    private String nombre;
    private Boolean esDefault; // Para que el front sepa cuál preseleccionar
}
