package com.cloud_technological.aura_pos.dto.rutas;

import lombok.Data;

@Data
public class RutaLocalDto {
    private Long localId;
    private String localNombre;
    private String localDireccion;
    private Integer orden;
}