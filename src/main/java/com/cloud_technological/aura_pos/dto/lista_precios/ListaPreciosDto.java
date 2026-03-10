package com.cloud_technological.aura_pos.dto.lista_precios;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ListaPreciosDto {
    private Long id;
    private Integer empresaId;
    private String nombre;
    private Boolean activa;
}
