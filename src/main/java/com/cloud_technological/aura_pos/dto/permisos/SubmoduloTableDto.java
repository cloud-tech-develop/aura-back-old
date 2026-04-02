package com.cloud_technological.aura_pos.dto.permisos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmoduloTableDto {

    private Integer id;
    private Integer moduloId;
    private String moduloNombre;
    private String nombre;
    private String codigo;
    private String descripcion;
    private Boolean activo;
    private Integer orden;
    private Integer totalRows;
}
