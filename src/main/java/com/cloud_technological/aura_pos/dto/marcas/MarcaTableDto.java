package com.cloud_technological.aura_pos.dto.marcas;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MarcaTableDto {
    private Long id;
    private String nombre;
    private Boolean activo;
    private long totalRows;
}
