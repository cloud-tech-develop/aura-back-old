package com.cloud_technological.aura_pos.dto.contabilidad;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PlanCuentaDto {
    private Long id;
    private String codigo;
    private String nombre;
    private String tipo;
    private String naturaleza;
    private Short nivel;
    private Long padreId;
    private String padreNombre;
    private Boolean activa;
    private Boolean auxiliar;
    private String codigoDian;
}
