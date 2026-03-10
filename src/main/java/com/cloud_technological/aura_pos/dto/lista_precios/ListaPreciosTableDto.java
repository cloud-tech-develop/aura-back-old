package com.cloud_technological.aura_pos.dto.lista_precios;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ListaPreciosTableDto {
    private Long id;
    private String nombre;
    private Boolean activa;
    private long totalRows;
}
