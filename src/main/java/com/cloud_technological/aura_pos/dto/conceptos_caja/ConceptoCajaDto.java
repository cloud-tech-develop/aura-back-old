package com.cloud_technological.aura_pos.dto.conceptos_caja;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ConceptoCajaDto {
    private Long id;
    private String nombre;
    private String tipo;
    private Long cuentaContableId;
    private String cuentaCodigo;
    private String cuentaNombre;
    private Boolean activo;
}
