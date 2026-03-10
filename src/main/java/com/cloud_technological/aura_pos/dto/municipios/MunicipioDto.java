package com.cloud_technological.aura_pos.dto.municipios;

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
public class MunicipioDto {
    private Integer id;
    private String codigo;
    private String nombre;
    private String departamento;
    private String label;
}
