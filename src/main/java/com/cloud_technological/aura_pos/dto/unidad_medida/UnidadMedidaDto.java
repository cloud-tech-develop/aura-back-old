package com.cloud_technological.aura_pos.dto.unidad_medida;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UnidadMedidaDto {
    private Long id;
    private String nombre;
    private String abreviatura;
    private Boolean permiteDecimales;
    private Boolean activo;
}
